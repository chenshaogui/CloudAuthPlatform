-- =============================================================
-- 客户端个性化：登录/注册/重置密码页面布局配置
-- 创建时间：2026-10-12
-- 用途：sys_client 新增 layout_config(text) + register_enabled(char1) + reset_pwd_enabled(char1)，
--       鉴权中心前端按 clientId 动态渲染登录/注册/重置密码页面（不同 client 可指定不同模板与配置）。
-- 兼容性：默认值与已存在客户端保持兼容（register 关闭、reset 开启），不影响存量。
-- =============================================================

-- 1) 新增字段
ALTER TABLE `sys_client`
    ADD COLUMN `layout_config`      text        DEFAULT NULL COMMENT '登录/注册/重置页个性化配置JSON，结构见鉴权中心前端文档' AFTER `sign_key`,
    ADD COLUMN `register_enabled`   char(1)     DEFAULT '0' COMMENT '是否开放注册 0=关闭 1=开放' AFTER `layout_config`,
    ADD COLUMN `reset_pwd_enabled`  char(1)     DEFAULT '1' COMMENT '是否开放重置密码 0=关闭 1=开放' AFTER `register_enabled`;

-- 2) 存量客户端：resetPwd 保持开启（默认已是 1，无需更新）；如需关闭某 client 的注册，需手动 UPDATE

-- 3) 验证 SQL
--    SELECT client_id, register_enabled, reset_pwd_enabled, LENGTH(layout_config) FROM sys_client;
