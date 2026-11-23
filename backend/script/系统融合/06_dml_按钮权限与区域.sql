-- ============================================================
-- 系统融合：按钮权限（第二/三阶段）与区域数据迁移
-- 编号：06（依赖 02_ddl 第 3/4 部分、03 的菜单/角色/用户迁移结果）
--
-- 一、按钮权限
--   阶段A（URL 级，方案 3.7 第二阶段）：
--     sys_sec_opaction × sys_sec_operation_opaction → sys_menu F 按钮
--     挂在 operation 对应的迁移菜单下；perms = 原 URL（后端可直接引用）；
--     仅注册按钮主数据，不生成角色关联（源库无 URL 级角色授权表）。
--   阶段B（动作级，方案 3.7 第三阶段）：
--     sys_sec_role_opt_action_data DISTINCT (菜单,动作) → sys_menu F 按钮；
--     同表 (角色,菜单,动作) → sys_role_menu 关联。
--     注：DATAID（数据范围）不迁入按钮，规范数据范围见阶段C。
--   阶段C（数据范围字典，方案 3.8）：
--     sys_sec_data → sys_dict_type 'ln_pro_data_scope' + sys_dict_data。
--   sys_sec_role_dicts（角色字典值过滤，1 行）：无对应模型，
--     直迁保留表（02 第 4 部分）。
--
-- 二、区域数据
--   sys_sec_region（武汉市区划码）→ sys_area 增量合并（area_code 不存在才插入）；
--   norm_domain/range/source/timefeature/unit（字典性质、字段多于 sys_dict）
--     → 同名保留表直迁（02 第 4 部分）。
--
-- 三、映射重建（关键）
--   03 脚本的 tmp_*_map 为 TEMPORARY 表（会话结束即失效），本脚本按名称锚点重建：
--     菜单：sys_menu.menu_name + sys_menu.path = COALESCE(NULLIF(URI,''), ID)，
--           且 sys_menu.app_id IS NOT NULL（03 迁移行特征）；
--     角色：sys_role.role_name（tenant '000000'）。
--   重建后与源行数对比校验，缺口超阈值直接报错终止（SIGNAL），避免错迁。
--
-- 幂等标记：
--   sys_menu 按钮  remark = 'ln-pro-btn-url' / 'ln-pro-btn-action'
--   sys_dict_*     dict_type = 'ln_pro_data_scope'
--   sys_area       create_user_name = 'ln-pro-migrated'
--   保留表         DELETE + 重插（02 第 3/4 部分均 DROP IF EXISTS 重建）
--
-- 执行顺序：02 → 03 → 04 → 05 → 06 →（可选）99
-- ============================================================

USE `security-cloud`;

DELIMITER $$

DROP PROCEDURE IF EXISTS proc_migrate_ln_pro_btn_region $$
CREATE PROCEDURE proc_migrate_ln_pro_btn_region()
BEGIN
    DECLARE v_src_menu_cnt   INT DEFAULT 0;
    DECLARE v_map_menu_cnt   INT DEFAULT 0;
    DECLARE v_src_role_cnt   INT DEFAULT 0;
    DECLARE v_map_role_cnt   INT DEFAULT 0;
    DECLARE v_err_state VARCHAR(64);
    DECLARE v_err_msg    TEXT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        GET DIAGNOSTICS CONDITION 1
            v_err_state = RETURNED_SQLSTATE,
            v_err_msg   = MESSAGE_TEXT;
        SELECT CONCAT('⚠️ 按钮/区域迁移失败，当前事务已 ROLLBACK。错误: ', v_err_state, ' / ', v_err_msg) AS err_msg;
        RESIGNAL;
    END;

    -- ========================================================
    -- 阶段 0：重建菜单/角色映射（名称锚点）+ 数量校验
    -- ========================================================
    DROP TEMPORARY TABLE IF EXISTS tmp_menu_map;
    CREATE TEMPORARY TABLE tmp_menu_map (
        old_id VARCHAR(32) PRIMARY KEY,
        new_id BIGINT
    ) ENGINE=Memory;

    INSERT INTO tmp_menu_map (old_id, new_id)
    SELECT o.`ID`, MIN(m.menu_id)
    FROM security_ln_pro.sys_sec_operation o
    JOIN `sys_menu` m
        ON m.menu_name = o.`NAME`
       AND m.path = COALESCE(NULLIF(o.URI, ''), o.`ID`)
       AND m.app_id IS NOT NULL
    GROUP BY o.`ID`;

    DROP TEMPORARY TABLE IF EXISTS tmp_role_map;
    CREATE TEMPORARY TABLE tmp_role_map (
        old_id VARCHAR(32) PRIMARY KEY,
        new_id BIGINT
    ) ENGINE=Memory;

    INSERT INTO tmp_role_map (old_id, new_id)
    SELECT r.`ID`, MIN(ro.role_id)
    FROM security_ln_pro.sys_sec_role r
    JOIN `sys_role` ro
        ON ro.role_name = r.`NAME`
       AND ro.tenant_id = '000000'
    GROUP BY r.`ID`;

    -- 校验：映射覆盖率不足 95% 视为锚点失配，终止（剩余可能是重名/改名导致，人工介入）
    SET v_src_menu_cnt = (SELECT COUNT(*) FROM security_ln_pro.sys_sec_operation);
    SET v_map_menu_cnt = (SELECT COUNT(*) FROM tmp_menu_map);
    SET v_src_role_cnt = (SELECT COUNT(*) FROM security_ln_pro.sys_sec_role);
    SET v_map_role_cnt = (SELECT COUNT(*) FROM tmp_role_map);

    IF v_map_role_cnt < CEIL(v_src_role_cnt * 0.95) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            '角色映射重建不足 95%（名称锚点失配），请人工核对 sys_role 与 ln_pro sys_sec_role';
    END IF;
    -- 菜单允许低于源总量（根节点/被跳过的「基础业务系统」子树本就未迁移），
    -- 但至少要覆盖 03 实际迁移量的一半，防止大面积失配
    IF v_map_menu_cnt < (SELECT CEIL(COUNT(*) / 2) FROM sys_menu WHERE app_id IS NOT NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            '菜单映射重建覆盖率过低（menu_name+path 锚点失配），请人工核对';
    END IF;

    -- ========================================================
    -- 阶段 A：URL 级 F 按钮（sys_sec_opaction × sys_sec_operation_opaction）
    -- ========================================================
    START TRANSACTION;

    -- 幂等清理：先删角色关联，再删按钮
    DELETE FROM sys_role_menu
    WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE remark = 'ln-pro-btn-url');
    DELETE FROM sys_menu WHERE remark = 'ln-pro-btn-url';

    -- 只为"能挂到已迁移菜单"的 opaction 生成序列
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_btn_url;
    CREATE TEMPORARY TABLE tmp_seq_btn_url (
        id BIGINT NOT NULL AUTO_INCREMENT,
        opaction_id VARCHAR(200),
        operation_id VARCHAR(32),
        PRIMARY KEY (id),
        UNIQUE KEY uk_op (opaction_id, operation_id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(menu_id), 0) + 100000 FROM sys_menu);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_btn_url AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT IGNORE INTO tmp_seq_btn_url (opaction_id, operation_id)
    SELECT DISTINCT oo.opactionid, oo.operationid
    FROM security_ln_pro.sys_sec_operation_opaction oo
    JOIN tmp_menu_map mm ON mm.old_id = oo.operationid
    JOIN security_ln_pro.sys_sec_opaction oa ON oa.`id` = oo.opactionid;

    INSERT INTO `sys_menu`
        (menu_id, menu_name, parent_id, path, component, icon, menu_type,
         visible, status, is_frame, is_cache, order_num, perms, app_id,
         create_dept, create_by, remark)
    SELECT
        seq.id,
        LEFT(CONCAT(COALESCE(NULLIF(oa.optDesc, ''), oa.stype, '操作'), '-', oa.methodname), 50),
        seq.operation_id_map,
        '',
        '',
        NULL,
        'F',
        '0', '0', 1, 0,
        999,
        LEFT(oa.`id`, 100),
        pm.app_id,
        103, 1,
        'ln-pro-btn-url'
    FROM (
        SELECT seq.id AS id, mm.new_id AS operation_id_map, seq.opaction_id, seq.operation_id
        FROM tmp_seq_btn_url seq
        JOIN tmp_menu_map mm ON mm.old_id = seq.operation_id
    ) seq
    JOIN security_ln_pro.sys_sec_opaction oa ON oa.`id` = seq.opaction_id
    JOIN `sys_menu` pm ON pm.menu_id = seq.operation_id_map;

    COMMIT;

    -- ========================================================
    -- 阶段 B：动作级 F 按钮 + 角色关联（sys_sec_role_opt_action_data）
    -- ========================================================
    START TRANSACTION;

    DELETE FROM sys_role_menu
    WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE remark = 'ln-pro-btn-action');
    DELETE FROM sys_menu WHERE remark = 'ln-pro-btn-action';

    -- DISTINCT (operation, action)：实际授权过的组合才建按钮，避免菜单×动作全量爆炸
    DROP TEMPORARY TABLE IF EXISTS tmp_pair;
    CREATE TEMPORARY TABLE tmp_pair (
        operation_id VARCHAR(32),
        action_id    VARCHAR(32),
        PRIMARY KEY (operation_id, action_id)
    ) ENGINE=Memory;
    INSERT IGNORE INTO tmp_pair (operation_id, action_id)
    SELECT DISTINCT rad.OPERATIONID, rad.ACTIONID
    FROM security_ln_pro.sys_sec_role_opt_action_data rad
    JOIN tmp_menu_map mm ON mm.old_id = rad.OPERATIONID
    -- 预过滤动作字典 JOIN 不上的行，保证"序列行 = 按钮行 = 关联行"三者一致
    JOIN security_ln_pro.sys_sec_action sa ON sa.`ID` = CAST(rad.ACTIONID AS UNSIGNED);

    -- 按钮序列（含 old 双键 → new menu_id 的映射表，供 sys_role_menu 使用）
    DROP TEMPORARY TABLE IF EXISTS tmp_btn_action;
    CREATE TEMPORARY TABLE tmp_btn_action (
        id BIGINT NOT NULL AUTO_INCREMENT,
        operation_id VARCHAR(32),
        action_id    VARCHAR(32),
        PRIMARY KEY (id),
        UNIQUE KEY uk_pair (operation_id, action_id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(menu_id), 0) + 100000 FROM sys_menu);
    SET @sql = CONCAT('ALTER TABLE tmp_btn_action AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_btn_action (operation_id, action_id)
    SELECT p.operation_id, p.action_id FROM tmp_pair p;

    INSERT INTO `sys_menu`
        (menu_id, menu_name, parent_id, path, component, icon, menu_type,
         visible, status, is_frame, is_cache, order_num, perms, app_id,
         create_dept, create_by, remark)
    SELECT
        ba.id,
        LEFT(sa.`name`, 50),
        mm.new_id,
        '', '', NULL, 'F',
        '0', '0', 1, 0,
        998,
        NULL,
        pm.app_id,
        103, 1,
        'ln-pro-btn-action'
    FROM tmp_btn_action ba
    JOIN security_ln_pro.sys_sec_action sa ON sa.`ID` = CAST(ba.action_id AS UNSIGNED)
    JOIN tmp_menu_map mm ON mm.old_id = ba.operation_id
    JOIN `sys_menu` pm ON pm.menu_id = mm.new_id;

    -- 角色关联：sys_sec_role_opt_action_data → sys_role_menu
    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT DISTINCT rm.new_id, ba.id
    FROM security_ln_pro.sys_sec_role_opt_action_data rad
    JOIN tmp_role_map rm ON rm.old_id = rad.ROLEID
    JOIN tmp_btn_action ba
        ON ba.operation_id = rad.OPERATIONID AND ba.action_id = rad.ACTIONID;

    COMMIT;

    -- ========================================================
    -- 阶段 C：数据范围字典（sys_sec_data → sys_dict_type/sys_dict_data）
    -- ========================================================
    START TRANSACTION;

    DELETE FROM sys_dict_data WHERE dict_type = 'ln_pro_data_scope';
    DELETE FROM sys_dict_type WHERE dict_type = 'ln_pro_data_scope';

    SET @new_dict_id = (SELECT IFNULL(MAX(dict_id), 0) + 100000 FROM sys_dict_type);
    INSERT INTO sys_dict_type
        (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
    SELECT
        @new_dict_id,
        '000000', 'ln_pro 数据范围', 'ln_pro_data_scope',
        103, 1, NOW(), 'ln-pro-migrated';

    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dict;
    CREATE TEMPORARY TABLE tmp_seq_dict (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(dict_code), 0) + 100000 FROM sys_dict_data);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_dict AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_dict (old_id) SELECT CAST(`ID` AS CHAR) FROM security_ln_pro.sys_sec_data;

    INSERT INTO sys_dict_data
        (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type,
         create_dept, create_by, create_time, remark)
    SELECT
        seq.id, '000000',
        CAST(sd.`ID` AS UNSIGNED),
        LEFT(sd.`NAME`, 100),
        CAST(sd.`ID` AS CHAR),
        'ln_pro_data_scope',
        103, 1, NOW(), 'ln-pro-migrated'
    FROM tmp_seq_dict seq
    JOIN security_ln_pro.sys_sec_data sd ON sd.`ID` = CAST(seq.old_id AS UNSIGNED);

    COMMIT;

    -- ========================================================
    -- 阶段 D：区域增量合并（sys_sec_region → sys_area，area_code 不存在才插入）
    --   420118/420119（东湖高新/经开区）非标准区划码，大概率缺失 → 插入 level 3；
    --   已存在的标准区划跳过，不覆盖名称（保护标准数据）
    -- ========================================================
    START TRANSACTION;

    DROP TEMPORARY TABLE IF EXISTS tmp_seq_area;
    CREATE TEMPORARY TABLE tmp_seq_area (
        id BIGINT NOT NULL AUTO_INCREMENT,
        area_code VARCHAR(12),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(id), 0) + 100000 FROM sys_area);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_area AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_area (area_code)
    SELECT rg.`ID` FROM security_ln_pro.sys_sec_region rg
    WHERE NOT EXISTS (SELECT 1 FROM sys_area a WHERE a.area_code = rg.`ID`);

    INSERT INTO sys_area
        (id, area_code, area_name, area_level, parent_code, compartment,
         sync_flag, create_time, create_user_name, status)
    SELECT
        seq.id,
        seq.area_code,
        LEFT(rg.`NAME`, 50),
        CASE WHEN RIGHT(seq.area_code, 2) = '00' THEN 2 ELSE 3 END,
        CASE WHEN RIGHT(seq.area_code, 2) = '00' THEN CONCAT(LEFT(seq.area_code, 2), '0000')
             ELSE CONCAT(LEFT(seq.area_code, 4), '00') END,
        LEFT(seq.area_code, 2),
        0, NOW(), 'ln-pro-migrated', 1    FROM tmp_seq_area seq
    JOIN security_ln_pro.sys_sec_region rg ON rg.`ID` = seq.area_code;

    COMMIT;

    -- ========================================================
    -- 阶段 E：保留表直迁（10 张，DELETE + INSERT，幂等）
    --   sys_sec_action / sys_sec_frontend_opaction / sys_sec_role_region /
    --   sys_sec_role_dicts / norm_domain / norm_range / norm_source /
    --   norm_timefeature / norm_unit
    --   （sys_sec_region 已并入 sys_area，不再重复保留）
    -- ========================================================
    START TRANSACTION;

    DELETE FROM sys_sec_action;
    INSERT INTO sys_sec_action (id, name)
    SELECT `ID`, `NAME` FROM security_ln_pro.sys_sec_action;

    DELETE FROM sys_sec_frontend_opaction;
    INSERT INTO sys_sec_frontend_opaction (mname, svcname, methodname)
    SELECT `mname`, `svcname`, `methodname` FROM security_ln_pro.sys_sec_frontend_opaction;

    DELETE FROM sys_sec_role_region;
    INSERT INTO sys_sec_role_region (role_id, region_id)
    SELECT DISTINCT `ROLEID`, `REGIONID` FROM security_ln_pro.sys_sec_role_region;

    DELETE FROM sys_sec_role_dicts;
    INSERT INTO sys_sec_role_dicts (role_id, operation_id, code, name)
    SELECT `ROLEID`, `OPERATIONID`, `code`, `name` FROM security_ln_pro.sys_sec_role_dicts;

    DELETE FROM norm_domain;
    INSERT INTO norm_domain (id, code, name, description, valid, short_name, tab_ext, sort)
    SELECT `ID`, `CODE`, `NAME`, `DESCRIPTION`, `VALID`, `SHORTNAME`, `TABEXT`, `SORT`
    FROM security_ln_pro.norm_domain;

    DELETE FROM norm_range;
    INSERT INTO norm_range (id, code, name, description, valid, pattern, mins, tab_ext)
    SELECT `ID`, `CODE`, `NAME`, `DESCRIPTION`, `VALID`, `PATTERN`, `MINS`, `TABEXT`
    FROM security_ln_pro.norm_range;

    DELETE FROM norm_source;
    INSERT INTO norm_source (id, code, name, description, valid, short_name)
    SELECT `ID`, `CODE`, `NAME`, `DESCRIPTION`, `VALID`, `SHORTNAME`
    FROM security_ln_pro.norm_source;

    DELETE FROM norm_timefeature;
    INSERT INTO norm_timefeature (id, code, name, description, valid, parent, inset, flag, min_range)
    SELECT `ID`, `CODE`, `NAME`, `DESCRIPTION`, `VALID`, `PARENT`, `INSET`, `FLAG`, `MINRANGE`
    FROM security_ln_pro.norm_timefeature;

    DELETE FROM norm_unit;
    INSERT INTO norm_unit (id, code, name, exchange_id, exchange_rate, valid)
    SELECT `ID`, `CODE`, `NAME`, `EXCHANGEID`, `EXCHANGERATE`, `VALID`
    FROM security_ln_pro.norm_unit;

    COMMIT;

    DROP TEMPORARY TABLE IF EXISTS tmp_menu_map;
    DROP TEMPORARY TABLE IF EXISTS tmp_role_map;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_btn_url;
    DROP TEMPORARY TABLE IF EXISTS tmp_pair;
    DROP TEMPORARY TABLE IF EXISTS tmp_btn_action;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dict;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_area;
END $$

DELIMITER ;

-- ============================================================
-- 调用存储过程（执行迁移）
-- ============================================================
CALL proc_migrate_ln_pro_btn_region();
DROP PROCEDURE IF EXISTS proc_migrate_ln_pro_btn_region;

-- ============================================================
-- 验证统计
-- ============================================================
SELECT 'F按钮(URL级) ← opaction×operation_opaction' AS channel, COUNT(*) AS cnt
FROM sys_menu WHERE remark = 'ln-pro-btn-url'
UNION ALL
SELECT 'F按钮(动作级) ← role_opt_action_data', COUNT(*)
FROM sys_menu WHERE remark = 'ln-pro-btn-action'
UNION ALL
SELECT '角色按钮关联 ← role_opt_action_data', COUNT(*)
FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.remark IN ('ln-pro-btn-url', 'ln-pro-btn-action')
UNION ALL
SELECT '数据范围字典 ← sys_sec_data', COUNT(*)
FROM sys_dict_data WHERE dict_type = 'ln_pro_data_scope'
UNION ALL
SELECT 'sys_area 新增 ← sys_sec_region', COUNT(*)
FROM sys_area WHERE create_user_name = 'ln-pro-migrated'
UNION ALL
SELECT '来源总量 opaction 关联(可挂载)', COUNT(*)
FROM security_ln_pro.sys_sec_operation_opaction oo
JOIN security_ln_pro.sys_sec_operation o ON o.`ID` = oo.operationid
WHERE o.URI IS NOT NULL AND o.URI <> ''
UNION ALL
SELECT '来源总量 role_opt_action_data', COUNT(*)
FROM security_ln_pro.sys_sec_role_opt_action_data
UNION ALL
SELECT '来源总量 sys_sec_data', COUNT(*) FROM security_ln_pro.sys_sec_data
UNION ALL
SELECT '来源总量 sys_sec_region', COUNT(*) FROM security_ln_pro.sys_sec_region;

-- ============================================================
-- 脚本结束
-- ============================================================
