-- Remove the key-management feature and its client-key bindings.
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 1624 AND 1629;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 1624 AND 1629;

DROP TABLE IF EXISTS `sys_key_rotation`;
DROP TABLE IF EXISTS `sys_key_info`;

ALTER TABLE `sys_client`
    DROP COLUMN `app_key_id`,
    DROP COLUMN `platform_key_id`,
    DROP COLUMN `key_pool_size`,
    DROP COLUMN `sign_key`;
