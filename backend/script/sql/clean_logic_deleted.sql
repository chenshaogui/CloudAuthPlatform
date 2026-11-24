-- ============================================================================
-- 逻辑删除数据物理清理脚本
-- 依据脚本: security-cloud.sql
-- 警告: 物理删除不可恢复，执行前务必备份数据库，建议在业务低峰期执行
-- ----------------------------------------------------------------------------
-- 清理规则（共 10 张表）:
--   del_flag = '1'   : sys_client / sys_dept / sys_role / sys_social
--                      sys_tenant / sys_tenant_package / sys_user
--   status   = -1    : sys_area / sys_mail_template
--   status   = 9     : sys_sms_send_record
-- ============================================================================

-- 一、清理前待删除数量核对
SELECT 'sys_client' AS table_name, COUNT(*) AS delete_count FROM sys_client WHERE del_flag = '1'
UNION ALL SELECT 'sys_dept', COUNT(*) FROM sys_dept WHERE del_flag = '1'
UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role WHERE del_flag = '1'
UNION ALL SELECT 'sys_social', COUNT(*) FROM sys_social WHERE del_flag = '1'
UNION ALL SELECT 'sys_tenant', COUNT(*) FROM sys_tenant WHERE del_flag = '1'
UNION ALL SELECT 'sys_tenant_package', COUNT(*) FROM sys_tenant_package WHERE del_flag = '1'
UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user WHERE del_flag = '1'
UNION ALL SELECT 'sys_area', COUNT(*) FROM sys_area WHERE status = -1
UNION ALL SELECT 'sys_mail_template', COUNT(*) FROM sys_mail_template WHERE status = -1
UNION ALL SELECT 'sys_sms_send_record', COUNT(*) FROM sys_sms_send_record WHERE status = 9;

-- 二、执行清理
START TRANSACTION;

-- 1. del_flag 逻辑删除
DELETE FROM sys_user            WHERE del_flag = '1';
DELETE FROM sys_dept            WHERE del_flag = '1';
DELETE FROM sys_role            WHERE del_flag = '1';
DELETE FROM sys_client          WHERE del_flag = '1';
DELETE FROM sys_social          WHERE del_flag = '1';
DELETE FROM sys_tenant          WHERE del_flag = '1';
DELETE FROM sys_tenant_package  WHERE del_flag = '1';

-- 2. status 逻辑删除
DELETE FROM sys_area            WHERE status = -1;
DELETE FROM sys_mail_template   WHERE status = -1;
DELETE FROM sys_sms_send_record WHERE status = 9;

COMMIT;

-- 三、复核：重新执行第一部分查询，结果应全部为 0

-- 四、可选：清理物理删除后遗留的关联表孤儿数据（按需取消注释）
-- DELETE FROM sys_user_role          WHERE user_id NOT IN (SELECT user_id FROM sys_user);
-- DELETE FROM sys_user_post          WHERE user_id NOT IN (SELECT user_id FROM sys_user);
-- DELETE FROM sys_user_client_config WHERE user_id NOT IN (SELECT user_id FROM sys_user)
--                                       OR client_id NOT IN (SELECT id FROM sys_client);
-- DELETE FROM sys_client_role        WHERE client_id NOT IN (SELECT id FROM sys_client)
--                                       OR role_id NOT IN (SELECT role_id FROM sys_role);
-- DELETE FROM sys_role_menu          WHERE role_id NOT IN (SELECT role_id FROM sys_role);
-- DELETE FROM sys_role_dept          WHERE role_id NOT IN (SELECT role_id FROM sys_role);
