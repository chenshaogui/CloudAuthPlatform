-- ----------------------------
-- 2026-09-29：sys_oper_log 补充 client_id 列
-- 用途：子系统通过 SDK（CloudPlatformHelper.writeOperLog）上报的操作日志，
--       以 client_id 标识来源子系统，与后台管理自身的操作审计同表区分、可按系统筛选。
-- ----------------------------

ALTER TABLE `sys_oper_log`
    ADD COLUMN `client_id` varchar(64) DEFAULT '' COMMENT '客户端ID（来源子系统标识）' AFTER `tenant_id`;

-- 按系统维度查询高频，补索引
CREATE INDEX `idx_sys_oper_log_client_id` ON `sys_oper_log` (`client_id`);
