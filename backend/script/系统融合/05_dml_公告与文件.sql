-- ============================================================
-- 系统融合：公告/文件迁移 + ln_pro 独有表数据直迁
-- 编号：05（依赖 02_ddl_扩展表.sql 第 3 部分的 6 张独有表、03 的用户映射）
--
-- 映射规则：
--   security_ln_pro.sys_sec_notice → sys_notice（标题缺失，以 DOMAIN 兜底；URI 无对应字段，不迁）
--   security_ln_pro.sys_file       → sys_oss（仅元数据，二进制不迁，见方案 3.10）
--   6 张 ln_pro 独有表             → 同名新表（02_ddl 第 3 部分），原样直迁
--
-- 幂等：
--   sys_notice   按 remark = 'ln-pro-migrated' 标记清理后重跑
--   sys_oss      按 service = 'ln-pro' 标记清理后重跑
--   6 张直迁表   仅含迁移数据，脚本内 DELETE 后重插，天然幂等
--
-- 大数据量策略：sys_file 元数据量级较大（十万级），与 04 相同按主键范围
--   5000/批插入、每批独立 COMMIT。
--
-- 执行顺序：02 → 03 → 04 → 05 →（可选）99 清理
-- ============================================================

USE `security-cloud`;

DELIMITER $$

DROP PROCEDURE IF EXISTS proc_migrate_ln_pro_notice_file $$
CREATE PROCEDURE proc_migrate_ln_pro_notice_file()
BEGIN
    DECLARE v_cur      BIGINT;
    DECLARE v_max      BIGINT;
    DECLARE v_batch    INT DEFAULT 5000;
    DECLARE v_err_state VARCHAR(64);
    DECLARE v_err_msg    TEXT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        GET DIAGNOSTICS CONDITION 1
            v_err_state = RETURNED_SQLSTATE,
            v_err_msg   = MESSAGE_TEXT;
        SELECT CONCAT('⚠️ 公告/文件迁移失败，当前批次已 ROLLBACK。错误: ', v_err_state, ' / ', v_err_msg) AS err_msg;
        RESIGNAL;
    END;

    -- ========================================================
    -- 阶段 1：公告迁移 sys_sec_notice → sys_notice（小表，一次插入）
    --   标题：源表无标题字段，DOMAIN 非空则用之，否则“未命名公告”
    --   类型：TYPE=1 → '1'（通知），其余 → '2'（公告）
    --   状态：源 STATUS 语义未知，统一置 '0'（正常），如需关闭请在后台人工调整
    -- ========================================================
    START TRANSACTION;

    DROP TEMPORARY TABLE IF EXISTS tmp_seq_notice;
    CREATE TEMPORARY TABLE tmp_seq_notice (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(notice_id), 0) + 100000 FROM sys_notice);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_notice AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    -- 源 ID 允许 NULL，仅对非空且唯一的行建序列（去重防主键冲突）
    INSERT INTO tmp_seq_notice (old_id)
    SELECT DISTINCT n.`ID` FROM security_ln_pro.sys_sec_notice n WHERE n.`ID` IS NOT NULL;

    INSERT INTO `sys_notice`
        (notice_id, tenant_id, notice_title, notice_type, notice_content,
         status, create_dept, create_by, create_time, remark)
    SELECT
        seq.id,
        '000000',
        LEFT(COALESCE(NULLIF(n.DOMAIN, ''), '未命名公告'), 50),
        CASE WHEN n.TYPE = 1 THEN '1' ELSE '2' END,
        n.CONTENT,
        '0',
        103,
        1,
        n.CREATETIME,
        'ln-pro-migrated'
    FROM tmp_seq_notice seq
    JOIN security_ln_pro.sys_sec_notice n ON n.`ID` = seq.old_id;

    COMMIT;

    -- ========================================================
    -- 阶段 2：文件元数据迁移 sys_file → sys_oss（分批）
    --   file_name  ← file_path（对象存储 key，与目标语义一致）
    --   original_name ← file_name（原始文件名）
    --   file_suffix ← extension（自动补 '.'），缺失时从文件名提取
    --   ext1       ← JSON（oldId/type/interlockId/remark/fileSize 溯源）
    --   service    ← 'ln-pro'（来源标记，兼作幂等清理键）
    --   create_by  ← 按用户名回连 sys_user（03 已迁移，找不到则 NULL）
    -- ========================================================
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_file;
    CREATE TEMPORARY TABLE tmp_seq_file (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(oss_id), 0) + 100000 FROM sys_oss);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_file AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_file (old_id) SELECT `id` FROM security_ln_pro.sys_file;

    -- 用户名 → user_id 映射（每个用户名取最小 user_id，避免重名导致 JOIN 扇出）
    -- MySQL 5.7 限制：同一条语句里同一临时表只能打开一次（Can't reopen table），
    -- create_by / update_by 各需一次回连 → 建两张内容相同的映射表各 JOIN 一次（03 的乒乓表手法）
    DROP TEMPORARY TABLE IF EXISTS tmp_user_c;
    CREATE TEMPORARY TABLE tmp_user_c (
        user_name varchar(50) PRIMARY KEY,
        user_id   bigint(20)
    ) ENGINE=Memory;
    INSERT INTO tmp_user_c (user_name, user_id)
    SELECT user_name, MIN(user_id) FROM sys_user WHERE tenant_id = '000000' GROUP BY user_name;

    DROP TEMPORARY TABLE IF EXISTS tmp_user_u;
    CREATE TEMPORARY TABLE tmp_user_u (
        user_name varchar(50) PRIMARY KEY,
        user_id   bigint(20)
    ) ENGINE=Memory;
    INSERT INTO tmp_user_u (user_name, user_id)
    SELECT user_name, MIN(user_id) FROM sys_user WHERE tenant_id = '000000' GROUP BY user_name;

    SET v_cur = (SELECT IFNULL(MIN(id), 0) FROM tmp_seq_file);
    SET v_max = (SELECT IFNULL(MAX(id), 0) FROM tmp_seq_file);

    WHILE v_cur <= v_max DO
        START TRANSACTION;

        INSERT INTO `sys_oss`
            (oss_id, tenant_id, file_name, original_name, file_suffix,
             url, ext1, create_dept, create_time, create_by,
             update_time, update_by, service)
        SELECT
            seq.id,
            '000000',
            LEFT(COALESCE(NULLIF(f.file_path, ''), f.file_name), 255),
            LEFT(COALESCE(f.file_name, ''), 255),
            COALESCE(
                CASE WHEN f.extension IS NOT NULL AND f.extension <> ''
                     THEN LEFT(CONCAT('.', REPLACE(f.extension, '.', '')), 10) END,
                CASE WHEN f.file_name IS NOT NULL AND LOCATE('.', f.file_name) > 0
                     THEN LEFT(CONCAT('.', SUBSTRING_INDEX(f.file_name, '.', -1)), 10) END,
                ''
            ),
            LEFT(COALESCE(NULLIF(f.file_path, ''), ''), 500),
            JSON_OBJECT(
                'source', 'ln-pro',
                'oldId', f.id,
                'type', f.type,
                'interlockId', f.interlock_id,
                'remark', f.remark,
                'fileSize', f.file_size
            ),
            103,
            f.create_time,
            cm.user_id,
            f.update_time,
            um2.user_id,
            'ln-pro'
        FROM tmp_seq_file seq
        JOIN security_ln_pro.sys_file f ON f.`id` = seq.old_id
        LEFT JOIN tmp_user_c cm  ON cm.user_name  = f.create_by
        LEFT JOIN tmp_user_u um2 ON um2.user_name = f.update_by
        WHERE seq.id >= v_cur AND seq.id < v_cur + v_batch;

        COMMIT;
        SET v_cur = v_cur + v_batch;
    END WHILE;

    -- ========================================================
    -- 阶段 3：ln_pro 独有表数据直迁（6 张，DELETE + INSERT，幂等）
    --   注：用 DELETE 而非 TRUNCATE——后者是 DDL 会隐式提交，破坏本阶段事务原子性；
    --       这些表仅含迁移数据，量级小，DELETE 开销可忽略
    -- ========================================================
    START TRANSACTION;

    DELETE FROM sys_allow_loginip;
    INSERT INTO sys_allow_loginip (ip) SELECT `ip` FROM security_ln_pro.sys_allow_loginip;

    DELETE FROM sys_check_rule;
    INSERT INTO sys_check_rule
        (id, rule_name, rule_code, rule_json, rule_description, update_by, update_time, create_by, create_time)
    SELECT `id`, `rule_name`, `rule_code`, `rule_json`, `rule_description`, `update_by`, `update_time`, `create_by`, `create_time`
    FROM security_ln_pro.sys_check_rule;

    DELETE FROM sys_fill_rule;
    INSERT INTO sys_fill_rule
        (id, rule_name, rule_code, rule_class, rule_params, update_by, update_time, create_by, create_time)
    SELECT `id`, `rule_name`, `rule_code`, `rule_class`, `rule_params`, `update_by`, `update_time`, `create_by`, `create_time`
    FROM security_ln_pro.sys_fill_rule;

    DELETE FROM sys_layout;
    INSERT INTO sys_layout
        (id, sys_name, layout_name, config, image, icon, description)
    SELECT `id`, `sysName`, `layoutName`, `config`, `image`, `icon`, `description`
    FROM security_ln_pro.sys_layout;

    DELETE FROM sys_sec_config_logs;
    INSERT INTO sys_sec_config_logs
        (id, config_type, create_time, log_name, config)
    SELECT `id`, `configtype`, `createtime`, `logname`, `config`
    FROM security_ln_pro.sys_sec_config_logs;

    DELETE FROM sys_sec_system_info;
    INSERT INTO sys_sec_system_info (id, name, description)
    SELECT `ID`, `NAME`, `DESCRIPTION` FROM security_ln_pro.sys_sec_system_info;

    COMMIT;

    DROP TEMPORARY TABLE IF EXISTS tmp_seq_notice;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_file;
    DROP TEMPORARY TABLE IF EXISTS tmp_user_c;
    DROP TEMPORARY TABLE IF EXISTS tmp_user_u;
END $$

DELIMITER ;

-- ============================================================
-- 调用存储过程（执行迁移）
-- ============================================================
CALL proc_migrate_ln_pro_notice_file();
DROP PROCEDURE IF EXISTS proc_migrate_ln_pro_notice_file;

-- ============================================================
-- 验证统计
-- ============================================================
SELECT 'sys_notice ← sys_sec_notice' AS channel, COUNT(*) AS cnt FROM sys_notice WHERE remark = 'ln-pro-migrated'
UNION ALL
SELECT 'sys_oss ← sys_file', COUNT(*) FROM sys_oss WHERE service = 'ln-pro'
UNION ALL
SELECT '来源总量 sys_sec_notice', COUNT(*) FROM security_ln_pro.sys_sec_notice
UNION ALL
SELECT '来源总量 sys_file', COUNT(*) FROM security_ln_pro.sys_file
UNION ALL
SELECT '直迁 sys_allow_loginip', COUNT(*) FROM sys_allow_loginip
UNION ALL
SELECT '直迁 sys_layout', COUNT(*) FROM sys_layout
UNION ALL
SELECT '直迁 sys_sec_config_logs', COUNT(*) FROM sys_sec_config_logs
UNION ALL
SELECT '直迁 sys_sec_system_info', COUNT(*) FROM sys_sec_system_info
UNION ALL
SELECT '直迁 sys_check_rule', COUNT(*) FROM sys_check_rule
UNION ALL
SELECT '直迁 sys_fill_rule', COUNT(*) FROM sys_fill_rule;

-- ============================================================
-- 脚本结束
-- ============================================================
