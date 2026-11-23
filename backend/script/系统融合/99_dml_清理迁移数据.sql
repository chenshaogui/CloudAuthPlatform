-- ============================================================
-- 清理上一次的迁移数据（按 ln_pro 老数据反向删除）
-- 使用场景：03/06 跑了一半出错，需要清理重跑
--   ⚠️ 本脚本不清理日志数据（sys_oper_log / sys_logininfor / sys_oss / sys_notice），
--      这些由 04/05 脚本自带幂等清理段负责（按 client_id / client_key / service / remark 删除）。
--
-- 【清理策略】按 ln_pro 标志性字段识别迁移数据：
--   sys_user.user_name    IN (SELECT ID FROM security_ln_pro.sys_sec_user)
--   sys_role.role_key     IN (SELECT ID FROM security_ln_pro.sys_sec_role)
--   sys_dict_type.dict_type IN (SELECT CODE FROM security_ln_pro.sys_dict)
--   sys_dict_type.dict_type = 'ln_pro_data_scope'           ← 06 数据范围字典
--   sys_menu.create_dept = 103  (迁移菜单统一标记，含 menu_type='A' 应用节点)
--   sys_menu.remark IN ('ln-pro-btn-url','ln-pro-btn-action') ← 06 F 按钮
--   sys_dept.dept_code    IN (SELECT CODE FROM security_ln_pro.sys_sec_department)
--   sys_tenant.tenant_id  IN (SELECT ID FROM security_ln_pro.sys_sec_tenant)
--   sys_area.create_user_name = 'ln-pro-migrated'           ← 06 区域增量
--   保留表（06 阶段 E）：DELETE 全表
--
-- 【清理顺序】先子表后主表，避免外键约束
-- ============================================================

USE `security-cloud`;

-- ============================================================
-- 1. 用户相关
-- ============================================================

-- 1.1 用户角色关联
DELETE FROM `sys_user_role`
WHERE user_id IN (
    SELECT user_id FROM `sys_user`
    WHERE user_name IN (SELECT `ID` FROM security_ln_pro.sys_sec_user)
);

-- 1.2 用户扩展
DELETE FROM `sys_user_ext`
WHERE user_id IN (
    SELECT user_id FROM `sys_user`
    WHERE user_name IN (SELECT `ID` FROM security_ln_pro.sys_sec_user)
);

-- 1.3 用户主表
DELETE FROM `sys_user`
WHERE user_name IN (SELECT `ID` FROM security_ln_pro.sys_sec_user);

-- ============================================================
-- 2. 角色 / 菜单相关
-- ============================================================

-- 2.1 角色菜单关联（删迁移菜单的关联；现有菜单的关联不动）
DELETE FROM `sys_role_menu`
WHERE menu_id IN (
    SELECT menu_id FROM `sys_menu`
    WHERE create_dept = 103
      AND (app_id IS NOT NULL OR menu_type = 'A')
);

-- 2.2 菜单扩展
DELETE FROM `sys_menu_ext`
WHERE menu_id IN (
    SELECT menu_id FROM `sys_menu`
    WHERE create_dept = 103
      AND (app_id IS NOT NULL OR menu_type = 'A')
);

-- 2.3 菜单主表（含迁移的应用节点，app_id 为 NULL 靠 menu_type='A' + create_dept=103 识别）
DELETE FROM `sys_menu`
WHERE create_dept = 103
  AND (app_id IS NOT NULL OR menu_type = 'A');

-- 2.4 角色
DELETE FROM `sys_role`
WHERE role_key IN (SELECT `ID` FROM security_ln_pro.sys_sec_role);

-- ============================================================
-- 3. 部门相关
-- ============================================================

DELETE FROM `sys_dept_ext`
WHERE dept_id IN (
    SELECT dept_id FROM `sys_dept`
    WHERE dept_code IN (SELECT `CODE` FROM security_ln_pro.sys_sec_department)
);

DELETE FROM `sys_dept`
WHERE dept_code IN (SELECT `CODE` FROM security_ln_pro.sys_sec_department);

-- ============================================================
-- 4. 字典相关
-- ============================================================

DELETE FROM `sys_dict_data`
WHERE dict_type IN (SELECT `CODE` FROM security_ln_pro.sys_dict);

DELETE FROM `sys_dict_type`
WHERE dict_type IN (SELECT `CODE` FROM security_ln_pro.sys_dict);

-- ============================================================
-- 5. 租户
-- ============================================================

DELETE FROM `sys_tenant`
WHERE tenant_id IN (SELECT `ID` FROM security_ln_pro.sys_sec_tenant);

-- ========================================================
-- 6. 06 脚本迁移数据（F 按钮 / 数据范围字典 / 区域 / 保留表）
--    顺序：先清 sys_role_menu 关联，再清各业务表
-- ========================================================

-- 6.1 F 按钮：先删角色关联，再删按钮菜单本身
DELETE FROM `sys_role_menu`
WHERE menu_id IN (
    SELECT menu_id FROM `sys_menu`
    WHERE remark IN ('ln-pro-btn-url', 'ln-pro-btn-action')
);

DELETE FROM `sys_menu`
WHERE remark IN ('ln-pro-btn-url', 'ln-pro-btn-action');

-- 6.2 数据范围字典（06 阶段 C）
DELETE FROM `sys_dict_data`  WHERE dict_type = 'ln_pro_data_scope';
DELETE FROM `sys_dict_type`  WHERE dict_type = 'ln_pro_data_scope';

-- 6.3 sys_area 增量合并的数据（06 阶段 D，按 create_user_name 识别）
DELETE FROM `sys_area`       WHERE create_user_name = 'ln-pro-migrated';

-- 6.4 06 阶段 E 保留表（DELETE + INSERT 直迁，清理 = 清空全部）
DELETE FROM `sys_sec_action`;
DELETE FROM `sys_sec_frontend_opaction`;
DELETE FROM `sys_sec_role_region`;
DELETE FROM `sys_sec_role_dicts`;
DELETE FROM `norm_domain`;
DELETE FROM `norm_range`;
DELETE FROM `norm_source`;
DELETE FROM `norm_timefeature`;
DELETE FROM `norm_unit`;

-- ============================================================
-- 验证清理结果（应为 0）
-- ============================================================

SELECT
    (SELECT COUNT(*) FROM `sys_user`     WHERE user_name IN (SELECT ID FROM security_ln_pro.sys_sec_user))   AS leftover_user,
    (SELECT COUNT(*) FROM `sys_role`     WHERE role_key  IN (SELECT ID FROM security_ln_pro.sys_sec_role))    AS leftover_role,
    (SELECT COUNT(*) FROM `sys_menu`     WHERE create_dept = 103 AND (app_id IS NOT NULL OR menu_type = 'A'))  AS leftover_menu,
    (SELECT COUNT(*) FROM `sys_menu`     WHERE remark IN ('ln-pro-btn-url','ln-pro-btn-action'))              AS leftover_btn,
    (SELECT COUNT(*) FROM `sys_dept`     WHERE dept_code IN (SELECT CODE FROM security_ln_pro.sys_sec_department)) AS leftover_dept,
    (SELECT COUNT(*) FROM `sys_dict_type` WHERE dict_type IN (SELECT CODE FROM security_ln_pro.sys_dict))    AS leftover_dict_type,
    (SELECT COUNT(*) FROM `sys_dict_type` WHERE dict_type = 'ln_pro_data_scope')                            AS leftover_data_scope,
    (SELECT COUNT(*) FROM `sys_area`     WHERE create_user_name = 'ln-pro-migrated')                        AS leftover_area,
    (SELECT COUNT(*) FROM `sys_sec_action`) AS leftover_sec_action,
    (SELECT COUNT(*) FROM `norm_domain`)    AS leftover_norm_domain,
    (SELECT COUNT(*) FROM `sys_tenant`   WHERE tenant_id IN (SELECT ID FROM security_ln_pro.sys_sec_tenant))   AS leftover_tenant;

-- ============================================================
-- 清理脚本结束。可重跑 03_dml_数据迁移.sql。
-- ============================================================