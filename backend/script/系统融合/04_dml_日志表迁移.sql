-- ============================================================
-- 系统融合：日志表迁移脚本（ln_pro → security-cloud）
-- 编号：04（与 03_dml_数据迁移.sql 同风格，可独立执行）
--
-- 背景：sys_log / sys_operate_log 数据量大，从 03 脚本拆出单独迁移；
--       sys_sec_log 为小表，顺带并入本脚本。
--
-- 映射规则（详见 doc/系统融合方案.md 3.9 节）：
--   security_ln_pro.sys_log (log_type=1)  → sys_logininfor
--   security_ln_pro.sys_log (log_type=2)  → sys_oper_log
--   security_ln_pro.sys_operate_log       → sys_logininfor（登录留痕）
--   security_ln_pro.sys_sec_log (LOGTYPE=1) → sys_logininfor
--   security_ln_pro.sys_sec_log (LOGTYPE=2) → sys_oper_log
--
-- 执行前提：
--   1) 已执行 02_ddl_扩展表.sql、03_dml_数据迁移.sql（日志不依赖其映射表，
--      但执行顺序保持一致便于排查）；
--   2) security-cloud 库与 security_ln_pro 库在同一 MySQL 实例；
--   3) sys_oper_log 需已含 client_id 列（02 之后单独加过，脚本内不重复 DDL）。
--
-- 大数据量策略：
--   临时自增序列表生成新主键，按主键范围分批（5000/批）插入，每批独立 COMMIT，
--   避免大事务撑爆 undo log；中途失败可重跑（见“幂等”说明）。
--
-- 幂等：脚本本身不判断重复，重跑前先按 client_id 标记清理：
--   DELETE FROM sys_oper_log    WHERE client_id = 'ln-pro-migrated';
--   DELETE FROM sys_logininfor  WHERE client_key = 'ln-pro-migrated';
--   （sys_logininfor 无 client_id 字段，用 client_key 标记来源）
-- ============================================================

USE `security-cloud`;

DELIMITER $$

DROP PROCEDURE IF EXISTS proc_migrate_ln_pro_logs $$
CREATE PROCEDURE proc_migrate_ln_pro_logs()
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
        SELECT CONCAT('⚠️ 日志迁移失败，当前批次已 ROLLBACK。错误: ', v_err_state, ' / ', v_err_msg) AS err_msg;
        RESIGNAL;
    END;

    -- ========================================================
    -- 阶段 1：临时序列表（新主键 = 目标表现有 MAX + 100000 起步）
    -- ========================================================

    -- 1.1 sys_log 序列（一张序列同时服务 log_type=1/2 两条通道）
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_syslog;
    CREATE TEMPORARY TABLE tmp_seq_syslog (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(oper_id), 0) + 100000 FROM sys_oper_log);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_syslog AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_syslog (old_id) SELECT `id` FROM security_ln_pro.sys_log;

    -- 1.2 sys_operate_log 序列（落 sys_logininfor，起始值按其 MAX 独立计算）
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_operlog;
    CREATE TEMPORARY TABLE tmp_seq_operlog (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(info_id), 0) + 100000 FROM sys_logininfor);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_operlog AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_operlog (old_id) SELECT `id` FROM security_ln_pro.sys_operate_log;

    -- 1.3 sys_sec_log 序列（小表，起始值再抬一档避免与 1.2 的 info_id 空间冲突）
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_seclog;
    CREATE TEMPORARY TABLE tmp_seq_seclog (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(info_id), 0) + 200000 FROM sys_logininfor);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_seclog AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_seclog (old_id) SELECT `ID` FROM security_ln_pro.sys_sec_log;

    -- ========================================================
    -- 阶段 2：sys_log 分批迁移（log_type=1 → 登录日志；=2 → 操作日志）
    -- ========================================================
    SET v_cur = (SELECT IFNULL(MIN(id), 0) FROM tmp_seq_syslog);
    SET v_max = (SELECT IFNULL(MAX(id), 0) FROM tmp_seq_syslog);

    WHILE v_cur <= v_max DO
        START TRANSACTION;

        -- 2.1 登录日志通道
        INSERT INTO `sys_logininfor`
            (info_id, tenant_id, user_name, client_key, device_type,
             ipaddr, login_location, browser, os, status, msg, login_time)
        SELECT
            seq.id,
            '000000',
            LEFT(l.userid, 50),
            'ln-pro-migrated',
            '',
            LEFT(l.ip, 128),
            '',
            '',
            '',
            '0',
            LEFT(IFNULL(l.log_content, ''), 255),
            l.create_time
        FROM tmp_seq_syslog seq
        JOIN security_ln_pro.sys_log l ON l.`id` = seq.old_id
        WHERE seq.id >= v_cur AND seq.id < v_cur + v_batch
          AND l.log_type = 1;

        -- 2.2 操作日志通道
        INSERT INTO `sys_oper_log`
            (oper_id, tenant_id, client_id, title, business_type, method,
             request_method, operator_type, oper_name, dept_name, oper_url,
             oper_ip, oper_location, oper_param, json_result, status,
             error_msg, oper_time, cost_time)
        SELECT
            seq.id,
            '000000',
            'ln-pro-migrated',
            LEFT(COALESCE(NULLIF(l.log_content, ''), l.request_url), 50),
            0,
            LEFT(IFNULL(l.method, ''), 100),
            LEFT(IFNULL(l.request_type, ''), 10),
            1,
            LEFT(IFNULL(l.userid, ''), 50),
            '',
            LEFT(IFNULL(l.request_url, ''), 255),
            LEFT(IFNULL(l.ip, ''), 128),
            '',
            LEFT(IFNULL(l.request_param, ''), 4000),
            '',
            0,
            '',
            l.create_time,
            IFNULL(l.cost_time, 0)
        FROM tmp_seq_syslog seq
        JOIN security_ln_pro.sys_log l ON l.`id` = seq.old_id
        WHERE seq.id >= v_cur AND seq.id < v_cur + v_batch
          AND l.log_type = 2;

        COMMIT;
        SET v_cur = v_cur + v_batch;
    END WHILE;

    -- ========================================================
    -- 阶段 3：sys_operate_log 分批迁移（登录留痕 → sys_logininfor）
    -- remark 常存失败原因（如“账号或密码错误”），非空则记为失败
    -- ========================================================
    SET v_cur = (SELECT IFNULL(MIN(id), 0) FROM tmp_seq_operlog);
    SET v_max = (SELECT IFNULL(MAX(id), 0) FROM tmp_seq_operlog);

    WHILE v_cur <= v_max DO
        START TRANSACTION;

        INSERT INTO `sys_logininfor`
            (info_id, tenant_id, user_name, client_key, device_type,
             ipaddr, login_location, browser, os, status, msg, login_time)
        SELECT
            seq.id,
            '000000',
            LEFT(o.userid, 50),
            'ln-pro-migrated',
            '',
            LEFT(COALESCE(NULLIF(o.operate_ip, ''), o.ip), 128),
            '',
            LEFT(IFNULL(o.browser, ''), 50),
            '',
            CASE WHEN o.remark IS NULL OR o.remark = '' THEN '0' ELSE '1' END,
            LEFT(IFNULL(o.remark, ''), 255),
            o.opertime
        FROM tmp_seq_operlog seq
        JOIN security_ln_pro.sys_operate_log o ON o.`id` = seq.old_id
        WHERE seq.id >= v_cur AND seq.id < v_cur + v_batch;

        COMMIT;
        SET v_cur = v_cur + v_batch;
    END WHILE;

    -- ========================================================
    -- 阶段 4：sys_sec_log 整表迁移（小表，一次插入）
    -- LOGTYPE=1 → sys_logininfor；其余 → sys_oper_log
    -- LOGDATE 为 varchar(20)，容错解析（格式不符置 NULL）
    -- ========================================================
    START TRANSACTION;

    INSERT INTO `sys_logininfor`
        (info_id, tenant_id, user_name, client_key, device_type,
         ipaddr, login_location, browser, os, status, msg, login_time)
    SELECT
        seq.id,
        '000000',
        LEFT(IFNULL(sl.LOGGER, ''), 50),
        'ln-pro-migrated',
        '',
        '',
        '',
        '',
        '',
        '0',
        LEFT(CONCAT_WS(' ', sl.LOGTITLE, sl.LOGMSG), 255),
        STR_TO_DATE(sl.LOGDATE, '%Y-%m-%d %H:%i:%s')
    FROM tmp_seq_seclog seq
    JOIN security_ln_pro.sys_sec_log sl ON sl.`ID` = seq.old_id
    WHERE sl.LOGTYPE = 1;

    INSERT INTO `sys_oper_log`
        (oper_id, tenant_id, client_id, title, business_type, method,
         request_method, operator_type, oper_name, dept_name, oper_url,
         oper_ip, oper_location, oper_param, json_result, status,
         error_msg, oper_time, cost_time)
    SELECT
        seq.id,
        '000000',
        'ln-pro-migrated',
        LEFT(IFNULL(sl.LOGTITLE, ''), 50),
        0,
        '',
        '',
        1,
        LEFT(IFNULL(sl.LOGGER, ''), 50),
        '',
        '',
        '',
        '',
        LEFT(IFNULL(sl.LOGMSG, ''), 4000),
        '',
        0,
        '',
        STR_TO_DATE(sl.LOGDATE, '%Y-%m-%d %H:%i:%s'),
        0
    FROM tmp_seq_seclog seq
    JOIN security_ln_pro.sys_sec_log sl ON sl.`ID` = seq.old_id
    WHERE (sl.LOGTYPE IS NULL OR sl.LOGTYPE <> 1);

    COMMIT;

    DROP TEMPORARY TABLE IF EXISTS tmp_seq_syslog;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_operlog;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_seclog;
END $$

DELIMITER ;

-- ============================================================
-- 调用存储过程（执行迁移）
-- ============================================================
CALL proc_migrate_ln_pro_logs();
DROP PROCEDURE IF EXISTS proc_migrate_ln_pro_logs;

-- ============================================================
-- 验证统计（迁移总量 = 三个登录通道之和，与来源总量对账）
-- ============================================================
SELECT 'sys_logininfor ← 迁移合计'  AS channel, COUNT(*) AS cnt FROM sys_logininfor WHERE client_key = 'ln-pro-migrated'
UNION ALL
SELECT 'sys_oper_log   ← 迁移合计', COUNT(*) FROM sys_oper_log WHERE client_id = 'ln-pro-migrated'
UNION ALL
SELECT '来源总量 sys_log(log_type=1)', COUNT(*) FROM security_ln_pro.sys_log WHERE log_type = 1
UNION ALL
SELECT '来源总量 sys_log(log_type=2)', COUNT(*) FROM security_ln_pro.sys_log WHERE log_type = 2
UNION ALL
SELECT '来源总量 sys_operate_log', COUNT(*) FROM security_ln_pro.sys_operate_log
UNION ALL
SELECT '来源总量 sys_sec_log', COUNT(*) FROM security_ln_pro.sys_sec_log;

-- ============================================================
-- 脚本结束
-- ============================================================
