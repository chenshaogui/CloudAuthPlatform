-- =============================================================
-- 部门表新增"部门编码"字段 + 唯一性约束
-- 创建时间：2026-09-28
-- 用途：与 cloud-auth-client-oauth SDK 的 getDeptCodeById(s) 接口配套，
--       业务方可在部门管理中维护 dept_code，客户端按 dept_id 拿值走参考数据档（300s TTL）。
--
-- 唯一性约定：同租户内 sys_dept.dept_code 必须唯一（MySQL UNIQUE 索引默认允许多个 NULL，
--           即未填写编码的部门不受影响）。该约束是 SDK 反向查询（deptCode → deptId）
--           和业务方数据导入按 code 对齐 deptId 的前置条件。
-- =============================================================

-- 0) 先查一下当前是否存在重复 dept_code（含 NULL 不算），
--    若存在则需要先人工处理（合并 / 加后缀）后再跑下面的索引 DDL。
--    排查 SQL：
--      SELECT tenant_id, dept_code, COUNT(*) cnt
--        FROM sys_dept
--       WHERE dept_code IS NOT NULL AND dept_code <> ''
--       GROUP BY tenant_id, dept_code
--      HAVING cnt > 1;

-- 1) 新增字段（首次部署执行；已执行过 20250928_add_dept_code_to_sys_dept.sql 的跳过本行）
ALTER TABLE `sys_dept`
    ADD COLUMN `dept_code` varchar(64) DEFAULT NULL COMMENT '部门编码（业务自定义标识，区别于 dept_id 自增主键）' AFTER `dept_category`;

-- 2) 唯一性约束：同租户 (tenant_id) 内 dept_code 不允许重复
--    - 多租户隔离：必须带 tenant_id，否则不同租户间会冲突
--    - MySQL UNIQUE 允许多个 NULL，不影响未填编码的存量部门
ALTER TABLE `sys_dept`
    ADD UNIQUE INDEX `uk_sys_dept_tenant_code` (`tenant_id`, `dept_code`);