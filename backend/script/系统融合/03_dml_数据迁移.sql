-- ============================================================
-- 系统融合：数据迁移脚本（ln_pro → security-cloud）
-- 适用：将 security_ln_pro 库的数据导入到当前 security-cloud 库
-- 前提：先执行 02_ddl_扩展表.sql 建好扩展表
--
-- 【运行方式】
--   整脚本必须一次性执行（Navicat 直接打开运行 / mysql -uroot -p < xxx.sql）
--   全部逻辑封装在存储过程 proc_migrate_ln_pro() 内，调用即跑
--
-- 【ID 策略】
--   7 张临时自增序列辅助表（id BIGINT AUTO_INCREMENT），起始值 = 目标表 MAX + 100000
--   灌入 ln_pro 老 ID（old_id），AUTO_INCREMENT 自动分配新 ID
--   主表 INSERT ... FROM tmp_seq_xxx JOIN ln_pro 拿到 ID
--
-- 【幂等性】存储过程 + EXIT HANDLER + 事务
--   - DDL（建临时表/灌老ID）在事务外
--   - 主表 INSERT 在事务里
--   - 任意一步失败 → EXIT HANDLER 触发 → ROLLBACK 事务 → 报错信息输出
--   - 临时表不在事务里，会话结束自动清理；DDL 不污染库
--   - 重跑前如果库里有上次"未在事务内"的脏数据（如手工跑出来的临时数据），请先跑 99_dml_清理迁移数据.sql
--
-- 【执行顺序】
--   1) 准备临时自增序列
--   2) 租户
--   3) 字典类型 + 字典内容
--   4) 角色
--   5) 菜单（先建应用根） + 菜单扩展 + 角色菜单
--   6) 部门 + 部门扩展
--   7) 用户 + 用户扩展
--   8) 用户角色关联
--   9) 清理临时表 + 验证统计
-- ============================================================

USE `security-cloud`;

DELIMITER $$

DROP PROCEDURE IF EXISTS proc_migrate_ln_pro $$
CREATE PROCEDURE proc_migrate_ln_pro()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        GET DIAGNOSTICS CONDITION 1
            @err_state = RETURNED_SQLSTATE,
            @err_msg   = MESSAGE_TEXT;
        SELECT CONCAT('⚠️ 迁移失败，已 ROLLBACK。错误代码: ', @err_state, ' / ', @err_msg) AS err_msg;
        RESIGNAL;
    END;

    -- ========================================================
    -- 阶段 1：准备 7 张临时自增序列（DDL 在事务外，auto-commit）
    -- ========================================================

    -- 1.1 租户序列
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_tenant;
    CREATE TEMPORARY TABLE tmp_seq_tenant (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(60),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(id), 0) + 100000 FROM sys_tenant);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_tenant AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_tenant (old_id) SELECT `ID` FROM security_ln_pro.sys_sec_tenant;

    -- 1.2 字典类型序列
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dict_type;
    CREATE TEMPORARY TABLE tmp_seq_dict_type (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(dict_id), 0) + 100000 FROM sys_dict_type);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_dict_type AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_dict_type (old_id) SELECT `ID` FROM security_ln_pro.sys_dict
        WHERE DELETEFLAG = 0 OR DELETEFLAG IS NULL;

    -- 1.3 字典内容序列
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dict_data;
    CREATE TEMPORARY TABLE tmp_seq_dict_data (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(dict_code), 0) + 100000 FROM sys_dict_data);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_dict_data AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_dict_data (old_id) SELECT `ID` FROM security_ln_pro.sys_dictcontent;

    -- 1.4 角色序列
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_role;
    CREATE TEMPORARY TABLE tmp_seq_role (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(role_id), 0) + 100000 FROM sys_role);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_role AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_role (old_id) SELECT `ID` FROM security_ln_pro.sys_sec_role;

    -- 1.5 菜单序列
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_menu;
    CREATE TEMPORARY TABLE tmp_seq_menu (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(menu_id), 0) + 100000 FROM sys_menu);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_menu AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_menu (old_id) SELECT `ID` FROM security_ln_pro.sys_sec_operation;

    -- 1.6 部门序列
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dept;
    CREATE TEMPORARY TABLE tmp_seq_dept (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(32),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(dept_id), 0) + 100000 FROM sys_dept);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_dept AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_dept (old_id) SELECT `ID` FROM security_ln_pro.sys_sec_department;

    -- 1.7 用户序列（跳过已删除的）
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_user;
    CREATE TEMPORARY TABLE tmp_seq_user (
        id BIGINT NOT NULL AUTO_INCREMENT,
        old_id VARCHAR(60),
        PRIMARY KEY (id)
    ) ENGINE=Memory;
    SET @seq_start = (SELECT IFNULL(MAX(user_id), 0) + 100000 FROM sys_user);
    SET @sql = CONCAT('ALTER TABLE tmp_seq_user AUTO_INCREMENT = ', @seq_start);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    INSERT INTO tmp_seq_user (old_id) SELECT `ID` FROM security_ln_pro.sys_sec_user
        WHERE DELETEFLAG IS NULL OR DELETEFLAG <> 1;

    -- ========================================================
    -- 阶段 2：迁移主表（事务包裹，失败 ROLLBACK）
    -- ========================================================

    START TRANSACTION;

    -- --------------------------------------------------
    -- 2.1 租户迁移（sys_sec_tenant → sys_tenant）
    -- --------------------------------------------------
    INSERT INTO `sys_tenant`
        (id, tenant_id, company_name, expire_time, system_id, system_name,
         status, del_flag, account_count, create_dept, create_by)
    SELECT
        seq.id                                AS id,
        t.`ID`                                AS tenant_id,
        t.`NAME`                              AS company_name,
        STR_TO_DATE(t.EXPDATE, '%Y-%m-%d')    AS expire_time,
        t.SYSTEMID                            AS system_id,
        t.SYSTEMNAME                          AS system_name,
        CASE WHEN t.VALID = 1 THEN '0' ELSE '1' END AS status,
        '0'                                   AS del_flag,
        -1                                    AS account_count,
        103                                   AS create_dept,
        1                                     AS create_by
    FROM tmp_seq_tenant seq
    JOIN security_ln_pro.sys_sec_tenant t ON t.`ID` = seq.old_id;

    -- --------------------------------------------------
    -- 2.2 字典类型迁移（sys_dict → sys_dict_type）
    -- --------------------------------------------------
    INSERT INTO `sys_dict_type`
        (dict_id, dict_name, dict_type, tenant_id, create_dept, create_by)
    SELECT
        seq.id        AS dict_id,
        t.`NAME`      AS dict_name,
        t.CODE        AS dict_type,
        '000000'      AS tenant_id,
        103           AS create_dept,
        1             AS create_by
    FROM tmp_seq_dict_type seq
    JOIN security_ln_pro.sys_dict t ON t.`ID` = seq.old_id;

    -- 保存字典类型映射（用于 sys_dict_data 关联）
    DROP TEMPORARY TABLE IF EXISTS tmp_dict_type_map;
    CREATE TEMPORARY TABLE tmp_dict_type_map (
        old_id VARCHAR(32) PRIMARY KEY,
        new_id BIGINT,
        dict_type VARCHAR(100)
    ) ENGINE=Memory;
    INSERT INTO tmp_dict_type_map (old_id, new_id, dict_type)
    SELECT seq.old_id, seq.id, t.CODE
    FROM tmp_seq_dict_type seq
    JOIN security_ln_pro.sys_dict t ON t.`ID` = seq.old_id;

    -- 2.3 字典内容迁移（sys_dictcontent → sys_dict_data）
    -- sys_dict_data 标准表无 status 字段（业务通过修改 label/启用 0/停用 1 实现）
    INSERT INTO `sys_dict_data`
        (dict_code, dict_sort, dict_label, dict_value, dict_type,
         is_default, tenant_id, create_dept, create_by, remark)
    SELECT
        seq.id                                                AS dict_code,
        0                                                     AS dict_sort,
        c.`NAME`                                              AS dict_label,
        c.CODE                                                AS dict_value,
        m.dict_type                                           AS dict_type,
        CASE WHEN c.COMMON = 1 THEN 'Y' ELSE 'N' END          AS is_default,
        '000000'                                              AS tenant_id,
        103                                                   AS create_dept,
        1                                                     AS create_by,
        c.REMARK                                              AS remark
    FROM tmp_seq_dict_data seq
    JOIN security_ln_pro.sys_dictcontent c ON c.`ID` = seq.old_id
    JOIN tmp_dict_type_map m ON m.old_id = c.DICTID;

    -- --------------------------------------------------
    -- 2.4 角色迁移（sys_sec_role → sys_role）
    -- --------------------------------------------------
    INSERT INTO `sys_role`
        (role_id, role_name, role_key, role_sort, data_scope,
         status, del_flag, tenant_id, create_dept, create_by, remark)
    SELECT
        seq.id      AS role_id,
        r.`NAME`    AS role_name,
        r.`ID`      AS role_key,
        0           AS role_sort,
        '1'         AS data_scope,
        '0'         AS status,
        '0'         AS del_flag,
        '000000'    AS tenant_id,
        103         AS create_dept,
        1           AS create_by,
        NULL        AS remark
    FROM tmp_seq_role seq
    JOIN security_ln_pro.sys_sec_role r ON r.`ID` = seq.old_id;

    -- 保存角色映射
    DROP TEMPORARY TABLE IF EXISTS tmp_role_map;
    CREATE TEMPORARY TABLE tmp_role_map (
        old_id VARCHAR(32) PRIMARY KEY,
        new_id BIGINT
    ) ENGINE=Memory;
    INSERT INTO tmp_role_map (old_id, new_id)
    SELECT seq.old_id, seq.id FROM tmp_seq_role seq;

    -- --------------------------------------------------
    -- 2.5 菜单迁移
    -- --------------------------------------------------
    -- 2.5.0 识别 ln_pro 应用层节点，生成 old_id → 所属应用 映射
    -- 源结构：「系统权限」(PARENTID 空, ID='000000') → 应用层 (PARENTID='000000', 16 个) → 业务菜单
    -- MySQL 5.7 无递归 CTE，用 WHILE 循环逐层向下扩散（树无环必终止）
    -- 跳过「基础业务系统」：其子菜单（用户管理/角色管理等旧后台功能）与本系统原生菜单重复
    DROP TEMPORARY TABLE IF EXISTS tmp_menu_app;
    CREATE TEMPORARY TABLE tmp_menu_app (
        old_id     VARCHAR(32) PRIMARY KEY,
        app_old_id VARCHAR(32),
        app_new_id BIGINT
    ) ENGINE=Memory;

    -- 初始：应用层自身（app_old_id = 自身，app_new_id 预取映射 ID）
    INSERT INTO tmp_menu_app (old_id, app_old_id, app_new_id)
    SELECT o.ID, o.ID, seq.id
    FROM security_ln_pro.sys_sec_operation o
    JOIN tmp_seq_menu seq ON seq.old_id = o.ID
    WHERE o.PARENTID = (
        SELECT r.ID FROM security_ln_pro.sys_sec_operation r
        WHERE r.`NAME` = '系统权限'
          AND (r.PARENTID IS NULL OR r.PARENTID = '' OR r.PARENTID = '0')
        LIMIT 1
    );

    -- 排除「基础业务系统」（整个应用不迁移，避免系统菜单重复）
    DELETE FROM tmp_menu_app
    WHERE old_id IN (
        SELECT ID FROM security_ln_pro.sys_sec_operation WHERE `NAME` = '基础业务系统'
    );

    -- WHILE 逐层向下：子节点继承父节点的所属应用
    -- 注意：5.7 中 INSERT 目标临时表不能在同一语句里被再次读取（即使包派生表也报 Can't reopen），
    --       用乒乓表 tmp_menu_app_stage 中转：先写 stage（读 app），再写 app（读 stage），两表各只打开一次
    DROP TEMPORARY TABLE IF EXISTS tmp_menu_app_stage;
    CREATE TEMPORARY TABLE tmp_menu_app_stage (
        old_id     VARCHAR(32) PRIMARY KEY,
        app_old_id VARCHAR(32),
        app_new_id BIGINT
    ) ENGINE=Memory;

    downward: LOOP
        DELETE FROM tmp_menu_app_stage;
        INSERT IGNORE INTO tmp_menu_app_stage (old_id, app_old_id, app_new_id)
        SELECT c.ID, t.app_old_id, t.app_new_id
        FROM security_ln_pro.sys_sec_operation c
        JOIN tmp_menu_app t ON c.PARENTID = t.old_id;
        -- 合并回主映射表：INSERT IGNORE 去重，实际新增 0 行说明整棵树已映射完 → 退出
        INSERT IGNORE INTO tmp_menu_app (old_id, app_old_id, app_new_id)
        SELECT s.old_id, s.app_old_id, s.app_new_id FROM tmp_menu_app_stage s;
        IF ROW_COUNT() = 0 THEN
            LEAVE downward;
        END IF;
    END LOOP;
    DROP TEMPORARY TABLE IF EXISTS tmp_menu_app_stage;

    -- 保存菜单映射（必须在 sys_menu 业务菜单 INSERT 之前建好，
    --   因为下面 INSERT 的 SELECT 列表里的子查询会引用它）
    DROP TEMPORARY TABLE IF EXISTS tmp_menu_map;
    CREATE TEMPORARY TABLE tmp_menu_map (
        old_id VARCHAR(32) PRIMARY KEY,
        new_id BIGINT
    ) ENGINE=Memory;
    INSERT INTO tmp_menu_map (old_id, new_id)
    SELECT seq.old_id, seq.id FROM tmp_seq_menu seq;

    -- 2.5.1 sys_sec_operation → sys_menu
    --   应用层节点 → menu_type='A'、parent_id=0、app_id=NULL、is_frame='0'（getApplications 过滤条件）
    --   其余节点   → app_id = 所属应用的 new_id
    --   JOIN tmp_menu_app 只迁应用子树（根「系统权限」、被跳过应用自然排除）
    --   （tmp_menu_map 仅被标量子查询引用一次、tmp_menu_app 仅 JOIN 一次，避免 5.7 临时表 reopen 冲突）
    INSERT INTO `sys_menu`
        (menu_id, menu_name, parent_id, path, component, icon, menu_type,
         visible, status, is_frame, is_cache, order_num, perms, app_id,
         create_dept, create_by, remark)
    SELECT
        seq.id                                                AS menu_id,
        o.`NAME`                                              AS menu_name,
        -- 应用层节点 parent_id 固定 0（其 PARENTID='000000' 是未迁移的根「系统权限」，
        -- tmp_menu_map 里有根的映射，不能走 COALESCE 转换，否则 parent_id 指向不存在的菜单）
        CASE WHEN ma.old_id = ma.app_old_id THEN 0
             ELSE COALESCE(
                 (SELECT pm.new_id FROM tmp_menu_map pm WHERE pm.old_id = o.PARENTID),
                 0
             )
        END                                                   AS parent_id,
        COALESCE(NULLIF(o.URI, ''), o.ID)                     AS path,
        CASE WHEN ma.old_id = ma.app_old_id THEN 'Layout' ELSE o.COMPONENT END AS component,
        o.ICON                                                AS icon,
        CASE
            WHEN ma.old_id = ma.app_old_id THEN 'A'
            -- ln-pro TYPE 语义：2=菜单/目录，1=操作，0=按钮
            -- 有真实 URI（且不等于自身 ID，不是「空回退到 ID」的占位）→ 'C' 菜单
            -- 否则 TYPE=2 才是目录 'M'，TYPE=1 → 'F'，TYPE=0 有 URI → 'C'，否则 'F'
            WHEN o.TYPE = 2 AND o.URI IS NOT NULL AND o.URI <> ''
                 AND o.URI <> o.ID THEN 'C'
            WHEN o.TYPE = 2 THEN 'M'
            WHEN o.TYPE = 1 THEN 'F'
            WHEN o.TYPE = 0 AND (o.URI IS NOT NULL AND o.URI <> ''
                 AND o.URI <> o.ID) THEN 'C'
            ELSE 'F'
        END                                                   AS menu_type,
        CASE WHEN o.HIDDEN = 1 THEN '1' ELSE '0' END          AS visible,
        '0'                                                   AS status,
        CASE WHEN ma.old_id = ma.app_old_id THEN '0' ELSE 1 END AS is_frame,
        CASE WHEN o.KEEP_ALIVE = 1 THEN 1 ELSE 0 END          AS is_cache,
        COALESCE(CAST(LEAST(2147483647, CAST(NULLIF(o.ORDERNUM, '') AS UNSIGNED)) AS SIGNED), 0) AS order_num,
        o.PERMS                                               AS perms,
        -- 应用节点 app_id = 自身 menu_id（与 selectMenuListByMenuId 的 eq(appId).ne(menuId) 设计对齐）
        CASE WHEN ma.old_id = ma.app_old_id THEN seq.id ELSE ma.app_new_id END AS app_id,
        103                                                   AS create_dept,
        1                                                     AS create_by,
        NULL                                                  AS remark
    FROM tmp_seq_menu seq
    JOIN security_ln_pro.sys_sec_operation o ON o.`ID` = seq.old_id
    JOIN tmp_menu_app ma ON ma.old_id = o.`ID`
    ORDER BY ma.app_old_id, CAST(NULLIF(o.ORDERNUM, '') AS UNSIGNED), o.`ID`;

    -- 2.5.2 收敛映射表：只保留实际迁移的菜单（根节点、被跳过应用及其子树的映射删除，
    --   防止 2.5.3/2.5.4 生成指向不存在菜单的孤儿数据）
    DELETE mm FROM tmp_menu_map mm
    LEFT JOIN sys_menu m ON m.menu_id = mm.new_id
    WHERE m.menu_id IS NULL;

    -- 2.5.3 sys_menu_ext
    INSERT INTO `sys_menu_ext`
        (menu_id, is_route, keep_alive, redirect_url, internal_external,
         class_name, remember_log, log_string, config)
    SELECT
        mm.new_id,
        o.IS_ROUTE,
        o.KEEP_ALIVE,
        o.REDIRECT,
        o.INTERNAL_OR_EXTERNAL,
        o.CLASSNAME,
        o.REMEMBERLOG,
        o.LOGSTRING,
        o.CONFIG
    FROM security_ln_pro.sys_sec_operation o
    JOIN tmp_menu_map mm ON mm.old_id = o.`ID`;

    -- 2.5.4 sys_sec_role_operation → sys_role_menu
    INSERT INTO `sys_role_menu` (role_id, menu_id)
    SELECT DISTINCT rm.new_id, mm.new_id
    FROM security_ln_pro.sys_sec_role_operation ro
    JOIN tmp_role_map rm ON rm.old_id = ro.ROLEID
    JOIN tmp_menu_map mm ON mm.old_id = ro.OPERATIONID;

    -- --------------------------------------------------
    -- 2.6 部门迁移
    -- --------------------------------------------------
    -- 保存部门映射（必须在 sys_dept INSERT 之前建好，子查询会引用它）
    DROP TEMPORARY TABLE IF EXISTS tmp_dept_map;
    CREATE TEMPORARY TABLE tmp_dept_map (
        old_id VARCHAR(32) PRIMARY KEY,
        new_id BIGINT
    ) ENGINE=Memory;
    INSERT INTO tmp_dept_map (old_id, new_id)
    SELECT seq.old_id, seq.id FROM tmp_seq_dept seq;

    -- 2.6.1 sys_dept 主表（parent_id 直接用子查询从 tmp_dept_map 转换）
    INSERT INTO `sys_dept`
        (dept_id, tenant_id, parent_id, dept_name, dept_category, dept_code,
         order_num, leader, phone, email, status, del_flag, create_dept, create_by)
    SELECT
        seq.id                                            AS dept_id,
        '000000'                                          AS tenant_id,
        COALESCE(
            (SELECT pm.new_id FROM tmp_dept_map pm WHERE pm.old_id = d.PARENTID),
            0
        )                                                 AS parent_id,
        d.`NAME`                                          AS dept_name,
        NULL                                              AS dept_category,
        d.CODE                                            AS dept_code,
        0                                                 AS order_num,
        NULL                                              AS leader,
        d.TEL                                             AS phone,
        LEFT(d.EMAIL, 50)                                 AS email,
        CASE WHEN d.USED = 10 THEN '0' ELSE '1' END      AS status,
        '0'                                               AS del_flag,
        103                                               AS create_dept,
        1                                                 AS create_by
    FROM tmp_seq_dept seq
    JOIN security_ln_pro.sys_sec_department d ON d.`ID` = seq.old_id
    ORDER BY (CASE WHEN d.PARENTID IS NULL OR d.PARENTID = '' THEN 0 ELSE 1 END), d.`RANK`, d.`ID`;

    -- 2.6.3 sys_dept_ext
    INSERT INTO `sys_dept_ext`
        (dept_id, dept_manager, dept_manager_tel, fax, postcode, address,
         on_duty_tel, url, short_name, print_name, description, dept_type,
         deployed, happen_area, area, used)
    SELECT
        dm.new_id,
        d.DEPTMANAGER,
        d.DEPTMANAGERTEL,
        d.FAX,
        d.POSTCODE,
        d.ADDRESS,
        d.ONDUTYTEL,
        d.URL,
        d.SHORTNAME,
        d.PRINTNAME,
        d.DESCRIPTION,
        d.TYPE,
        d.DEPLOYED,
        d.HAPPENAREA,
        d.AREA,
        d.USED
    FROM security_ln_pro.sys_sec_department d
    JOIN tmp_dept_map dm ON dm.old_id = d.`ID`;

    -- --------------------------------------------------
    -- 2.7 用户迁移
    -- --------------------------------------------------
    INSERT INTO `sys_user`
        (user_id, tenant_id, dept_id, user_name, nick_name, user_type,
         email, phonenumber, sex, password, status, del_flag,
         login_ip, login_date, create_dept, create_by, create_time,
         update_by, update_time, remark, avatar)
    SELECT
        seq.id                                                       AS user_id,
        '000000'                                                     AS tenant_id,
        (SELECT new_id FROM tmp_dept_map
            WHERE old_id = SUBSTRING_INDEX(u.DEPARTMENTID, ',', 1))   AS dept_id,
        u.`ID`                                                       AS user_name,
        u.`NAME`                                                     AS nick_name,
        'sys_user'                                                   AS user_type,
        LEFT(u.EMAIL, 50)                                            AS email,
        LEFT(u.MOBILE, 11)                                           AS phonenumber,
        CASE u.SEX WHEN '1' THEN '1' WHEN '0' THEN '0' ELSE '2' END AS sex,
        -- ln_pro 密码是 MD5（如 e10adc...），保留原值由 PasswordAuthStrategy 兼容验证
        COALESCE(u.PASSWORD, '')                                     AS password,
        CASE WHEN IFNULL(u.VALID, 1) = 1 THEN '0' ELSE '1' END       AS status,
        CASE WHEN u.DELETEFLAG = 1 THEN '1' ELSE '0' END            AS del_flag,
        LEFT(u.LASTIP, 128)                                          AS login_ip,
        NULL                                                         AS login_date,
        103                                                          AS create_dept,
        1                                                            AS create_by,
        u.CREATETIME                                                 AS create_time,
        NULL                                                         AS update_by,
        u.UPDATETIME                                                 AS update_time,
        u.DESCRIPTION                                                AS remark,
        NULL                                                         AS avatar
    FROM tmp_seq_user seq
    JOIN security_ln_pro.sys_sec_user u ON u.`ID` = seq.old_id;

    -- 保存用户映射
    DROP TEMPORARY TABLE IF EXISTS tmp_user_map;
    CREATE TEMPORARY TABLE tmp_user_map (
        old_id VARCHAR(60) PRIMARY KEY,
        new_id BIGINT
    ) ENGINE=Memory;
    INSERT INTO tmp_user_map (old_id, new_id)
    SELECT seq.old_id, seq.id FROM tmp_seq_user seq;

    -- 2.7.1 sys_user_ext
    INSERT INTO `sys_user_ext`
        (user_id, identify, identify_card, real_user_name, usci, enterprise_code,
         transport_license_code, enterprise_name, enterprise_identity, qc,
         city_district, pwd_reset_flag, shared_user, user_type_ln, level_id,
         need_send_daily_paper, ca_id, desktop_config, background, user_source_type,
         tel, address)
    SELECT
        um.new_id,
        u.IDENTIFY,
        u.IDENTIFYCARD,
        u.`NAME`,
        u.USCI,
        u.ENTERPRISECODE,
        u.TRANSPORTLICENSECODE,
        NULL,
        NULL,
        NULL,
        NULL,
        'N',
        u.SHAREDUSER,
        u.USERTYPE,
        CAST(u.LEVEL_ID AS UNSIGNED),
        u.NEEDSENDDAILYPAPER,
        u.CAID,
        u.DESKTOPCONFIG,
        u.BACKGROUND,
        u.USERSOURCETYPE,
        u.TEL,
        u.ADDRESS
    FROM security_ln_pro.sys_sec_user u
    JOIN tmp_user_map um ON um.old_id = u.`ID`
    WHERE u.DELETEFLAG IS NULL OR u.DELETEFLAG <> 1;

    -- --------------------------------------------------
    -- 2.8 用户角色关联
    -- --------------------------------------------------
    INSERT INTO `sys_user_role` (user_id, role_id)
    SELECT DISTINCT um.new_id, rm.new_id
    FROM security_ln_pro.sys_sec_user_role ur
    JOIN tmp_user_map um ON um.old_id = ur.USERID
    JOIN tmp_role_map rm ON rm.old_id = ur.ROLEID;

    -- --------------------------------------------------
    -- 2.9 创建 superadmin 角色 + 绑定 USERTYPE=99 的迁移用户
    -- ln_pro 没有 superadmin 角色，超管由 USERTYPE=99 标识
    -- 兼容登录后 LoginHelper.isSuperAdmin() 通过角色判断识别超管
    -- --------------------------------------------------
    -- 2.9.1 创建 superadmin 角色（如果不存在；role_id 固定 100000，确保不和原系统冲突）
    INSERT INTO sys_role
        (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, tenant_id, create_dept, create_by)
    SELECT 100000, '超级管理员', 'superadmin', 1, '1', '0', '0', '000000', 103, 1
    FROM dual
    WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'superadmin');

    -- 2.9.2 取 superadmin 角色 ID
    SET @superadmin_role_id = (SELECT role_id FROM sys_role WHERE role_key = 'superadmin');

    -- 2.9.3 给 USERTYPE=99 的迁移用户绑 superadmin 角色
    -- 用 tmp_user_map 精确圈定迁移用户（避免依赖 create_by）
    INSERT INTO sys_user_role (user_id, role_id)
    SELECT um.new_id, @superadmin_role_id
    FROM tmp_user_map um
    JOIN sys_user_ext e ON e.user_id = um.new_id
    WHERE e.user_type_ln = 99
      AND NOT EXISTS (
          SELECT 1 FROM sys_user_role ur
          WHERE ur.user_id = um.new_id AND ur.role_id = @superadmin_role_id
      );

    -- --------------------------------------------------
    -- 2.9.4 把迁移过来的 admin 调整为 user_id = 1
    -- 目的：兼容 LoginHelper.isSuperAdmin(userId) 按 userId==1 判断的逻辑（菜单/角色等模块）。
    --       无参版 isSuperAdmin() 已支持角色判定，但 SysMenuServiceImpl 等 6 处用的是带参版。
    -- 识别：原 RuoYi admin 密码 = bcrypt(LENGTH≈60)；迁移 admin 密码 = ln_pro MD5(LENGTH=32)。
    -- 幂等：多次执行无副作用。
    -- --------------------------------------------------
    -- 取出需要操作的 user_id（多次执行：条件不满足则保持 NULL，整个语句为 0 行动作）
    SET @old_admin_id := (
        SELECT user_id FROM sys_user
        WHERE user_id = 1 AND del_flag = '0'
          AND user_name = 'admin' AND LENGTH(password) = 60
        LIMIT 1
    );
    SET @migrated_admin_id := (
        SELECT user_id FROM sys_user
        WHERE user_name = 'admin' AND del_flag = '0'
          AND LENGTH(password) = 32 AND user_id <> 1
        LIMIT 1
    );

    -- Step 1：清掉原 admin（user_id=1, bcrypt）及其子表
    DELETE FROM sys_user_role WHERE user_id = @old_admin_id AND @old_admin_id IS NOT NULL;
    DELETE FROM sys_user_post WHERE user_id = @old_admin_id AND @old_admin_id IS NOT NULL;
    DELETE FROM sys_user_ext  WHERE user_id = @old_admin_id AND @old_admin_id IS NOT NULL;
    DELETE FROM sys_user      WHERE user_id = @old_admin_id AND @old_admin_id IS NOT NULL;

    -- Step 2：把迁移 admin 的 user_id 改为 1（同时级联更新子表）
    UPDATE sys_user      SET user_id = 1 WHERE user_id = @migrated_admin_id AND @migrated_admin_id IS NOT NULL;
    UPDATE sys_user_ext  SET user_id = 1 WHERE user_id = @migrated_admin_id AND @migrated_admin_id IS NOT NULL;
    UPDATE sys_user_role SET user_id = 1 WHERE user_id = @migrated_admin_id AND @migrated_admin_id IS NOT NULL;
    UPDATE sys_user_post SET user_id = 1 WHERE user_id = @migrated_admin_id AND @migrated_admin_id IS NOT NULL;

    -- 校验
    SELECT
        '✅ 2.9.4 admin user_id 已对齐'                                       AS msg,
        (SELECT COUNT(*) FROM sys_user WHERE user_id = 1 AND LENGTH(password) = 32) AS ln_admin_now_id_1,
        (SELECT COUNT(*) FROM sys_user WHERE user_id = 1 AND LENGTH(password) = 60) AS old_admin_gone;

    COMMIT;

    -- ========================================================
    -- 阶段 3：清理 + 验证
    -- ========================================================

    DROP TEMPORARY TABLE IF EXISTS tmp_dict_type_map;
    DROP TEMPORARY TABLE IF EXISTS tmp_role_map;
    DROP TEMPORARY TABLE IF EXISTS tmp_menu_map;
    DROP TEMPORARY TABLE IF EXISTS tmp_dept_map;
    DROP TEMPORARY TABLE IF EXISTS tmp_user_map;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_tenant;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dict_type;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dict_data;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_role;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_menu;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_dept;
    DROP TEMPORARY TABLE IF EXISTS tmp_seq_user;

    SELECT '✅ 迁移完成' AS msg;
END $$

DELIMITER ;

-- ============================================================
-- 调用存储过程（执行迁移）
-- ============================================================
CALL proc_migrate_ln_pro();

-- ============================================================
-- 验证统计
-- ============================================================
SELECT 'sys_tenant'               AS tbl, COUNT(*) AS cnt FROM sys_tenant         WHERE company_name = '重点车辆监管平台'
UNION ALL SELECT 'sys_dict_type',      COUNT(*) FROM sys_dict_type    WHERE create_dept = 103
UNION ALL SELECT 'sys_dict_data',      COUNT(*) FROM sys_dict_data    WHERE create_dept = 103
UNION ALL SELECT 'sys_role',           COUNT(*) FROM sys_role         WHERE create_dept = 103
UNION ALL SELECT 'sys_menu (多应用)',    COUNT(*) FROM sys_menu        WHERE create_dept = 103 AND (app_id IS NOT NULL OR menu_type = 'A')
UNION ALL SELECT 'sys_menu_ext',       COUNT(*) FROM sys_menu_ext
UNION ALL SELECT 'sys_role_menu (迁移)', COUNT(*) FROM sys_role_menu  WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE app_id IS NOT NULL)
UNION ALL SELECT 'sys_dept',           COUNT(*) FROM sys_dept         WHERE create_dept = 103
UNION ALL SELECT 'sys_dept_ext',       COUNT(*) FROM sys_dept_ext
UNION ALL SELECT 'sys_user',           COUNT(*) FROM sys_user         WHERE create_dept = 103
UNION ALL SELECT 'sys_user_ext',       COUNT(*) FROM sys_user_ext
UNION ALL SELECT 'sys_user_role (迁移)', COUNT(*) FROM sys_user_role  WHERE user_id IN (SELECT user_id FROM sys_user WHERE create_dept = 103);

-- 清理存储过程（可选）
DROP PROCEDURE IF EXISTS proc_migrate_ln_pro;

-- ============================================================
-- 脚本结束
-- ============================================================