# 认证中心-服务端改造：`getUserRoles`（按 userId/username 查用户角色）

> 目标工程：`D:\project\cloud-auth-platform`（认证中心服务端）
> 配套 SDK：`D:\project\cloud-auth-client-oauth`（本仓库，`cloud-auth-client-oauth`，v1.5.5 起）
>
> SDK 已新增 `CloudPlatformHelper.getRolesByUserId(Long)` 与 `getRolesByUsername(String)`，
> 走总线 type **`getUserRoles`**。服务端需注册对应 handler 并暴露 Dubbo 方法方可生效。

---

## 0. 总线契约

| 项 | 说明 |
|----|------|
| type | `getUserRoles` |
| 请求参数 | `userId`（Long，可选）或 `username`（String，可选）——二选一，`userId` 优先 |
| 响应 | `SaResult.ok().setData(List<RemoteRoleVo>)`：角色对象列表 |
| 空结果 | 用户不存在或无角色 → `SaResult.ok().setData(Collections.emptyList())`（不报错） |
| 是否需登录 | 是（SDK 自动注入 loginId + client） |

`RemoteRoleVo` 字段（SDK 侧映射为 `CloudRoleDTO`）：

```java
private Long roleId;
private String roleName;
private String roleKey;
private String dataScope;
```

---

## 1. 改动文件一览

| # | 文件 | 性质 |
|---|------|------|
| 1 | `cloud-auth-platform-api/.../system/api/RemoteRoleService.java` | 接口新增 2 个方法 |
| 2 | `cloud-auth-platform-api/.../system/api/domain/RemoteRoleVo.java` | **新建** VO（或复用已有 RoleVo） |
| 3 | `cloud-auth-platform-modules/.../system/dubbo/RemoteRoleServiceImpl.java` | 实现 2 个方法 |
| 4 | `cloud-auth-platform-auth/.../controller/SsoServerController.java` | 新增 `getUserRoles` handler |

---

## 2. `RemoteRoleVo`（新建 Dubbo DTO）

放在 `cloud-auth-platform-api/cloud-auth-platform-api-system/.../system/api/domain/` 包下（与 `RemoteUserVo` 同级）：

```java
package io.gitee.chenshaogui.cloudauth.system.api.domain;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户角色信息（供总线 SDK 查询，仅含业务需要的字段）。
 */
@Data
public class RemoteRoleVo implements Serializable {
    private Long roleId;
    private String roleName;
    private String roleKey;
    private String dataScope;
}
```

---

## 3. `RemoteRoleService` 接口新增

```java
/**
 * 按用户 ID 查询其绑定的启用角色列表（status=0，不含逻辑删除角色）。
 * 用户不存在或无角色时返回空列表。
 */
List<RemoteRoleVo> selectRolesByUserId(Long userId);

/**
 * 按用户登录账号（userName）+ 租户查询其绑定的启用角色列表。
 * 账号不存在或无角色时返回空列表。
 */
List<RemoteRoleVo> selectRolesByUsername(String username, String tenantId);
```

---

## 4. `RemoteRoleServiceImpl` 实现

```java
@Override
public List<RemoteRoleVo> selectRolesByUserId(Long userId) {
    if (userId == null) {
        return Collections.emptyList();
    }
    // sys_role 为租户隔离表——userId 查出的用户归属其租户，先获取 tenantId 再切上下文
    SysUser user = userMapper.selectById(userId);
    if (user == null) {
        return Collections.emptyList();
    }
    return TenantHelper.dynamic(user.getTenantId(), () -> {
        List<SysRoleVo> roles = roleMapper.selectRolesByUserId(userId);
        return toRemoteRoleVos(roles);
    });
}

@Override
public List<RemoteRoleVo> selectRolesByUsername(String username, String tenantId) {
    if (!StringUtils.hasText(username)) {
        return Collections.emptyList();
    }
    String tid = StringUtils.hasText(tenantId) ? tenantId : TenantConstants.DEFAULT_TENANT_ID;
    return TenantHelper.dynamic(tid, () -> {
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, username));
        if (user == null) {
            return Collections.emptyList();
        }
        List<SysRoleVo> roles = roleMapper.selectRolesByUserId(user.getUserId());
        return toRemoteRoleVos(roles);
    });
}

private List<RemoteRoleVo> toRemoteRoleVos(List<SysRoleVo> list) {
    if (CollUtil.isEmpty(list)) {
        return Collections.emptyList();
    }
    List<RemoteRoleVo> result = new ArrayList<>(list.size());
    for (SysRoleVo r : list) {
        RemoteRoleVo vo = new RemoteRoleVo();
        vo.setRoleId(r.getRoleId());
        vo.setRoleName(r.getRoleName());
        vo.setRoleKey(r.getRoleKey());
        vo.setDataScope(r.getDataScope());
        result.add(vo);
    }
    return result;
}
```

> 注入补充（`RemoteRoleServiceImpl` 顶部，如尚未存在）：
> ```java
> private final SysUserMapper userMapper;
> private final SysRoleMapper roleMapper;
> ```
> `SysRoleMapper.selectRolesByUserId` 已有，返回 `List<SysRoleVo>`（`sys_user_role JOIN sys_role WHERE status='0'`）。
> `TenantHelper`、`StringUtils`、`Collections`、`CollUtil` 按已有 import 即可。

---

## 5. `SsoServerController` 新增 handler

放在 `configAuth` 方法体内（与 `getUserInfoById` 等查询类 handler 相邻）：

```java
// 【登录态】按 userId 或 username 查用户绑定的启用角色列表
ssoServerTemplate.messageHolder.addHandle("getUserRoles", (tpl, message) -> {
    Long userId = messageId(message, "userId");
    String username = message.getString("username");
    List<RemoteRoleVo> roles;
    if (userId != null) {
        roles = remoteRoleService.selectRolesByUserId(userId);
    } else if (StringUtils.hasText(username)) {
        // 多租户下同名需 tenantId 限定，SDK 消息已上报 tenantId
        String tenantId = resolveTenantId(message);
        roles = remoteRoleService.selectRolesByUsername(username, tenantId);
    } else {
        return SaResult.error("userId 或 username 至少传一个");
    }
    return SaResult.ok().setData(roles);
});
```

> 注入补充（`SsoServerController` 字段，如尚未存在）：
> ```java
> @DubboReference
> private RemoteRoleService remoteRoleService;
> ```

---

## 6. 注意事项

1. **逻辑删除**：`selectRolesByUserId` 的底层 SQL 含 `sys_role.del_flag = '0' AND sys_role.status = '0'`（MyBatis-Plus 逻辑删除 + 状态过滤），已删除/停用角色不返回。
2. **租户隔离**：`sys_role` 是租户表，必须用 `TenantHelper.dynamic` 切到正确租户查询。`selectRolesByUserId` 通过用户自身 tenantId 切换；`selectRolesByUsername` 使用 SDK 消息上报的 tenantId（`resolveTenantId(message)`）。
3. **空结果不报错**：用户不存在、无角色 → 返回空列表（data = `[]`），与 SDK 约定一致（空集合 = 无角色，不抛异常）。
4. **SDK 缓存**：SDK 用 `remoteCacheList` 按 TTL（参考数据档，默认 300s）缓存结果。服务端角色变更后最多 TTL 秒内 SDK 侧可见。若需即时生效，可后续扩展为服务端主动通知（`publishUserChange`），此处不提前实现。

---

## 7. 验证

```bash
# 按 userId（SDK 端点 /authCenter/getRoles?userId=1037）
→ SaResult { code:200, data: [{roleId:2, roleName:"普通角色", roleKey:"894bd-...-common", dataScope:"1"}] }

# 按 username（SDK 端点 /authCenter/getRoles?username=zhangsan）
→ 同上结构

# 不存在的 userId / username
→ SaResult { code:200, data: [] }
```
