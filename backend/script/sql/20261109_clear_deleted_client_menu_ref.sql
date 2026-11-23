-- =============================================================
-- 清理逻辑删除客户端残留的应用菜单引用（悬空 menu_id）
-- 创建时间：2026-11-09
-- 背景：sys_client.menu_id 上有唯一索引 uk_sys_client_menu_id；
--       逻辑删除(del_flag <> '0')的客户端若仍指向已被删除的应用根菜单，
--       删菜单后该值成为悬空引用，并继续占用唯一键。
-- 范围：仅处理 del_flag 非 '0'（含 NULL）且 menu_id 在 sys_menu 中已不存在的记录；
--       status='1'(停用) 但 del_flag='0' 的客户端仍是有效绑定，不动。
-- 执行时机：删除应用根菜单之后；可重复执行。
-- =============================================================

-- 1) 先核对将被清理的记录
SELECT c.`id`, c.`client_id`, c.`menu_id`, c.`status`, c.`del_flag`
FROM `sys_client` c
LEFT JOIN `sys_menu` m ON m.`menu_id` = c.`menu_id`
WHERE (c.`del_flag` IS NULL OR c.`del_flag` <> '0')
  AND c.`menu_id` IS NOT NULL
  AND m.`menu_id` IS NULL;

-- 2) 清理悬空引用（menu_id 允许 NULL，唯一索引允许多个 NULL）
UPDATE `sys_client` c
LEFT JOIN `sys_menu` m ON m.`menu_id` = c.`menu_id`
SET c.`menu_id` = NULL
WHERE (c.`del_flag` IS NULL OR c.`del_flag` <> '0')
  AND c.`menu_id` IS NOT NULL
  AND m.`menu_id` IS NULL;

-- 3) 复核：应返回空结果集
SELECT c.`id`, c.`client_id`, c.`menu_id`, c.`del_flag`
FROM `sys_client` c
LEFT JOIN `sys_menu` m ON m.`menu_id` = c.`menu_id`
WHERE (c.`del_flag` IS NULL OR c.`del_flag` <> '0')
  AND c.`menu_id` IS NOT NULL
  AND m.`menu_id` IS NULL;
