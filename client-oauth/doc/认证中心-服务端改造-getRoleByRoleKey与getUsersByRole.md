# 认证中心-服务端改造：`getRoleByRoleKey` / `getRoleUserPage`

> 目标工程：`D:\project\cloud-auth-platform`（认证中心服务端）
> 配套 SDK：`D:\project\cloud-auth-client-oauth`（本仓库，`cloud-auth-client-oauth`，v1.5.6 起）
>
> SDK 已新增：
> - `CloudPlatformHelper.getRoleByRoleKey(String)` → type **`getRoleByRoleKey`**
> - `CloudPlatformHelper.getRoleUserPage(CloudRoleUserQueryBo)` → type **`getRoleUserPage`**
>
> 服务端需注册对应 handler 并扩展 Dubbo 方法方可生效。

---

## 0. 总线契约

### 0.1 `getRoleByRoleKey` — 按角色权限标识查单个角色

| 项 | 说明 |
|----|------|
| type | `getRoleByRoleKey` |
| 请求参数 | `roleKey`（String，必填）——角色权限标识，支持短标识或完整 `{clientId}-{标识}` |
| 响应 | `SaResult.ok().setData(RemoteRoleVo)`：单个角色对象；角色不存在时 data = null |
| 是否需登录 | 是 |

### 0.2 `getRoleUserPage` — 分页查询角色下关联用户列表

| 项 | 说明 |
|----|------|
| type | `getRoleUserPage` |
| 请求参数 | `roleId`（Long，可选）或 `roleKey`（String，可选）——二选一，roleId 优先；`pageNum`（Integer，默认1）；`pageSize`（Integer，默认10） |
| 响应 | `SaResult.ok().setData({rows: List<RemoteUserVo>, total: Long})`；角色无关联用户时 rows=[], total=0 |
| 是否需登录 | 是 |

`RemoteRoleVo` 字段（SDK 侧映射为 `CloudRoleDTO`）：

```java
private Long roleId;
private String roleName;
private String roleKey;
private String dataScope;
```

`RemoteUserVo` 已存在（SDK 侧映射为 `CloudUserVo`）。

分页响应结构（SDK 侧映射为 `CloudUserPageVo`）：

```json
{ "rows": [/* RemoteUserVo[] */], "total": 100 }
```

---

## 1. 改动文件一览

| # | 文件 | 性质 |
|---|------|------|
| 1 | `cloud-auth-platform-api/.../system/api/RemoteRoleService.java` | 接口新增 2 个方法 |
| 2 | `cloud-auth-platform-api/.../system/api/domain/RemoteRoleVo.java` | **新建** VO（或复用已有 RoleVo） |
| 3 | `cloud-auth-platform-modules/.../system/dubbo/RemoteRoleServiceImpl.java` | 实现 2 个新方法 |
| 4 | `cloud-auth-platform-auth/.../controller/SsoServerController.java` | 新增 2 个 handler |

---

## 2. `RemoteRoleService` 接口新增

```java
/**
 * 按角色权限标识（roleKey）查询单个启用角色。
 * 支持短标识自动补全 clientId 匹配。角色不存在时返回 null。
 */
RemoteRoleVo selectRoleByRoleKey(String roleKey, String clientId, String tenantId);

/**
 * 分页查询角色下绑定的正常状态用户列表。
 * 按 roleId 或 roleKey（二选一，roleId 优先）定位角色，返回分页结果。
 */
Map<String, Object> selectRoleUserPage(Long roleId, String roleKey, String clientId, String tenantId, int pageNum, int pageSize);
```

---

## 3. `RemoteRoleServiceImpl` 实现

```java
@Override
public RemoteRoleVo selectRoleByRoleKey(String roleKey, String clientId, String tenantId) {
    if (!StringUtils.hasText(roleKey)) {
        return null;
    }
    List<String> candidates = new ArrayList<>(2);
    candidates.add(roleKey.trim());
    if (StringUtils.hasText(clientId)) {
        String full = clientId + "-" + roleKey.trim();
        if (!candidates.contains(full)) {
            candidates.add(full);
        }
    }
    return TenantHelper.dynamic(tenantId, () -> {
        SysRole role = roleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getRoleKey, candidates)
                .eq(SysRole::getStatus, SystemConstants.NORMAL)
                .last("LIMIT 1"));
        return role == null ? null : toRemoteRoleVo(role);
    });
}

@Override
public Map<String, Object> selectRoleUserPage(Long roleId, String roleKey, String clientId,
                                              String tenantId, int pageNum, int pageSize) {
    // 1. 定位角色：roleId 优先，否则按 roleKey 查
    Long actualRoleId = roleId;
    if (actualRoleId == null) {
        RemoteRoleVo role = selectRoleByRoleKey(roleKey, clientId, tenantId);
        if (role == null) {
            return buildEmptyPage();
        }
        actualRoleId = role.getRoleId();
    }
    // 2. 查 sys_user_role 获取该角色下所有 userId
    final Long rid = actualRoleId;
    List<SysUserRole> userRoles = userRoleMapper.selectList(
        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, rid));
    if (CollUtil.isEmpty(userRoles)) {
        return buildEmptyPage();
    }
    List<Long> allUserIds = StreamUtils.toList(userRoles, SysUserRole::getUserId);
    long total = allUserIds.size();
    // 3. 分页切片
    int fromIndex = (pageNum - 1) * pageSize;
    if (fromIndex >= total) {
        return buildPage(Collections.emptyList(), total);
    }
    int toIndex = (int) Math.min(fromIndex + pageSize, total);
    List<Long> pageUserIds = allUserIds.subList(fromIndex, toIndex);
    // 4. 查用户详情（仅正常状态）
    List<RemoteUserVo> rows = TenantHelper.dynamic(tenantId, () ->
        remoteUserService.selectListByIds(pageUserIds));
    return buildPage(rows, total);
}

private Map<String, Object> buildPage(List<RemoteUserVo> rows, long total) {
    Map<String, Object> page = new HashMap<>(2);
    page.put("rows", rows);
    page.put("total", total);
    return page;
}

private Map<String, Object> buildEmptyPage() {
    return buildPage(Collections.emptyList(), 0L);
}

private RemoteRoleVo toRemoteRoleVo(SysRole role) {
    RemoteRoleVo vo = new RemoteRoleVo();
    vo.setRoleId(role.getRoleId());
    vo.setRoleName(role.getRoleName());
    vo.setRoleKey(role.getRoleKey());
    vo.setDataScope(role.getDataScope());
    return vo;
}
```

> 注入补充（`RemoteRoleServiceImpl` 顶部，如尚未存在）：
> ```java
> private final SysRoleMapper roleMapper;
> private final SysUserRoleMapper userRoleMapper;
> @DubboReference
> private RemoteUserService remoteUserService;
> ```

---

## 4. `SsoServerController` 新增 handler

放在 `configAuth` 方法体内（与 `getUserRoles` 等查询类 handler 相邻）：

```java
// 【登录态】按 roleKey 查询单个角色信息
ssoServerTemplate.messageHolder.addHandle("getRoleByRoleKey", (tpl, message) -> {
    String roleKey = message.getString("roleKey");
    if (!StringUtils.hasText(roleKey)) {
        return SaResult.error("roleKey 不能为空");
    }
    String clientId = resolveClientId(message);
    String tenantId = resolveTenantId(message);
    RemoteRoleVo role = remoteRoleService.selectRoleByRoleKey(roleKey, clientId, tenantId);
    return SaResult.ok().setData(role);
});

// 【登录态】分页查询角色下绑定的用户列表
ssoServerTemplate.messageHolder.addHandle("getRoleUserPage", (tpl, message) -> {
    Long roleId = messageId(message, "roleId");
    String roleKey = message.getString("roleKey");
    if (roleId == null && !StringUtils.hasText(roleKey)) {
        return SaResult.error("roleId 或 roleKey 至少传一个");
    }
    Integer pageNum = message.getInt("pageNum", 1);
    Integer pageSize = message.getInt("pageSize", 10);
    String clientId = resolveClientId(message);
    String tenantId = resolveTenantId(message);
    Map<String, Object> page = remoteRoleService.selectRoleUserPage(
        roleId, roleKey, clientId, tenantId, pageNum, pageSize);
    return SaResult.ok().setData(page);
});
```

> `resolveClientId(message)` / `resolveTenantId(message)`：从消息 ext 或登录会话中获取当前客户端 ID 和租户 ID。

---

## 5. `RemoteRoleVo`（新建 Dubbo DTO）

放在 `cloud-auth-platform-api/cloud-auth-platform-api-system/.../system/api/domain/` 包下：

```java
package io.gitee.chenshaogui.cloudauth.system.api.domain;

import lombok.Data;
import java.io.Serializable;

/**
 * 角色信息（供总线 SDK 查询，仅含业务需要的字段）。
 */
@Data
public class RemoteRoleVo implements Serializable {
    private Long roleId;
    private String roleName;
    private String roleKey;
    private String dataScope;
}
```

> 如已有 `getUserRoles` 改造文档中创建的 `RemoteRoleVo`，可直接复用无需重复创建。

---

## 6. 注意事项

1. **逻辑删除**：`sys_role` 含 `del_flag`（MyBatis-Plus `@TableLogic`），LambdaQueryWrapper 查询自动过滤已删除角色。
2. **租户隔离**：`sys_role` 是租户表，必须用 `TenantHelper.dynamic` 切到正确租户查询。`sys_user_role` 非租户表，但关联用户查询需切租户。
3. **roleKey 双格式兼容**：设计同 `autoBindRolesByRoleKeys`，将原值 + `{clientId}-{原值}` 一并精确匹配，兼容短标识和完整标识。
4. **用户状态过滤**：`selectListByIds` 底层 SQL 含 `status = '0'`（正常），已停用用户不返回。
5. **分页策略**：服务端先查 `sys_user_role` 获取全量 userIds，然后内存切片后查用户详情。若角色下用户量极大（>10000），可后续优化为 DB 级分页（JOIN + LIMIT）。
6. **空结果不报错**：角色不存在、无关联用户 → 返回 null / 空分页 `{rows:[], total:0}`，与 SDK 约定一致。
7. **SDK 缓存**：
   - `getRoleByRoleKey`：参考数据档（默认 300s TTL）——角色定义低频变更
   - `getRoleUserPage`：用户信息档（默认 60s TTL）——角色人员分配变动频率与用户资料一致

---

## 7. `RemoteUserVo` 补充字段：`deptName` + `postNames`

> SDK 侧 `CloudUserVo` 已新增 `deptName`、`postNames`（List<String>）字段，服务端需同步扩展 `RemoteUserVo` 并在各查询方法中填充。

### 7.1 `RemoteUserVo` 新增字段

```java
/** 部门名称 */
private String deptName;

/** 岗位名称列表（如 ["开发工程师", "架构师"]） */
private List<String> postNames;
```

### 7.2 填充方式

| 场景 | 填充逻辑 |
|------|----------|
| `userInfo` (getUserInfoByLoginToken) | `SysUserVo.deptName` 已有（@Translation），MapStruct 会自动映射到 `RemoteUserVo.deptName`；岗位需额外查 `sys_user_post JOIN sys_post` 得到岗位名称列表 |
| `selectListByIds` / `getUserInfoById` | SQL 中加 JOIN sys_dept 取 `dept_name`；批量查 sys_user_post + sys_post 取岗位名列表 |
| `getRoleUserPage` | 同上——在查用户详情时一并返回 deptName、postNames |

### 7.3 示例（selectListByIds 改造思路）

```java
@Override
public List<RemoteUserVo> selectListByIds(List<Long> userIds) {
    if (CollUtil.isEmpty(userIds)) {
        return new ArrayList<>();
    }
    // 原查询 + 加 JOIN sys_dept 取 deptName
    List<SysUserVo> list = userMapper.selectVoList(new LambdaQueryWrapper<SysUser>()
        .select(SysUser::getUserId, SysUser::getDeptId, SysUser::getUserName,
            SysUser::getNickName, SysUser::getUserType, SysUser::getEmail,
            SysUser::getPhonenumber, SysUser::getSex, SysUser::getStatus,
            SysUser::getCreateTime)
        .eq(SysUser::getStatus, SystemConstants.NORMAL)
        .in(SysUser::getUserId, userIds));
    List<RemoteUserVo> result = MapstructUtils.convert(list, RemoteUserVo.class);
    // 批量填充 deptName（可用 DeptService 缓存查询）
    fillDeptNames(result);
    // 批量填充 postNames
    fillPostNames(result);
    return result;
}

private void fillDeptNames(List<RemoteUserVo> users) {
    List<Long> deptIds = users.stream().map(RemoteUserVo::getDeptId)
        .filter(Objects::nonNull).distinct().collect(Collectors.toList());
    if (deptIds.isEmpty()) return;
    Map<Long, String> deptMap = deptService.selectDeptNamesByIds(deptIds);
    users.forEach(u -> {
        if (u.getDeptId() != null) {
            u.setDeptName(deptMap.get(u.getDeptId()));
        }
    });
}

private void fillPostNames(List<RemoteUserVo> users) {
    List<Long> uIds = users.stream().map(RemoteUserVo::getUserId).collect(Collectors.toList());
    // 查 sys_user_post JOIN sys_post 获取每个用户的岗位名称列表
    Map<Long, List<String>> postNamesMap = postService.selectPostNamesListByUserIds(uIds);
    users.forEach(u -> u.setPostNames(postNamesMap.getOrDefault(u.getUserId(), Collections.emptyList())));
}
```

---

## 8. 验证

```bash
# 按 roleKey 查角色（SDK 端点 GET /authCenter/getRoleByKey?roleKey=894bd7683c885153e24844750d59134d-admin）
→ SaResult { code:200, data: {roleId:1, roleName:"超级管理员", roleKey:"894bd...-admin", dataScope:"1"} }

# 按短标识查角色（SDK 端点 GET /authCenter/getRoleByKey?roleKey=admin）
→ 同上（服务端自动补全 clientId 匹配）

# 不存在的 roleKey
→ SaResult { code:200, data: null }

# 分页查角色下用户（SDK 端点 POST /authCenter/getRoleUsers）
# Body: {"roleId": 2, "pageNum": 1, "pageSize": 10}
→ SaResult { code:200, data: {rows: [{userId:1037, userName:"zhangsan", ...}], total: 25} }

# 按 roleKey 分页查角色下用户
# Body: {"roleKey": "common", "pageNum": 2, "pageSize": 5}
→ SaResult { code:200, data: {rows: [...], total: 25} }

# 角色无关联用户
→ SaResult { code:200, data: {rows: [], total: 0} }
```
