# 认证中心-服务端改造：`addUser` 回传新用户 userId

> 目标工程：`D:\project\cloud-auth-platform`（认证中心服务端）
> 配套 SDK：`D:\project\cloud-auth-client-oauth`（本仓库，`cloud-auth-client-oauth`，v1.5.4 起）
>
> **本文是"待办"改造说明**，与《认证中心-服务端改造-新增用户与修改当前用户与注册默认密码.md》
> （那份记录的是**服务端已落地**的 `addUser`/`updateUser`/注册默认密码）分开维护，避免把未完成项
> 混进已完成文档里。

---

## 1. 背景

SDK 侧 `CloudPlatformHelper.addUser(...)` 自 **v1.5.4** 起已把返回类型从 `Object` 收窄为 **`Long`**
（走 `callApi("addUser", ..., Long.class)`），控制器 `/authCenter/addUser` 也以 `SaResult.data(userId)` 透出。

但服务端 `addUser` handler 目前成功分支只返回 **`SaResult.ok()`（不带 data）**，
所以 SDK 收到的 `SaResult.getData()` 恒为 `null` —— `addUser` 拿不到新用户 id（不报错，只是 id 为空）。

要让前端/接入系统真正拿到新建用户的 `userId`，需要在服务端把 `insert` 生成的 id **逐层回写透传**。

---

## 2. 现状（服务端调用链，userId 在哪一步被丢弃）

```
SDK addUser → 总线 type="addUser"
  → SsoServerController 的 "addUser" handler
      Boolean ok = remoteUserService.registerUserInfo(bo);
      return ok ? SaResult.ok() : SaResult.error(...)        // ← 没有 setData(userId)
  → RemoteUserServiceImpl.registerUserInfo(RemoteUserBo)
      return userService.registerUser(sysUserBo, tenantId);  // ← 只回 boolean
  → SysUserServiceImpl.registerUser(...)
      int insert = baseMapper.insert(sysUser);               // ← sysUser.getUserId() 已是新 id
      ... 用 userId 建 client 关联 / ext / 绑角色，但没回写、没返回
```

新 `userId` 在最内层 `SysUserServiceImpl.registerUser` 已生成，只是没往外传。

---

## 3. 改造方案（三处回写透传，均不改方法签名、不影响注册链路）

> 关键点：`registerUser` / `registerUserInfo` 的返回类型保持 `boolean` 不变（注册链路也在用，不能动它）。
> 新 id 通过**入参 BO 的 `userId` 字段**回写带出——MyBatis-Plus `insert` 后实体会自动回填主键，
> 再手动把 id 写回上层 BO，最后由 handler `setData` 透出。

### 3.1 `SysUserServiceImpl.registerUser(...)`
`baseMapper.insert(sysUser)` 之后补一行，把新 id 回写到上层传入的 BO：
```java
int insert = baseMapper.insert(sysUser);
// ← 新增：把新 id 回写到入参 BO（registerUser 的 sysUserBo / SysUser 同源，据实际变量名取用）
sysUserBo.setUserId(sysUser.getUserId());
```

### 3.2 `RemoteUserServiceImpl.registerUserInfo(...)`
调用 `registerUser` 之后把 id 透出到 `RemoteUserBo`（若原写法是 `return userService.registerUser(...)`，
需先接到局部变量再回写）：
```java
boolean res = userService.registerUser(sysUserBo, remoteUserBo.getTenantId());
// ← 新增：把 id 回写到入参 bo
remoteUserBo.setUserId(sysUserBo.getUserId());
return res;
```

### 3.3 `addUser` handler 成功分支
由 `SaResult.ok()` 改为带上回写的 id：
```java
Boolean ok = remoteUserService.registerUserInfo(bo);
return Boolean.TRUE.equals(ok) ? SaResult.ok().setData(bo.getUserId()) : SaResult.error("新增用户失败");
```
> `bo` 即 handler 里解析出的 `RemoteUserBo`，经 §3.2 已回写 `userId`。

---

## 4. 兼容与注意

1. **向后兼容**：服务端未改造前，SDK `addUser` 返回 `null`（不抛异常），与旧行为一致；改造后即返回新 `userId`。
2. **注册链路不受影响**：本方案只加"回写 + setData"，不改 `registerUser`/`registerUserInfo` 的签名与返回值，
   普通注册（`register` handler）走同一 `registerUserInfo` 也拿不到 id 无关紧要，行为不变。
3. **雪花/自增主键**：`sysUser.getUserId()` 依赖 MyBatis-Plus `insert` 回填主键；确认 `SysUser` 主键策略
   能在 insert 后取到 id（雪花、自增均可；若为 DB 触发器等特殊策略需另取）。

---

## 5. 验证

```bash
# 经 SDK 端点 /authCenter/addUser 转发（登录态，需带 token）
body: { "userName":"zhangsan", "phonenumber":"131xxxx0000", "roleKeys":["common"] }

# 期望响应（改造后）：data 为新建用户 userId
{ "code": 200, "msg": "成功", "data": 1037 }
```
用返回的 `data` 再调 `getUserInfoById` / `getUserInfo` 能查到该用户即验证成功。
