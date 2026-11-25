# 统一授权认证平台SDK（v1.2.0，初始开发阶段）

> ⚠️ **当前处于初始开发阶段**，所有变更直接集成在 v1.2.0 主干上，**不再单独递增版本号**。
> 历史版本：v1.1.0

> 📦 **Maven 依赖版本约定**：下文所有 `<dependency>` 示例中，本 SDK 的 `<artifactId>cloud-auth-client-oauth</artifactId>` 一律使用 Maven 占位符 `${cloud-auth-client-oauth.version}` 表示，**当前值为 `1.2.0`**。
> 升级时无需逐处修改 README 示例，建议在业务项目自家 `pom.xml` 的 `<properties>` 块中统一定义一次即可：
>
> ```xml
> <properties>
>     <cloud-auth-client-oauth.version>1.2.3</cloud-auth-client-oauth.version>
> </properties>
> ```
>
> 复制示例到项目里即可直接生效。

> 📖 **客户端界面帮助文档**：SDK 内置一份随包发布的静态帮助页 `sdk-help.html`（位于 `src/main/resources/static/`，已改名避开 Knife4j 占用的 `/doc.html`）。是否对外暴露由接入系统自行决定：需客户端在白名单（如 `sa-token.permit-paths: [/sdk-help.html]`）或静态资源映射中放开后，开发者方可访问 `<服务域名>/sdk-help.html` 查看：sa-token 集成配置、所有对外 Controller 接口契约（参数 / 返回值）、以及 `CloudPlatformHelper` 调用工具方法总览。

## 1、概述

`cloud-auth-client-oauth` 是统一授权认证中心的客户端 SDK，负责：

- 与认证中心完成 SSO 单点登录（基于 sa-token-sso）
- 从认证中心拉取用户、菜单路由、机构、权限码等数据
- 提供 `@SaCheckPermission` / `@SaCheckRole` 等注解的本地 O(1) 校验
- 自动跟随认证中心的权限变更（节流式后台同步）

业务项目集成方式有两种：

| 方式 | 适用场景 | 配置项 |
|---|---|---|
| **方式一：仅单点登录** | 老项目已有自己的用户/角色/权限体系，只借用统一登录 | `sa-token.only-login: true` |
| **方式二：完全接入** | 客户端所有用户、角色、菜单都从认证中心统一管理（推荐） | `sa-token.only-login` 不配置或 `false` |

> 保持依赖版本一致：本 SDK 与服务端 `cloud-auth-platform-auth` 共同使用 `sa-token.version=1.44.0`、`forest.version=1.6.4`、`hutool.version=5.8.20`。

---

## 2、方式一：仅单点登录模式

### 2.1、Maven 引入

```xml
<dependency>
  <groupId>io.gitee.chenshaogui.cloudauth</groupId>
  <artifactId>cloud-auth-client-oauth</artifactId>
  <version>${cloud-auth-client-oauth.version}</version>
  <!--
    客户端没启用 Redis 时，需排除 sa-token-redis-template。
    启用 Redis 时把下面 <exclusions> 块注释掉。
  -->
  <!--<exclusions>
      <exclusion>
          <groupId>cn.dev33</groupId>
          <artifactId>sa-token-redis-template</artifactId>
      </exclusion>
  </exclusions>-->
</dependency>
```

### 2.2、`application.yml` 配置

```yaml
sa-token:
  # token 名称 (同时也是 cookie 名称)
  token-name: Authorization
  # 是否允许同一账号并发登录
  is-concurrent: true
  # 多人登录同一账号时是否共用一个 token
  is-share: false
  # jwt 秘钥
  jwt-secret-key: abcdefghijklmnopqrstuvwxyz
  # 仅做单点登录（方式一必填）
  only-login: true
  # sso-client 相关配置
  sso-client:
    # 应用标识
    client: b3ed3d93b017ba5921e014efc78a6ad6
    # sso-server 端主机地址
    server-url: https://example.com/uaa-gateway/auth
    # 前后端分离时需额外配置 auth-url（与 server-url 共存）
    auth-url: https://example.com/etc2-html/cloudAuthPlatform/index.html#/ssologin
    # 使用 HTTP 请求校验 ticket
    is-http: true
    # API 接口调用秘钥
    secret-key: qaJ5LPLxCPSZTtsfqTB8xkQC7MOTyqhx0KGJEhy84Fo
```

### 2.3、业务 Controller 示例

> **注意**：方式一推荐使用 `getUserInfoByTicketWithoutLogin(ticket)` 拉取用户信息，**不会消费 ticket 也不会自动登录**。
> 如果误用 `getUserInfoByTicket(ticket)`，ticket 会被消费并自动完成本地 SSO 登录。

```java
@Slf4j
@SaIgnore
@RequiredArgsConstructor
@RestController
@RequestMapping("/sso")
public class SSOLoginController {

    private final SysLoginService loginService;

    @PostMapping("/doLoginByTicket")
    public R<Map<String, Object>> doLoginByTicket(@RequestBody String body) {
        JSONObject reqBody = JSONObject.parseObject(body);
        Integer platformIndex = reqBody.getInteger("platformIndex");
        if (platformIndex == null) {
            return R.fail("平台序号【platformIndex】不能为空");
        }
        String ticket = reqBody.getString("ticket");
        // 1. 通过 ticket 仅拉取用户信息（不消费 ticket、不自动登录）
        CloudUserInfoVo userInfoByTicket = CloudPlatformHelper.getUserInfoByTicketWithoutLogin(ticket);
        CloudUserVo byTicketUser = userInfoByTicket.getUser();
        if (byTicketUser == null) {
            return R.fail("ticket 无效或用户不存在");
        }

        // 2. 客户端业务使用自己的逻辑登录（样例如下）
        // todo 用户不存在时将获取的用户同步到本地
        // todo 客户端增加用户时，需要调用 CloudPlatformHelper.syncUserInfo 向服务端添加用户
        String token = loginService.loginByPhone(byTicketUser.getPhonenumber(), platformIndex);

        Map<String, Object> rspMap = new HashMap<>();
        rspMap.put("token", token);
        return R.ok(rspMap);
    }

    @PostMapping("/getSsoAuthUrl")
    public R<String> getSsoAuthUrl(@RequestBody String body) {
        JSONObject reqBody = JSONObject.parseObject(body);
        String clientLoginUrl = reqBody.getString("clientLoginUrl");
        String serverAuthUrl = CloudPlatformHelper.getSsoAuthUrl(clientLoginUrl);
        log.info("get auth cloud url: {}", serverAuthUrl);
        return R.ok(serverAuthUrl);
    }
}
```

### 2.4、前端 SSO 登录页适配

参照业务侧前端框架的 SSO 接入文档，前端在登录页调用 `/sso/getSsoAuthUrl` 拿跳转地址，
用户在认证中心完成登录后回调业务端的 `clientLoginUrl`，业务端再调用 `/sso/doLoginByTicket`
完成本地登录。

**接口签名（本次更新）**：

| 参数 | 必填 | 说明 |
|---|---|---|
| `clientLoginUrl` | 否 | 票据投递地址，授权中心登录后回跳此处。**不传时 SDK 自动按 `clientId` 查绑定菜单取默认地址** |
| `back` | 否 | 登录成功后客户端再回跳到哪（用户原本想去的业务页面）。可空 |

> 历史版本 `clientLoginUrl` 为必填；新版改为可选，详见 7.8 节。

---

## 3、方式二：完全接入授权中心

### 3.1、前提

1. 老项目适配，需要摘除项目中已有的 saToken 相关代码，特别是继承了 ruoyi-cloud-plus 的项目
2. 项目中需要放行的接口（白名单）需重新适配

### 3.2、Maven 引入

```xml
<dependencies>
    <dependency>
        <groupId>io.gitee.chenshaogui.cloudauth</groupId>
        <artifactId>cloud-auth-client-oauth</artifactId>
        <version>${cloud-auth-client-oauth.version}</version>
    </dependency>
    <!-- Spring Boot 2.x 用 sa-token-spring-boot-starter；3.x 用 sa-token-spring-boot3-starter -->
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-spring-boot-starter</artifactId>
        <version>1.44.0</version>
    </dependency>
</dependencies>
```

### 3.3、`application.yml` 配置

```yaml
sa-token:
  # token 名称（同时也是 cookie 名称）
  token-name: Authorization
  # token 风格（默认可取值：uuid、simple-uuid、random-32、random-64、random-128、tik）
  # 注意：本 SDK 自 1.2.0 起统一使用 JWT 模式（StpLogicJwtForSimple，与认证中心对齐），
  #       该配置项在 JWT 模式下已由 SaJwtUtil 接管、不再生效，保留仅为兼容历史配置。
  token-style: random-32
  # 开启内网服务调用鉴权（不允许越过 gateway 访问内网服务）
  check-same-token: true
  # 打印操作日志
  is-log: true
  # sso-client 相关配置
  sso-client:
    # 应用标识
    client: sso-client3
    # sso-server 统一认证中心服务端地址
    server-url: http://sa-sso-server.com:9000
    # 使用 HTTP 请求校验 ticket
    is-http: true
    # API 接口调用秘钥
    secret-key: SSO-C3-kQwIOrYvnXmSDkwEiFngrKidMcdrgKor
```

> [!IMPORTANT]
> 自 1.2.0 起，本 SDK 会自动注入以下两项默认配置（**业务方无需配置**，如需调整可在配置中心显式覆盖）：
>
> | 配置项 | 默认值 | 作用 |
> |---|---|---|
> | `sa-token.dynamic-active-timeout` | `true` | 登录时写入 token 的「最后活跃时间」key，否则认证中心会判定 `token 已被冻结` |
> | `sa-token.jwt-secret-key` | `abcdefghijklmnopqrstuvwxyz` | JWT 签名密钥，**必须与认证中心一致** |
>
> 详见 [8、Sa-Token 模式对齐](#8sa-token-模式对齐v120-起自动生效)。

### 3.4、SSO 相关 Controller

> **本节代码已由 SDK 内置**。`CloudTokenSsoClientController` 已统一在 `/sso` 路径下接管以下 7 个端点：
> `/sso/getSsoAuthUrl`、`/sso/doLoginByTicket`、`/sso/login`、`/sso/logout`、
> `/sso/logoutCall`、`/sso/pushC`、`/sso/logoutByAlone`，
> 业务方**无需再手写** `SSOLoginController`。仅在需要自定义返回结构（例如包装成项目内部的 `R<T>`）或扩展 Swagger 注解时，
> 在自身工程定义同名 Controller（Spring 会优先加载业务方的 Bean）即可，下面代码块保留作为参考实现：
>
> **v1.3.3 新增免登认证端点**（同属 `CloudTokenSsoClientController`）：`/sso/captcha`、`/sso/sendSmsCode`、`/sso/sendSmsCodeByPhone`、`/sso/sendSmsCodeForRegister`、`/sso/sendSmsCodeByReset`、`/sso/resetPassword`、`/sso/register`（含类型化端点 `/sso/registerByCaptcha`、`/sso/registerBySms`）、`/sso/loginByPassword`、`/sso/loginByPhone`、`/sso/loginByWechat`。供小程序 / Android 自定义登录注册/忘记密码，走 pushS 总线、免登录、无需 RSA 公钥，详见 4.11 与接口文档 1.4~1.13。
>
> **v1.3.4 新增**：`/sso/loginByPasswordNoCaptcha`（账号密码登录、**免图形验证码**，入参同 `/sso/loginByPassword`，走服务端独立 `loginByPasswordNoCaptcha` handler；是否免验证码由“命中哪个端点”决定，`/sso/loginByPassword` 的验证码不会被绕过），详见接口文档 1.14。
>
> **v1.3.3 新增登录态账户操作**（属 `CloudAuthCenterController`，`/authCenter`，**需登录**）：`/authCenter/updatePassword`（修改密码）、`/authCenter/refreshToken`（续期）。同样走 pushS 总线，SDK 自动注入当前 `loginId`，详见 4.12。
>
> **本次更新**：`getSsoAuthUrl` 端点已升级——`clientLoginUrl` 改为可选、**新增 `back` 参数**（可选）。
> `clientLoginUrl` 为空时 SDK 自动按 `clientId` 从服务端拉取默认地址，详见 7.8 节。
> **业务方覆盖该端点时务必按新签名实现**——仅引用旧单参版本时不会编译失败，但 fallback 行为会丢失。

```java
@Slf4j
@RestController
@RequestMapping("/sso")
public class SSOLoginController {

    /**
     * 登录（使用授权码完成本地登录）
     */
    @PostMapping("/login")
    public R<CloudLoginVo> login(@RequestBody String body) {
        JSONObject reqBody = JSONObject.parseObject(body);
        String ticket = reqBody.getString("ticket");
        if (ticket == null || ticket.isEmpty()) {
            return R.fail("ticket 不能为空");
        }
        // 使用授权码登录，返回 CloudLoginVo（含 accessToken、expireIn、clientId）
        CloudLoginVo loginVo = CloudPlatformHelper.doLoginByTicket(ticket);
        return R.ok(loginVo);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        CloudPlatformHelper.ssoLogout();
        return R.ok();
    }

    /**
     * 单点注销回调（服务端回调客户端，必须实现否则 SSO 全局注销失败）
     */
    @RequestMapping("/logoutCall")
    public Object logoutCall() {
        return CloudPlatformHelper.ssoLogoutCall();
    }

    /**
     * 接收消息推送（服务端回调客户端，必须实现）
     */
    @RequestMapping("/pushC")
    public Object pushC() {
        return CloudPlatformHelper.ssoPushC();
    }

    /**
     * 获取授权地址
     */
    @PostMapping("/getAuthUrl")
    public R<String> getAuthUrl(@RequestBody String body) {
        JSONObject reqBody = JSONObject.parseObject(body);
        String clientLoginUrl = reqBody.getString("clientLoginUrl");
        String serverAuthUrl = CloudPlatformHelper.getSsoAuthUrl(clientLoginUrl);
        log.info("Get oauth cloud url: {}", serverAuthUrl);
        return R.ok(serverAuthUrl);
    }
}
```

### 3.5、老系统集成

> **SDK 已内置** `/authCenter/getMenuRouters`（获取路由信息）、`/authCenter/getLoginUserInfo`（获取用户信息）
> 与 `/authCenter/clearCache`（清除 SDK 缓存，后台维护用）三个端点，
> 通过 `CloudAuthCenterController`（`@RequestMapping("/authCenter")`）暴露，业务方**无需再手写** Controller。
>
> > **注意**：`CloudAuthCenterController` 类级别**未标注** `@SaIgnore`，由 SDK 全局鉴权过滤器对 `/authCenter/**` 进行登录校验，
> > 三个端点**必须登录后才能调用**，未登录访问将返回 401。前两个由前端登录成功后各调 1 次（自行缓存），
> > `clearCache` 为后台维护用（见 5.1），不建议直接暴露给普通用户。
>
> 如需保留老前端路径（`/system/menu/getRouters`、`/system/user/getInfo`），可参考下面示例在自身工程定义 Controller 转发调用。

1. 路由接口（若需保留 `/system/menu/getRouters`）：

```java
@RequestMapping("/system/menu")
public class SysMenuController extends BaseController {

    /**
     * 获取路由信息
     */
    @GetMapping("/getRouters")
    public R<List<CloudRouterVo>> getRouters() {
        return R.ok(CloudPlatformHelper.getRouters());
    }
}
```

2. 用户信息接口（若需保留 `/system/user/getInfo`）：

```java
@RequestMapping("/system/user")
public class SysUserController extends BaseController {

    /**
     * 获取用户信息
     */
    @GetMapping("/getInfo")
    public R<CloudUserInfoVo> getInfo() {
        return R.ok(CloudPlatformHelper.getUserInfo());
    }
}
```

3. 删除系统中已有的鉴权、角色、菜单、用户等相关的 system 代码。可以选择保留系统字典 dict 相关代码。

---

## 4、CloudPlatformHelper 工具类

### 4.1、基础功能

| 方法 | 说明 |
|---|---|
| `getRouters()` | 获取前端路由信息（自动从本地 token 推断 loginId） |
| `getUserInfo()` | 获取用户信息（含 user、permissions、roles） |
| `getLoginUser()` | 获取登录用户完整信息（含部门、客户端、租户等） |
| `doLoginByTicket(ticket)` | 使用授权码登录（消费 ticket，自动 `StpUtil.login`），返回 `CloudLoginVo` |
| `isLogin()` | 仅本地 token 校验，**不发起远程调用**，适合过滤器使用 |
| `isRemoteLogin()` | 远程校验登录态，仅在必要时使用（如关键操作二次确认） |
| `getUserInfoByTicket(ticket)` | **会消费 ticket 并自动登录**，返回用户信息 |
| `getUserInfoByTicketWithoutLogin(ticket)` | **不消费 ticket、不自动登录**，返回用户信息 |
| `getSsoAuthUrl(clientLoginUrl)` | 获取 SSO 认证中心登录地址 |
| `getSsoAuthUrl(clientLoginUrl, back)` | 同上，登录成功后回跳 `back`（本次更新） |
| `getDefaultLoginUrl()` | 从服务端按 `clientId` 拉取默认登录地址（本次更新，详见 7.8 节） |

### 4.2、机构信息

```java
// 根据多个 id 拼接部门名
String deptName = CloudPlatformHelper.getDeptNameByIds("1,2,3");
// 根据单个 id 拼接部门名
String deptName = CloudPlatformHelper.getDeptNameById(1L);
// 根据多个 id 拼接部门编码（与 getDeptNameByIds 对称）
String deptCode = CloudPlatformHelper.getDeptCodeByIds("1,2,3");
// 根据单个 id 获取部门编码
String deptCode = CloudPlatformHelper.getDeptCodeById(1L);
// 根据 id 获取子部门列表
List<CloudSysDeptVo> deptList = CloudPlatformHelper.getDeptChildListById(1L);
// 直接获取当前登录人所在部门的部门编码（便捷方法，内部等价于 getDeptCodeById(getDeptId())）
String currentDeptCode = CloudPlatformHelper.getCurrentDeptCode();
```

> **部门编码（dept_code）** 是服务端 `sys_dept.dept_code` 字段，业务方可在部门管理中维护；
> 返回的 `CloudSysDeptVo` 也会带 `deptCode` 字段。`getDeptCodeByIds` 对应的 SSO 消息类型与服务端处理器见下表：

| 客户端方法 | SSO 消息类型 | 服务端数据来源 |
|---|---|---|
| `getDeptCodeById(s)` | `getDeptCodeByIds` | `RemoteDeptService.selectDeptCodeByIds(deptIds)` → `SysDeptServiceImpl.selectDeptCodeByIds`（按 id 复用 `selectDeptById` 缓存） |

### 4.3、用户信息

```java
// 根据 id 获取单个用户
CloudUserVo user = CloudPlatformHelper.getUserInfoById(1L);
// 根据 id 列表获取多个用户
List<CloudUserVo> users = CloudPlatformHelper.getUserListByIds("1,2,3");
```

### 4.4、同步用户信息到授权中心

```java
CloudSyncUserVo cloudUserVo = BeanUtil.toBean(param, CloudSyncUserVo.class);
cloudUserVo.setPhonenumber(param.getMobilePhone());
SaResult saResult = CloudPlatformHelper.syncUserInfo(cloudUserVo);
if (saResult.getCode() != 200) {
    return R.fail(saResult.getMsg());
}
```

### 4.5、当前用户便捷获取

```java
Long userId = CloudPlatformHelper.getUserId();
String username = CloudPlatformHelper.getUsername();
String tenantId = CloudPlatformHelper.getTenantId();
Long deptId = CloudPlatformHelper.getDeptId();
String deptName = CloudPlatformHelper.getDeptName();
String deptCategory = CloudPlatformHelper.getDeptCategory();
String nickname = CloudPlatformHelper.getNickname();
boolean superAdmin = CloudPlatformHelper.isSuperAdmin();
boolean tenantAdmin = CloudPlatformHelper.isTenantAdmin();
```

### 4.6、权限主动刷新

`@SaCheckPermission` / `@SaCheckRole` 默认从本地 Session 缓存读取权限码，性能 O(1)。
当管理员后台调整了用户角色 / 菜单权限后，业务方可调用 `refreshLoginUser()` 强制刷新本地缓存：

```java
// 管理员修改用户权限后，业务方在合适的时机调用
CloudLoginUser refreshed = CloudPlatformHelper.refreshLoginUser();
```

刷新后该用户后续的 `@SaCheckPermission` 校验立即按最新权限生效，无需重新登录。

### 4.6.1、客户端子系统内的角色剪裁（1.2.1）

> 客户端角色在中央系统统一以 `{clientId}-{roleKey}` 的形式存储（如 `d5763d20...-admin`、
> `d5763d20...-user`），SDK 的 `CloudAuthPermissionImpl.getRoleList()` 会按"当前请求所属客户端 ID"
> 对 `rolePermission` 做剪裁，业务侧可直接使用裸角色名：
>
> ```java
> @SaCheckRole("admin")     // 当前子系统的子管理员
> @SaCheckRole("user")      // 当前子系统的 user 角色
> @SaCheckRole("审计员")    // 当前子系统的"审计员"角色
> ```

**"当前客户端 ID"的解析规则**（按优先级从高到低）：

| 优先级 | 来源 | 用途 |
|---|---|---|
| 1 | 请求头 `X-Client-Id` | 网关 / 业务侧动态覆盖（如超级管理员临时管理其他客户端） |
| 2 | 请求参数 `clientId` | 接口级动态指定（按调用上下文切目标） |
| 3 | **`sa-token.sso-client.client`** | **SDK 必备配置，自动复用，无需新增配置项** |
| 4 | `loginUser.clientKey` | 兜底 |

第 3 项是默认值——SDK 启动时 `sa-token.sso-client.client` 是必须配置的（缺失则 SSO 无法工作），
直接复用其值即可，无需再新增 `current-client-id` 配置项。

**动态覆盖示例**（仅在需要时使用，普通业务接口无需关心）：

```http
# 网关透传，覆盖默认的 sso-client.client
X-Client-Id: d5763d20f1c20f6b7c3f8366e2d23502

# 或接口级传参
GET /xxx?clientId=d5763d20f1c20f6b7c3f8366e2d23502
```

**剪裁语义**：
- 全局角色 `superadmin` / `admin` 原样透传；
- 当前客户端的角色 `{currentClientId}-{roleKey}` 同时暴露**原始 key + 剥离前缀的裸名**（兼容 `@SaCheckRole("{clientId}-admin")` 注解写法）；
- **其他客户端的角色完全不暴露**——避免 SSO session 跨子系统复用导致的越权；
- `superadmin` 用户自动额外获得 `admin`，保证任意子系统内 `@SaCheckRole("admin")` 通过；
- **未配置 / 未登录时**仅暴露全局角色（`superadmin` / `admin`），不做剥离——此时业务侧不能用裸角色名做跨客户端注解，需显式指定完整 `roleKey`。

**配套辅助方法**：

```java
// 当前用户是否为当前子系统的子管理员（O(1)）
boolean admin = CloudPlatformHelper.isClientAdmin();

// 当前用户是否为指定 clientId 的子管理员（不依赖当前请求上下文）
boolean admin = CloudPlatformHelper.isClientAdmin("d5763d20f1c20f6b7c3f8366e2d23502");
```

### 4.7、权限自动同步

服务端（认证中心）在**用户、角色、菜单、部门、岗位、租户套餐**等数据发生变更后，
会写入带"变更时间戳"的标记。SDK 会自动感知并重载登录用户，**业务方无需编写任何代码**。

**工作方式**：SDK 在鉴权过滤器中按节流周期检查服务端标记：

| 情况 | 行为 |
| --- | --- |
| 未到检查周期 | 零开销，不产生任何远程调用 |
| 到达检查周期且无变更 | 一次远程查询，返回"无变更" |
| 到达检查周期且有变更 | 自动调用 `refreshLoginUser()` 并触发 `onUserInfoChanged` 监听器 |
| 服务端登录态已失效（被禁用 / 删除 / 强退） | 返回 401，SDK **自动本地登出**并引导重新登录 |

> 变更标记采用"时间戳 + TTL 120 秒、服务端只写不删"的设计，
> 客户端按时间戳去重，因此**刷新失败不会丢通知**，**全网广播也不会引发刷新风暴**。

> **为什么不用定时任务**：sa-token 的登录态是"每请求"的，后台定时任务线程没有
> SaTokenContext，`StpUtil.isLogin()` 会直接抛异常并导致定时任务被永久取消。

**配置**（`application.yml`）：

```yaml
sa-token:
  permission-sync:
    # 是否启用自动同步，默认 true；
    # 设为 false 后需业务方在合适时机手动调用 checkPermissionChange()
    enabled: true
```

> 检查间隔（秒，默认 20、最小 5）与「权限相关档」的缓存 TTL 共用同一个值，
> 统一由 `sa-token.cache.ttl-seconds` 控制（档位划分见 5.1）。

**手动触发**（在删除、审批等关键操作前强制拉取最新权限）：

```java
// 忽略节流，立即检查一次
boolean changed = CloudPlatformHelper.checkPermissionChange();
```

### 4.8、字典信息

字典数据由认证中心（system 模块）统一维护，SDK 通过 SSO 消息向认证中心拉取，并按 TTL 缓存结果。

```java
// 获取全部字典类型（前端一次性拉取后可自行缓存）
List<CloudDictTypeVo> allTypes = CloudPlatformHelper.getDictTypeAll();

// 根据字典类型ID获取字典类型
CloudDictTypeVo type = CloudPlatformHelper.getDictTypeById(1L);

// 根据字典类型编码获取字典类型
CloudDictTypeVo type = CloudPlatformHelper.getDictTypeByType("sys_yes_no");

// 根据字典数据ID获取字典数据
CloudDictDataVo data = CloudPlatformHelper.getDictDataById(100L);

// 根据字典类型编码获取字典数据集合（下拉框 / 单选组常用）
List<CloudDictDataVo> items = CloudPlatformHelper.getDictDataByType("sys_user_sex");
```

> 字典属于「参考数据档」，内置 TTL 300 秒，可用 `sa-token.cache.reference-ttl-seconds`
> 整档调整（档位划分见 5.1）。字典变更后需等待 TTL 自然过期才会生效（无主动刷新接口）。

对应的服务端处理器（认证中心 `SsoServerController`）消息类型：

| 客户端方法 | SSO 消息类型 | 服务端数据来源 |
|---|---|---|
| `getDictTypeAll()` | `getDictTypeAll` | `RemoteDictService.selectDictTypeAll()` |
| `getDictTypeById(dictId)` | `getDictTypeById` | `RemoteDictService.selectDictTypeById(dictId)` |
| `getDictTypeByType(dictType)` | `getDictTypeByType` | `RemoteDictService.selectDictTypeByType(dictType)` |
| `getDictDataById(dictCode)` | `getDictDataById` | `RemoteDictService.selectDictDataById(dictCode)` |
| `getDictDataByType(dictType)` | `getDictDataByType` | `RemoteDictService.selectDictDataByType(dictType)` |

### 4.9、参数配置信息

参数配置（参数设置 `sys_config`）由认证中心（system 模块）统一维护，SDK 通过 SSO 消息向认证中心拉取，并按 TTL 缓存结果。

```java
// 获取当前租户下的全部参数配置（前端一次性拉取后可自行缓存）
List<CloudConfigVo> all = CloudPlatformHelper.getConfigAll();

// 根据参数ID获取参数配置
CloudConfigVo config = CloudPlatformHelper.getConfigById(1L);

// 根据参数键名获取参数配置
CloudConfigVo config = CloudPlatformHelper.getConfigByKey("sys.user.initPassword");

// 根据参数键名获取参数键值（只需单值场景推荐，无需自行解析 VO）
String value = CloudPlatformHelper.getConfigValueByKey("sys.account.registerUser");
```

> 参数配置属于「参考数据档」，内置 TTL 300 秒，可用 `sa-token.cache.reference-ttl-seconds`
> 整档调整（档位划分见 5.1）。参数变更后需等待 TTL 自然过期才会生效（无主动刷新接口）。

> **租户维度**：`sys_config` 为租户隔离表，SDK 自动携带当前登录用户的租户id
> （`CloudPlatformHelper.getTenantId()`）作为查询维度，缓存 key 同样带租户前缀，不会跨租户串号。
> 若客户端未上报租户id，服务端回退到默认租户 `000000`。

对应的服务端处理器（认证中心 `SsoServerController`）消息类型：

| 客户端方法 | SSO 消息类型 | 服务端数据来源 |
|---|---|---|
| `getConfigAll()` | `getConfigAll` | `RemoteConfigService.selectConfigAll(tenantId)` |
| `getConfigById(configId)` | `getConfigById` | `RemoteConfigService.selectConfigById(tenantId, configId)` |
| `getConfigByKey(configKey)` | `getConfigByKey` | `RemoteConfigService.selectConfigInfoByKey(tenantId, configKey)` |
| `getConfigValueByKey(configKey)` | `getConfigValueByKey` | `RemoteConfigService.selectConfigInfoByKey(tenantId, configKey)` → 取 `configValue` |

### 4.10、子系统操作日志写入（复用 sys_oper_log 表）

子系统通过 SDK 单条入口上报操作日志，由授权中心统一写入**已有的操作日志表
`sys_oper_log`**（不新建表），与后台管理操作审计同表同界面查询。

**推荐接入方式**：子系统自有框架的日志注解（如 `@Log`）切面在环绕通知中组装
`CloudOperLogVo`（method / operParam / jsonResult / costTime 等审计明细），
结束时调用 SDK 入口单条提交；SDK 负责异步批量推送与防写入放大。

```java
// 子系统日志注解切面（环绕通知）中的典型用法：
@Around("@annotation(log)")
public Object around(ProceedingJoinPoint point, Log log) throws Throwable {
    long start = System.currentTimeMillis();
    Object result;
    CloudBusinessStatus status = CloudBusinessStatus.SUCCESS; // SDK 枚举，与服务端编码一致
    String errorMsg = null;
    try {
        result = point.proceed();
    } catch (Throwable e) {
        status = CloudBusinessStatus.FAIL;
        errorMsg = e.getMessage();
        throw e;
    } finally {
        CloudOperLogVo vo = new CloudOperLogVo();
        vo.setTitle(log.title());
        vo.setBusinessType(CloudBusinessType.of(log.businessType())); // 业务类型枚举
        vo.setMethod(point.getSignature().toLongString());
        vo.setRequestMethod(CloudRequestMethod.of(request.getMethod())); // 请求方式枚举
        vo.setOperatorType(CloudOperatorType.MANAGE);
        vo.setOperUrl(request.getRequestURI());
        vo.setOperParam(JsonUtils.toJsonString(point.getArgs()));
        vo.setJsonResult(JsonUtils.toJsonString(result));
        vo.setStatus(status);
        vo.setErrorMsg(errorMsg);
        vo.setCostTime(System.currentTimeMillis() - start);
        CloudPlatformHelper.writeOperLog(vo); // operName/租户/客户端/IP/时间 自动补齐
    }
    return result;
}
```

**类型安全枚举**（`io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums` 包，编码与服务端一致）：

| 枚举 | 用途 | 未知值兜底 |
|---|---|---|
| `CloudBusinessType` | 业务类型（INSERT / UPDATE / DELETE / EXPORT…） | 归 OTHER |
| `CloudOperatorType` | 操作类别（MANAGE / MOBILE / OTHER） | 归 MANAGE |
| `CloudBusinessStatus` | 操作状态（SUCCESS / FAIL） | 归 SUCCESS |
| `CloudRequestMethod` | 请求方式（GET / POST / PUT / DELETE…） | 返回 null，可用 String setter 透传自定义动词 |

VO 字段本体保持 `Integer` / `String`（序列化按数字码 / 字符串流转），枚举 setter 是类型安全入口：
`vo.setBusinessType(CloudBusinessType.EXPORT)` 等价于 `vo.setBusinessType(5)`。

**防大量写入设计**（多系统接入也不会打爆授权中心）：

| 层 | 机制 | 效果 |
|---|---|---|
| 调用线程 | `writeOperLog` 仅有界队列一次 offer，无网络无阻塞，异常全吞 | 业务零开销、零风险 |
| 单条语义 | 每条日志独立成行不聚合，审计明细完整保留 | 操作可追溯 |
| 有界队列 | 容量上限（默认 10000），满则丢弃新日志并限频 warn | 不 OOM、不反压业务 |
| 批量推送 | 后台单线程每 5 秒整批推送，单批上限 500 条 | 网络往返与调用量无关 |
| 失败降级 | 推送失败重试 1 次，仍失败丢弃；停机时 shutdown hook 尽力刷盘 | 允许丢失、不重试风暴 |
| 服务端兜底 | 单批条数上限 1000 + 异步落库 | 防御性截断 |

**配置**（均有默认值，不配置即启用）：

```yaml
sa-token:
  oper-log:
    enabled: true            # 总开关，false 时 record 直接空操作
    flush-interval-ms: 5000  # 刷盘周期（毫秒），最小 1000
    queue-capacity: 10000    # 待推送队列容量（超出丢弃新日志）
    max-batch-size: 500      # 单次推送条数上限
```

### 4.11、免登登录 / 注册 / 验证码（v1.3.3，走 pushS 总线）

适用于小程序 / Android 等**无法跳转认证中心、需自定义登录注册界面**的客户端。全部通过 sa-token **pushS 消息总线**调用认证中心：自动验签、免登录、**无需 RSA 公钥**、**不受认证中心 `@ApiEncrypt` 影响**；登录成功后以一次性 ticket 桥接本地登录，返回**子系统本地 token**（复用 `doLoginByTicket` 内核）。

```java
// 图形验证码（免登）
CloudCaptchaVo captcha = CloudPlatformHelper.getCaptcha();

// 发送短信验证码
CloudPlatformHelper.sendSmsCode(sendSmsBo);            // 用户名+密码维度
CloudPlatformHelper.sendSmsCodeByPhone(byPhoneBo);     // 手机号登录（号码需已注册）
CloudPlatformHelper.sendSmsCodeForRegister(forRegBo);  // 注册场景（号码尚未注册）
CloudPlatformHelper.sendSmsCodeByReset(byResetBo);     // 重置密码

// 注册（单端点 /sso/register，图形码与短信码共用 code 字段）
CloudPlatformHelper.register(registerBo);              // CloudRegisterBo（通用超集）
// 类型化写法（编译期避免字段混用，底层同为服务端 register）
CloudPlatformHelper.registerByCaptcha(captchaRegBo);   // CloudRegisterByCaptchaBo：code(图形码) + uuid
CloudPlatformHelper.registerBySms(smsRegBo);           // CloudRegisterBySmsBo：phonenumber + code(短信码，非 smsCode)

// 忘记密码 / 重置密码（先 sendSmsCodeByPhone 收码，再提交改密）
CloudPlatformHelper.resetPassword(resetBo);            // CloudResetPasswordBo

// 登录：返回 CloudLoginVo（含子系统本地 token）
CloudLoginVo byPwd    = CloudPlatformHelper.loginByPassword(loginBo);      // CloudLoginBo
CloudLoginVo byPwdNc  = CloudPlatformHelper.loginByPasswordNoCaptcha(noCapLoginBo); // CloudLoginNoCaptchaBo（仅 username/password，无 code/uuid）
CloudLoginVo byPhone  = CloudPlatformHelper.loginByPhone(phoneLoginBo);    // CloudPhoneLoginBo
CloudLoginVo byWechat = CloudPlatformHelper.loginByWechat(wechatLoginBo);  // CloudWechatLoginBo（小程序 xcxCode，走 type=login、grantType=xcx）
```

**请求实体**（`io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean`，字段与认证中心 Body 类对齐）：

| 方法 | 入参实体 | 前端需传字段 |
|---|---|---|
| `getCaptcha()` | 无 | — |
| `sendSmsCode` | `CloudSendSmsBo` | username、password |
| `sendSmsCodeByPhone` | `CloudSendSmsByPhoneBo` | phonenumber（号码已注册，登录用） |
| `sendSmsCodeForRegister` | `CloudSendSmsForRegisterBo` | phonenumber（号码尚未注册，配合 register） |
| `sendSmsCodeByReset` | `CloudSendSmsByResetBo` | username、oldPassword、newPassword |
| `resetPassword` | `CloudResetPasswordBo` | phonenumber、smsCode、newPassword |
| `register` | `CloudRegisterBo` | username、password（图形码再加 code/uuid；短信码再加 phonenumber/code） |
| `registerByCaptcha` | `CloudRegisterByCaptchaBo` | username、password、code(图形码)、uuid |
| `registerBySms` | `CloudRegisterBySmsBo` | username、password、phonenumber、code(短信码) |
| `loginByPassword` | `CloudLoginBo` | username、password（开图形码再加 code、uuid） |
| `loginByPasswordNoCaptcha` | `CloudLoginNoCaptchaBo` | username、password（**免图形验证码**，实体不含 code/uuid；走服务端独立 `loginByPasswordNoCaptcha` handler） |
| `loginByPhone` | `CloudPhoneLoginBo` | phonenumber、smsCode |
| `loginByWechat` | `CloudWechatLoginBo` | xcxCode（小程序临时凭证，非 code；appid 可选）——内部走 type=`login`+`grantType=xcx` |

> **`clientId` 不在实体里、`tenantId` 可不填**：SDK 在 `CloudPlatformHelper.wrapBody` 统一兜底——`clientId` 自动取 `sa-token.sso-client.client`，`tenantId` 默认 `000000`，`grantType` 有默认值；业务方显式传值时以传值为准。自 v1.4.0 起，注册接口额外支持 `roleKeys` 配置兜底（见 [4.13 注册自动绑定角色](#413注册自动绑定角色v140)）。

> **服务端配套**：认证中心需在 `SsoServerController` 注册 `login/loginByPhone/register/sendSmsCode*（含 sendSmsCodeForRegister）/resetPassword/updatePassword/refreshToken/captcha` 免登与登录态 handler，并把短信 `@RateLimiter` 改为「发送成功才锁」的 Redis 守卫（`SmsSendGuard`）。**微信小程序登录不单独设 handler**：SDK 的 `loginByWechat` 复用通用 `login` handler（`grantType=xcx` → 服务端 `XcxAuthStrategy`）。截至今日服务端已落地 `login/loginByPhone/register/sendSmsCode/sendSmsCodeByPhone/sendSmsCodeByReset/sendSmsCodeForRegister/resetPassword/updatePassword/refreshToken/captcha`；**`loginByPasswordNoCaptcha`（免图形验证码密码登录，v1.3.4）尚需服务端新增 handler 并改 `PasswordLoginBody`/`PasswordAuthStrategy`**（见交接文档 §4.3/§4.4）。详见 `doc/认证中心-服务端免登改造交接.md`。

### 4.12、修改密码 / 续期（v1.3.3，登录态，走 pushS 总线）

适用于**已登录用户**的账户操作：修改密码（凭旧密码）、token 续期。同样走 pushS 总线，但总线不携带终端用户本地 token，SDK 会把当前 `loginId` 作为独立消息字段上报（因消息由持 secret-key 的客户端签名，`loginId` 可信），服务端据此定位用户，前端无法伪造身份。

```java
// 修改密码（登录态）：凭旧密码改新密码
CloudPlatformHelper.updatePassword(updateBo);          // CloudUpdatePasswordBo：oldPassword、newPassword

// 刷新令牌 / 续期（登录态）：校验会话存活后重新签票桥接本地登录，返回新的 CloudLoginVo
CloudLoginVo refreshed = CloudPlatformHelper.refreshToken();
```

> 未登录调用会抛出与其他需登录接口一致的 `NotLoginException`（code 11011）。HTTP 入口为 `POST /authCenter/updatePassword`、`POST /authCenter/refreshToken`（属 `CloudAuthCenterController`，由全局鉴权过滤器要求登录）。

### 4.13、注册自动绑定角色（v1.4.0）

**背景**：用户自助注册后没有任何角色/菜单权限，PC 端看不到菜单，必须等管理员手动配置。
v1.4.0 提供客户端配置项 `sa-token.register-role-keys`：配置角色标识后，SDK 会把标识随 `register`
总线消息上报认证中心，服务端在新用户**尚未绑定任何角色**时按标识自动完成 `sys_user_role` 绑定。

**配置**（`application.yml` / Nacos，支持热更新，无需重启）：

```yaml
sa-token:
  # 注册后自动绑定的角色标识，可配多个；不配置（无值）则不发送、行为完全不变
  # 推荐填短标识（如 car-user）：服务端自动用本客户端 clientId 补全为 {clientId}-car-user 后匹配
  # role_key（与库里存储规则一致，参见 4.6.1）；填完整格式（如 a0ba2cb2...-admin）也可直接命中
  register-role-keys:
    - car-user
```

**生效规则**：

| 规则 | 说明 |
|---|---|
| 无值不用管 | 未配置时注册 body 不含 `roleKeys` 字段，全链路行为与旧版完全一致（向后兼容） |
| 仅未绑定时才绑 | 服务端先查 `sys_user_role`，用户已有角色时直接跳过，**不覆盖管理员已配置的角色** |
| 显式传值优先 | 业务方在 `CloudRegisterBo#roleKeys` 显式传值时以传值为准，配置只做兜底 |
| 生效范围 | `CloudPlatformHelper.register / registerByCaptcha / registerBySms` 及对应 `/sso/register*` HTTP 端点 |
| 未命中静默跳过 | 标识查不到启用角色时仅记日志，不影响注册成功 |
| 防提权 | 系统超级管理员角色禁止经注册通道自动绑定；⚠️ 配 `admin` 命中的是 `{clientId}-admin` **客户端管理员角色**，即注册用户直接成为本客户端管理员，请确认是期望权限 |

**服务端依赖**：需认证中心同步完成 `register` 链路的 `roleKeys` 透传与绑定改造（服务端项目与业务方无关，
详见 `doc/认证中心-服务端改造-注册自动绑定角色.md`）；服务端未升级时本配置不报错、仅不生效。

```java
// 业务方需按次指定角色（不等全局配置）时，显式传 roleKeys 即可，优先于配置值：
CloudRegisterBo bo = new CloudRegisterBo();
bo.setUsername("zhangsan");
bo.setPassword("******");
bo.setRoleKeys(List.of("car-user"));   // 不填则由 SDK 自动注入 sa-token.register-role-keys
CloudPlatformHelper.register(bo);
```

---

## 5、缓存与性能

### 5.1、远程调用缓存架构

`CloudPlatformHelper` 的**远程读取接口**（ticket 换取用户信息除外）都按 TTL 在 Redis 中缓存结果，
TTL 期间**零远程调用**。

**缓存键与失效时机**：

| 接口 | 缓存键 | 失效时机 | TTL（内置默认） |
|---|---|---|---|
| `getUserInfo()` | loginId | `refreshLoginUser()` / 登出 / TTL 到期 | 20 秒 |
| `getRouters()` | loginId | `refreshLoginUser()` / 登出 / TTL 到期 | 20 秒 |
| `isRemoteLogin()` | loginId | `refreshLoginUser()` / 登出 / TTL 到期 | 20 秒 |
| `getDeptNameByIds()` / `getDeptNameById()` | deptIds 集合 | TTL 到期 | 300 秒 |
| `getDeptCodeByIds()` / `getDeptCodeById()` | deptIds 集合 | TTL 到期 | 300 秒 |
| `getDeptChildListById()` | deptId | TTL 到期 | 300 秒 |
| `getUserInfoById()` | userId | TTL 到期 | 60 秒 |
| `getUserListByIds()` | userIds 集合 | TTL 到期 | 60 秒 |
| `getDictTypeAll()` | 固定 ALL | TTL 到期 | 300 秒 |
| `getDictTypeById()` | dictId | TTL 到期 | 300 秒 |
| `getDictTypeByType()` | dictType | TTL 到期 | 300 秒 |
| `getDictDataById()` | dictCode | TTL 到期 | 300 秒 |
| `getDictDataByType()` | dictType | TTL 到期 | 300 秒 |
| `getConfigAll()` | 租户id + 固定 ALL | TTL 到期 | 300 秒 |
| `getConfigById()` | 租户id + configId | TTL 到期 | 300 秒 |
| `getConfigByKey()` / `getConfigValueByKey()` | 租户id + configKey | TTL 到期 | 300 秒 |

> TTL 列是各档位的**内置默认值**；按下方的档位键调整一次，整档接口统一生效。

**架构特点**：

| 维度 | 说明 |
|---|---|
| 存储位置 | 共享 Redis key（所有用户共享一份数据） |
| 写开销 | O(1)（不触发 SaSession 全量序列化） |
| 失效时机 | Redis TTL 自动到期（权限相关档可被 `refreshLoginUser()` / 登出提前失效） |
| 跨用户数据 | 单份（如 `getDeptNameById(123)` 在所有用户间共享） |
| 缓存击穿防护 | JVM 内 per-key ReentrantLock singleflight（同一时刻只有一个线程穿透到认证中心） |

**配置**（`application.yml`）—— 缓存按**档位**组织，**一个档位 = 一类接口**。
整类调整改档位键；不写即用内置默认值，所以通常只有 `ttl-seconds` 一行是必需的：

```yaml
sa-token:
  cache:
    # 档位一｜权限相关（userInfo / routers / isRemoteLogin）
    # 默认 20 秒，最小 5 秒。与权限变更检查节流共用，不建议放大。
    ttl-seconds: 20
    # 档位二｜参考数据（部门 / 字典 / 参数配置），默认 300 秒
    # reference-ttl-seconds: 300
    # 档位三｜用户信息（getUserInfoById / getUserListByIds），默认 60 秒
    # user-ttl-seconds: 60
  permission-sync:
    enabled: true
```

**三个档位分别管哪些接口**：

| 档位 | 配置键 | 档内接口（apiName） | 内置默认 |
|---|---|---|---|
| 权限相关 | `ttl-seconds` | `userInfo`（`getUserInfo()`）、`routers`（`getRouters()`）、`isRemoteLogin` | 20 秒 |
| 参考数据 | `reference-ttl-seconds` | `getDeptNameById` / `getDeptNameByIds` / `getDeptCodeById` / `getDeptCodeByIds` / `getDeptChildListById` / `getDictTypeAll` / `getDictTypeById` / `getDictTypeByType` / `getDictDataById` / `getDictDataByType` / `getConfigAll` / `getConfigById` / `getConfigByKey` / `getConfigValueByKey` | 300 秒 |
| 用户信息 | `user-ttl-seconds` | `getUserInfoById` / `getUserListByIds` | 60 秒 |

**单接口例外**（仅当某一个接口要偏离所在档位时才写，优先级最高）：

```yaml
sa-token:
  cache:
    # 只把 getDictDataByType 提长到 600 秒，同档其余 11 个接口仍走 300 秒
    getDictDataByType.ttl-seconds: 600
```

解析优先级：`<apiName>.ttl-seconds`（单接口） > 档位键 > 内置默认。

**运行时清空缓存**（不想等 TTL 过期时使用）：

```bash
# 清空整个缓存命名空间 cloudauth:security-client-oauth:cache:*，返回删除的 key 数量
curl -X POST https://<你的服务域名>/authCenter/clearCache -H "Authorization: <token>"
```

等价于 Java 侧调用 `CloudPlatformHelper.clearAllCache()`。后台刚改完部门 / 字典 / 参数配置后可立即调用。
该接口需登录（走 SDK 全局鉴权过滤器），**不影响登录态**（不清除 SaSession 登录用户快照，用户无需重新登录）；
清空后首次读取会触发一次远程调用，并发场景由单飞机制合并为 1 次。

**降级策略**：业务未集成 `StringRedisTemplate`（即未启用 Redis）时，
SDK 自动降级为「不缓存、每次都调用 loader」，业务可用性不受影响，启动日志会有降级提示。

**远程调用方法分类一览**：

| 类别 | 方法 | 备注 |
|---|---|---|
| **按 TTL 缓存（权限相关档，20s）** | `getRouters()` / `getUserInfo()` / `isRemoteLogin()` | 另可被 `refreshLoginUser()` / 登出主动失效 |
| **按 TTL 缓存（参考数据档，300s）** | `getDeptNameById(s)` / `getDeptCodeById(s)` / `getDeptChildListById()` / `getDictTypeAll()` / `getDictTypeById()` / `getDictTypeByType()` / `getDictDataById()` / `getDictDataByType()` | 见上方缓存键表 |
| **按 TTL 缓存（参考数据档，300s）** | `getConfigAll()` / `getConfigById()` / `getConfigByKey()` / `getConfigValueByKey()` | 缓存 key 带租户前缀 |
| **按 TTL 缓存（用户信息档，60s）** | `getUserInfoById()` / `getUserListByIds()` | 见上方缓存键表 |
| **不走缓存** | `syncUserInfo()` | 写入侧，写完立即生效无需缓存 |
| **不走缓存** | `getUserInfoByTicket()` / `getUserInfoByTicketWithoutLogin()` | ticket 是一次性的，远端用户信息按 ticket 拉取 |
| **仅本地 O(1)** | `isLogin()` | 仅本地 token 校验 |
| **仅本地 O(1)** | `isTenantAdmin()` / `isSuperAdmin()` / `getUserId()` 等便捷方法 | 全部从 Session 读取 |

### 5.2、本地 Session 缓存（O(1) 权限校验）

- **登录阶段**：`doLoginByTicket(ticket)` 远程拉取一次用户全量数据，写入 SaSession 的 `LOGIN_USER_KEY`，同时写入 token extra 扩展字段。
- **运行阶段**：`CloudAuthPermissionImpl`（`StpInterface`）从 SaSession 内存读取权限码，**零远程调用**，单次校验纳秒级。
- **缓存失效**：
  - Token 过期 / 注销 → 缓存自动失效
  - 管理员修改权限 → 调用 `refreshLoginUser()` 主动失效（见 [4.6](#46权限主动刷新)）
  - 服务端推送变更 → SDK 在下次请求链路自动感知并刷新（见 [4.7](#47权限自动同步)）

> **重要**：`@SaCheckPermission` / `@SaCheckRole` / `@SaCheckLogin` 等注解由 `cn.dev33:sa-token-spring-aop` 的 AOP 拦截器处理。
> sa-token 的其他 starter（`sa-token-spring-boot-starter`、`sa-token-servlet`、`sa-token-core`）**不会传递引入**此包，
> 缺失时注解在运行时会被静默忽略，表现为「打了 debug 也不走 `CloudAuthPermissionImpl.getPermissionList()`」，
> 任何权限码都不校验。本 SDK 已在 pom 中显式声明 `sa-token-spring-aop`，业务方无需额外引入。

### 5.3、CORS 配置

`Access-Control-Allow-Origin` 等响应头从硬编码 `*` 改为可配置：

```yaml
sa-token:
  client:
    cors:
      # 默认 "*"，生产环境建议配置为前端域名，多个用英文逗号分隔
      allowed-origins: https://app.example.com,https://admin.example.com
      # 默认 "POST, GET, OPTIONS, DELETE"
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      # 默认 "*"
      allowed-headers: Authorization,Content-Type,X-Requested-With
      # 默认 "3600"
      max-age: 3600
```

### 5.4、登录白名单配置

SDK 全局鉴权过滤器（`SaServletFilterJakarta` / `SaServletFilter`）默认要求所有 URL 必须登录，
硬编码白名单仅 `"/sso/*"` 和 `"/favicon.ico"`。本版本（v1.2.0 初始开发阶段）将白名单改为**可配置**：

```yaml
sa-token:
  # 全局登录白名单（追加到 SDK 硬默认白名单之后）
  # 路径写法遵循 Spring AntPathMatcher：
  #   /**  多层通配（如 /admin-api/system/sms/callback/**）
  #   /    单层通配
  #   *    单段通配
  permit-paths:
    - /admin-api/system/sms/callback/**       # 短信回调（第三方）
    - /admin-api/openapi/webhook/**           # 开放平台 webhook
    - /admin-api/file/upload/**               # 文件上传
    - /webhook/**                              # 通用 webhook
    - /admin-api/public/health                 # 健康检查
```

#### 白名单合并规则（生效顺序）

| 顺序 | 来源 | 说明 |
|---|---|---|
| 1 | SDK 硬默认 `DEFAULT_PERMIT_PATHS` | `["/sso/*", "/favicon.ico"]`，永远生效，**无法关闭** |
| 2 | `sa-token.permit-paths`（本 SDK 配置） | 不配置时为空，业务方按需添加 |

两组路径**取并集**（去重），按上述顺序追加，最终传给 `SaServletFilter*.addExclude(...)`。

#### 与第三方框架的兼容

**SDK 不耦合任何业务框架**。若业务方使用的 starter（如 `ems-spring-boot-starter-security` 芋道框架）
希望与本 SDK 共享白名单配置项，应在该 starter 内部自行实现 `EnvironmentPostProcessor`，
把自身历史配置项（如芋道的 `ems.security.permit-all-urls`）合并注入到
`sa-token.permit-paths`（参考芋道侧的 `YudaoPermitUrlsMergeProcessor` 实现）。

#### 典型场景

| 场景 | 推荐配置 | 备注 |
|---|---|---|
| 第三方回调（短信、支付、webhook） | `sa-token.permit-paths` | 必须绕过登录，否则无法接收回调 |
| 健康检查 / 探活 | `sa-token.permit-paths: ["/admin-api/public/health"]` | 探活端点不需要登录 |
| 文档 / Swagger | 已内置在 SDK 硬默认白名单 | SDK 自动放行 `/v3/api-docs/**` 等常见文档路径由调用方 starter 提供 |
| 业务模块内部跨服务调用（Feign 透传 Authorization） | 不需要白名单 | Token 透传即可，sa-token 在调用方模块解析 |

#### 安全提示

- 白名单配置应走 **code review**，避免误把敏感接口加入白名单
- 路径匹配使用 `SaRouter` 的 Ant 风格，`/**` 一定要谨慎（影响范围广）

---

### 5.5、子系统登录会话扩展数据（Session Ext，v1.3.5）

SDK 在 sa-token 的**账号级 Session**（与内置 `loginUser` 快照同处一份）之上开放一组 `setSessionExt` / `getSessionExt` 方法，方便子系统挂在**当前登录用户**上的额外业务数据，无需自己操作 `StpUtil.getSession()`。

- **归属隔离**：数据天然按登录用户隔离，无需手拼 `loginId`；
- **随登录态存活**：登出 / token 失效即自动清理，无需手动回收；
- **适用**：用户维度、量小、生命周期跟随登录的标志位 / 短数据；
- **不适用**：跨用户共享的数据（会各存一份）、大对象 / 高频写（每次 `set` 会序列化整个 Session blob）、需要“按 key 独立 TTL”的数据（Session 生命周期 = token，无字段级过期）。

```java
// 写入（value == null 等价删除该 key；未登录抛 NotLoginException）
CloudPlatformHelper.setSessionExt("cart", cartDto);
CloudPlatformHelper.setSessionExt(Map.of("lastDept", deptId, "guideShown", true));  // 批量

// 读取（未登录 / 无值：对象返回 null，列表返回空 List）
CartDto cart = CloudPlatformHelper.getSessionExt("cart", CartDto.class);
List<OrderDto> orders = CloudPlatformHelper.getSessionExtList("recentOrders", OrderDto.class);
Object raw = CloudPlatformHelper.getSessionExt("cart");   // 原始值，不做转换

// 存在性 / 删除
boolean has = CloudPlatformHelper.hasSessionExt("cart");
CloudPlatformHelper.delSessionExt("cart");
```

> 未集成 Redis 时依然可用（Session 由 sa-token 自身存储管理）。写入的对象需可被 sa-token 序列化器保存（基本类型 / `Map` / POJO 皆可），读取统一用 `convertValue` 兑底为强类型。
> **撞名保护**：`refreshLoginUser()` 会用 `tenantId/userId/userName/nickName/deptId/deptName/deptCategory` 及 `loginUser/clientid/browserId` 等内置键维护登录态，所以 `setSessionExt` / `delSessionExt` 的 key 命中这些内置键时会**直接抛 `IllegalArgumentException`**（而非静默覆盖），自定义 key 请避开。

---

### 5.6、登录 / 登出事件监听（v1.3.5）

子系统若要在**用户登录成功 / 登出后**执行自己的业务逻辑（初始化购物车、写登录日志、清理临时数据等），只需提供一个实现 `CloudAuthListener` 的 Spring Bean，**SDK 启动时自动收集注册，无需手动调用任何注册方法**（声明式用法与 sa-token 原生 `SaTokenListener` 一致，但时机与入参更适合业务）。

```java
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudLoginUser;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.listener.CloudAuthListener;
import org.springframework.stereotype.Component;

@Component
public class MyAuthListener implements CloudAuthListener {

    @Override
    public void onLogin(CloudLoginUser user) {
        // 登录成功、且全量用户数据已写入 Session 后触发（可直接读 user.getUserId() 等）
    }

    @Override
    public void onLogout(CloudLoginUser user) {
        // 登出时触发；SDK 已在销毁会话前抓取快照，user 仍能拿到"是谁登出了"
    }
}
```

- **两个方法都是默认空实现**，只覆写你关心的那一个即可。
- **onLogin 时机**：在 `doLoginByTicket` / `loginByPassword` 等所有登录入口完成本地登录、拉取并写入全量用户数据之后（比原生 `doLogin` 事件更晚、数据更全）。
- **onLogout 时机**：覆盖 `logout()` / `logoutByAlone()` / `ssoLogout()` 及权限失效本地登出；SDK 在销毁会话**前**抓取用户快照传入。
- **异常隔离**：单个监听器抛异常不影响登录/登出主流程与其它监听器；但回调运行在请求线程内，**务必轻量**，耗时逻辑自行异步。
- **覆盖边界**：若登录态是被认证中心强制下线、经 `logoutCall` 回调清理的，`onLogout` 不会触发；需要无死角监听所有登出时，另行实现 sa-token 原生 `cn.dev33.satoken.listener.SaTokenListener#doLogout`。
- **非 Spring 场景**：可手动调用 `CloudPlatformHelper.registerAuthListener(listener)` 注册。

---

## 6、客户端项目适配实践记录（ln-ems）

> 以下内容来自 `ln-ems`（SpringBoot3 新框架，原型 `ruoyi-vue-pro`）按 **方式二：完全接入授权中心** 改造的实战记录，供后续接入本 SDK 的项目参考。

### 6.1、改造目标与取舍

业务侧（`ems-module-system-server`、`ems-module-system-api`、`ems-common`）只保留字典、通知、邮件、短信、操作日志、API 日志等**通用基础设施**，鉴权链路（登录、用户/角色/菜单/部门/社交/租户/IP/通知公告）**全部由授权中心接管**。

| 维度 | 改造前（ruoyi-vue-pro） | 改造后（ln-ems） |
|---|---|---|
| 用户/角色/菜单表 | 业务库维护 | **授权中心维护** |
| 登录 | `system.local` 系统本地校验 | 跳转 `/sso/getSsoAuthUrl` → 授权中心统一登录 |
| Token 校验 | `OAuth2TokenApi` 自研 Feign | `StpUtil.checkLogin()` sa-token 本地校验 |
| 权限注解 | `@PreAuthorize("@ss.hasPermission('xx:yy:zz')")` | `@SaCheckPermission("xx:yy:zz")` |
| 鉴权工具 | `SecurityFrameworkUtils` | `LoginContext.getLoginUserId()` / `CloudPlatformHelper.*` |
| 网关鉴权 | 调 `OAuth2TokenApi.checkAccessToken` | 透传 `Authorization`，业务模块内自校验 |
| 异常处理 | `GlobalExceptionHandler.accessDeniedException` | 由 SDK `CloudTokenAuthExceptionHandler` 接管 |

### 6.2、Maven 依赖处理（最容易踩的坑）

> ⚠️ 上游 `cloud-auth-client-oauth` 的 pom 内置的是 **SpringBoot2 版的 `sa-token-spring-boot-starter`**，且声明了 `<optional>true</optional>` —— **不会向下游传递**。
> 而 SpringBoot3 项目用 `javax.servlet` → `jakarta.servlet`，**版本不兼容**直接启动失败，必须显式替换。

```xml
<!-- ems-spring-boot-starter-security/pom.xml -->
<dependency>
    <groupId>io.gitee.chenshaogui.cloudauth</groupId>
    <artifactId>cloud-auth-client-oauth</artifactId>
</dependency>

<!-- 替换为 SpringBoot3 版本，版本号由 SDK 的 dependencyManagement 锁定 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>

<!-- sa-token-spring-aop / sa-token-sso / sa-token-redis-template / sa-token-forest
     均为非 optional，会从 cloud-auth-client-oauth 顶层传递，无需重复声明 -->
```

**POM 注释规范**（推荐写法，避免后续维护时被误删）：

```xml
<!--
    注意：上游 cloud-auth-client-oauth 引入的是 SpringBoot2 版的
    sa-token-spring-boot-starter 且声明为 optional（不向下游传递），
    而本项目使用 SpringBoot3.x，必须显式替换。
    sa-token-spring-aop / sa-token-sso / sa-token-redis-template /
    sa-token-forest 均为非 optional，本 starter 不再重复声明。
-->
<dependency>
    <groupId>io.gitee.chenshaogui.cloudauth</groupId>
    <artifactId>cloud-auth-client-oauth</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>
```

### 6.3、SpringBoot 自动配置清单清理

`spring-boot 3.x` 走 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（**不是** spring.factories）。
原芋道版 `AutoConfiguration.imports` 通常会遗留以下条目，**改造后必须清理**：

| 旧条目 | 处置 | 原因 |
|---|---|---|
| `YudaoWebSecurityConfigurerAdapter` | **删除** | 原 Spring Security 适配类已重写，类已不存在 |
| `YudaoSecurityRpcAutoConfiguration` | **删除** | 原 OAuth2 RPC 配置已重写，类已不存在 |
| `YudaoSecurityAutoConfiguration` | **保留** | 仍提供 `LoginContext` 工具类 |
| `YudaoOperateLogConfiguration` | 保留 | 操作日志模块未动 |
| `YudaoOperateLogRpcAutoConfiguration` | 保留 | 操作日志模块未动 |

> ⚠️ 漏删旧条目会导致 spring boot 启动时 `ClassNotFoundException`，**整服务起不来**。

### 6.4、`ems-spring-boot-starter-web` 反向依赖清理

`GlobalExceptionHandler`（业务全局异常处理）位于此 starter，**不应反向依赖 sa-token**。
原版用 Spring Security 的 `AccessDeniedException`，改造后**移除该 import**。
sa-token 异常（`NotLoginException` / `NotPermissionException` 等）由 SDK 内置的 `CloudTokenAuthExceptionHandler`（`@Order(HIGHEST_PRECEDENCE)`）统一拦截并返回 `SaResult`，**业务侧无需也不应重复实现**。

**`pom.xml` 清理示例**：

```xml
<!-- 删除 -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-core</artifactId>
    <scope>provided</scope>
</dependency>
```

**`GlobalExceptionHandler.java` 清理示例**：

```java
// 删除 import
// import org.springframework.security.access.AccessDeniedException;

// 删除方法
// @ExceptionHandler(value = AccessDeniedException.class)
// public CommonResult<?> accessDeniedExceptionHandler(...) { ... }

// 注释占位（避免后续误改）
/**
 * sa-token 相关异常已由 cloud-auth-client-oauth 提供的
 * CloudTokenAuthExceptionHandler 统一处理
 * （@Order(HIGHEST_PRECEDENCE)，自动通过 CloudTokenClientAutoConfigure 注册），
 * 本 handler 不再重复拦截。
 */
```

### 6.5、`ems-spring-boot-starter-security` 重构要点

| 原芋道内容 | 改造后 |
|---|---|
| `SecurityFrameworkUtils.getLoginUserId()` | `LoginContext.getLoginUserId()` |
| `@ss.hasPermission('xxx')` EL 表达式 | `@SaCheckPermission("xxx")` 字符串字面量 |
| `YudaoWebSecurityConfigurerAdapter`（删） | — |
| `YudaoSecurityRpcAutoConfiguration`（删） | — |
| `TenantSecurityWebFilter` | 仅校验租户上下文，业务鉴权下放到 `@SaCheckLogin` |

**`LoginContext.java` 兼容层**：为了让旧业务代码最小化迁移，业务侧仍然可以保留 `LoginContext` 作为兼容工具类，但内部实现改为读取 sa-token Session：

```java
public class LoginContext {
    public static Long getLoginUserId() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    }
    public static LoginUser getLoginUser() {
        Object obj = StpUtil.getSession().get(LOGIN_USER_KEY);
        return obj == null ? null : (LoginUser) obj;
    }
    // ...
}
```

### 6.6、代码生成器模板更新

`controller.vm` 模板里的鉴权注解必须从 `@PreAuthorize` 改为 `@SaCheckPermission`：

```velocity
## 旧模板
#if ($sceneEnum.scene == 1)
    @PreAuthorize("@ss.hasPermission('${permissionPrefix}:create')")
#end

## 新模板
#if ($sceneEnum.scene == 1)
    @SaCheckPermission("${permissionPrefix}:create")
#end
```

> 注意：`@PreAuthorize` 走 `ExpressionInterpreter` 解析 EL 表达式，而 `@SaCheckPermission` 直接读取字符串字面量，**不要再用 `@ss` Bean**。

### 6.7、网关（`ems-gateway`）改造要点

`TokenAuthenticationFilter` **不再远程调 OAuth2 Token API**，仅做两件事：

1. 移除客户端伪造的 `login-user` Header（防止伪造身份）
2. 透传 `Authorization` 给后端服务（sa-token 在业务模块内解析）

```java
@Component
public class TokenAuthenticationFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 移除伪造的 login-user 头
        ServerWebExchange clean = SecurityFrameworkUtils.removeLoginUser(exchange);
        // 2. 提取并透传 Authorization（sa-token 在业务模块内解析）
        String token = SecurityFrameworkUtils.obtainAuthorization(clean);
        if (StrUtil.isEmpty(token)) return chain.filter(clean);
        return chain.filter(clean);
    }
    @Override public int getOrder() { return -100; }
}
```

> `gateway/util/SecurityFrameworkUtils` **保留**，它仅是 reactive 上下文工具（往 `exchange.getAttributes()` 写 `login-user-id` 用于日志），与 Spring Security 无关。

### 6.8、前端老路径兼容

按 README 3.5 节指引，业务侧可在自身工程保留以下 Controller 用于老前端兼容：

| 老前端路径 | 兜底实现 |
|---|---|
| `/admin-api/system/user/getInfo` | 转发到 `CloudPlatformHelper.getUserInfo()` |
| `/admin-api/system/menu/getRouters` | 转发到 `CloudPlatformHelper.getRouters()` |
| `/admin-api/system/auth/login` | **删除**，改为调 `/sso/getSsoAuthUrl` |
| `/admin-api/system/auth/logout` | **删除**，仅保留 `/sso/logout`（SDK 内置） |

> 强烈建议新前端直接使用 SDK 内置的 `/authCenter/getLoginUserInfo` 和 `/authCenter/getMenuRouters`，不走兼容 Controller，减少中间环节。

### 6.9、数据库与配置变更

业务库需要清理以下鉴权相关表（与 `system` 模块一并删除）：

```
system_user / admin_user_connection
system_role / system_user_role
system_menu / system_role_menu
system_dept / system_user_dept
system_post / system_user_post
system_social_user / system_social_client
system_tenant / system_tenant_package
system_notice
system_oauth2_client
system_oauth2_approval / system_oauth2_access_token
system_oauth2_refresh_token / system_oauth2_code
（system_login_log / system_operate_log / system_dict_type / system_dict_data 保留）
（infra_api_access_log / infra_api_error_log 保留）
```

`nacos` 配置每个环境独立下发：

```yaml
sa-token:
  sso-client:
    client: <申请的应用标识>
    secret-key: <申请的密钥>
    server-url: https://example.com/uaa-gateway/auth
    is-http: true
```

### 6.10、改造踩坑清单（生产环境必看）

| 编号 | 坑 | 解决方案 |
|---|---|---|
| 1 | `@SaCheckPermission` 注解不生效 | 漏引 `sa-token-spring-aop`，调试模式也不报错，只是静默忽略——见 5.2 节 |
| 2 | `StpUtil.getLoginId()` 在 `@Async` / `Quartz` 线程中抛空指针 | sa-token 上下文基于 ThreadLocal，异步场景需用 `SaTokenContext` 或透传 `loginId` |
| 3 | `CommonResult` 与 SDK 返回的 `SaResult` 字段冲突 | 二者 JSON 都是 `code/msg/data`，**字段名一致**，前端 axios 拦截器按 `code === 0` 判断即可 |
| 4 | 启动后 Bean 找不到 `CloudTokenAuthExceptionHandler` | SDK 的 `@ComponentScan` 需要 spring boot 加载 `CloudTokenClientAutoConfigure`，确认 `AutoConfiguration.imports` 已发布到私服 |
| 5 | `@PreAuthorize` 残留 | 全局 `grep "@PreAuthorize" --include="*.java"` 必须为 0，否则仍走 Spring Security 链路 |
| 6 | `infra_api_error_log` / `system_operate_log` 的 userName 关联查询失效 | 原 `@Trans(target = AdminUserDO.class)` 自动翻译注解已移除，用户名由前端从 `/authCenter/getLoginUserInfo` 拉取后回填 |
| 7 | 多租户场景下 `system_operate_log` 切租户失效 | `LoginContext.getTenantId()` 内部从 SaSession 读取，配合 `TenantConstants` 自动注入；不要在 starter-biz-tenant 里再调本地 RPC |
| 8 | 业务侧自建 `SaTokenExceptionHandler` 与 SDK 重复 | **删除业务侧版本**！SDK 已通过 `CloudTokenClientAutoConfigure` 的 `@ComponentScan` 自动注册 |
| 9 | 操作日志 `@OperationLog` 与 `@SaCheckPermission` 顺序 | 二者都走 AOP，建议操作日志切面优先级更低（数字更大），确保权限校验失败时不打日志 |
| 10 | `sa-token.only-login: true` 与"完全接入"混用 | 见 1 节表格，二选一；混用会导致登录态校验逻辑不一致 |
| 11 | `Redis` 未启用但 `sa-token-redis-template` 已引入 | SDK 已声明为非 optional，若项目未配 Redis 需排除：`{ groupId: cn.dev33, artifactId: sa-token-redis-template }` 见 2.1 节 |
| 12 | nacos 多环境共用同一 `secret-key` | 各环境独立下发，**严禁** dev / test / prod 共用密钥 |
| 13 | `LoginLog` / `OperateLog` 切面失效 | `@Around` 切面内部依赖 `LoginContext.getLoginUserId()`，确保 `LoginContext` 优先于日志切面加载 |

### 6.11、回归验证清单（上线前必跑）

- [ ] 浏览器打开系统登录页 → 自动跳 `/sso/getSsoAuthUrl` → 跳转授权中心 → 回调 `clientLoginUrl` → `/sso/doLoginByTicket` → 跳回主页
- [ ] F12 抓包确认 `/sso/getSsoAuthUrl`、`/sso/doLoginByTicket`、`/authCenter/getLoginUserInfo`、`/authCenter/getMenuRouters` 四个端点全部 200
- [ ] 触发任意 `@SaCheckPermission` 接口，未登录返回 401（`SaResult`），登录无权限返回 403（`SaResult`）
- [ ] 管理员后台修改某用户权限 → 该用户**下一次请求**自动加载新权限（无需重新登录）
- [ ] 管理员删除某用户 → 该用户下次请求自动登出（401 → 前端跳登录页）
- [ ] gateway 关掉时直接访问业务接口 → 鉴权仍生效（验证鉴权下放到业务模块）
- [ ] 多个浏览器同时登录同一账号 → 互踢（`is-concurrent: false`）或共存（`is-concurrent: true`），按配置生效
- [ ] 后台调 `POST /authCenter/clearCache` → 字典/部门/参数配置立刻生效
- [ ] quartz 定时任务 / `@Async` 方法内 `StpUtil.getLoginId()` 不抛空指针（手动 `SaTokenContext` 透传）
- [ ] 多租户切换后，`operate_log.tenant_id` 自动更新

---

## 7、v1.2.0：SpringBoot2 / SpringBoot3 双版本自动适配

### 7.1、背景

v1.1.0 内部两个类（`CloudTokenClientConfigure`、`BrowserIdFilter`）直接 import `javax.servlet.*`，
在 SpringBoot3（jakarta.servlet）项目中启动时被 `OnBeanCondition` 反射加载，
抛 `ClassNotFoundException: javax.servlet.Filter`，无法使用。

### 7.2、适配机制（条件装配）

v1.2.0 按 classpath 中实际存在的 servlet API 自动选择实现，**业务方零配置**：

| classpath 环境 | 生效的配置类 | 全局鉴权 Filter | BrowserIdFilter |
|---|---|---|---|
| SpringBoot 2.x（javax.servlet） | `CloudTokenClientConfigure` | sa-token-spring-boot-starter 的 `SaServletFilter`（javax） | `BrowserIdFilter`（javax） |
| SpringBoot 3.x（jakarta.servlet） | `CloudTokenClientConfigureJakarta` | `SaServletFilterJakarta`（SDK 自带） | `BrowserIdFilterJakarta`（SDK 自带） |

原理：两个配置类分别标注

```java
@ConditionalOnClass(name = "javax.servlet.http.HttpServletRequest")   // javax 版
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest") // jakarta 版
```

Spring 的条件评估基于 **ASM 读取字节码元数据**（不会真正加载类），
不满足条件的配置类被直接跳过，因此不会触发对端 servlet API 的类加载异常。

### 7.3、编译期双 starter 类冲突的处理

`sa-token-spring-boot-starter`（javax）与 `sa-token-spring-boot3-starter`（jakarta）
内含**同名类**（`SaServletFilter`、`SpringMVCUtil`、`SaTokenContextServletUtil` 等），
SDK 编译期无法同时引入两者。处理方式：

1. **`SaServletFilterJakarta`**：SDK 自带 jakarta 版全局鉴权 Filter，
   仅依赖 `sa-token-core`（无 servlet 依赖）+ `jakarta.servlet-api`（provided），
   逻辑与官方 javax 版 1:1，`writeResult` 内联实现；
2. **`SaHolder.getRequest().getSource()`**：jakarta 版配置中以此替代
   `SpringMVCUtil.getRequest()`（同名冲突类），运行时由业务方
   sa-token-spring-boot3-starter 提供的上下文返回 jakarta 实现；
3. **`@Bean` 直接注册 Filter**：jakarta 版不用 `FilterRegistrationBean<T>`
   （SDK 编译期其泛型上界绑定 javax Filter，与 jakarta 类型不兼容），
   改为 `@Bean` + `Ordered` 控序，SpringBoot3 自动注册。

### 7.4、业务方接入（v1.2.0+）

**SpringBoot 3.x 项目**（如 ln-ems）：

```xml
<dependency>
    <groupId>io.gitee.chenshaogui.cloudauth</groupId>
    <artifactId>cloud-auth-client-oauth</artifactId>
    <version>${cloud-auth-client-oauth.version}</version>
</dependency>
<!-- SDK 的 sa-token-spring-boot-starter 为 optional 不传递，Boot3 需自行引入 jakarta 版 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
    <version>1.44.0</version>
</dependency>
```

**SpringBoot 2.x 项目**：引入 SDK 后按 v1.1.0 原方式补充 `sa-token-spring-boot-starter` 即可。

> 升级 1.2.0 后，此前为 1.1.0 编写的业务侧 jakarta 桥接层（exclude 配置、
> 桥接配置类、jakarta 版 BrowserIdFilter 副本）应全部删除，恢复 SDK 自动装配。

### 7.5、修复：业务层 servlet-API 解耦（ln-ems 实战反馈）

v1.2.0 已完成配置层条件适配，但 SDK 内部两个**业务类**仍直接静态 import 了 javax 版 `BrowserIdFilter`：

| 文件 | 问题代码（v1.2.0） |
|---|---|
| `CloudPlatformHelper.java` | `import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.BrowserIdFilter;` 第 771 行 `BrowserIdFilter.current()` |
| `RemoteSaSsoClientStrategy.java` | `import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.BrowserIdFilter;` 第 28、47 行 `BrowserIdFilter.HEADER_NAME / .current()` |

虽然在 SpringBoot3 下，javax 版的 `CloudTokenClientConfigure` 被 `@ConditionalOnClass` 跳过不会被 Spring 装配，
但上述业务类**不是配置类**，会随着业务请求链路（如 `/sso/doLoginByTicket`）被 `DispatcherServlet` 加载。
加载 `BrowserIdFilter` 时 JVM 必须解析其父接口 `javax.servlet.Filter`，而 classpath 上只有 `jakarta.servlet.*`，
于是抛 `NoClassDefFoundError: javax/servlet/Filter`（堆栈终点即 `CloudPlatformHelper.doLogin(CloudPlatformHelper.java:771)`）。

#### 修复方案

抽出 **servlet-API 无关的公共门面** `io.gitee.chenshaogui.cloudauth.securitycloud.oauth.core.BrowserIdContext`，
把 ThreadLocal / HEADER_NAME / COOKIE_NAME 全部下沉到门面；两个版本的 Filter 与业务类都只与门面交互：

```
[修改前] 业务 → 静态 import BrowserIdFilter（javax）→ 触发 javax.servlet.Filter 类加载 → 报错
[修改后] 业务 → 静态 import BrowserIdContext（零 servlet 依赖）→ 不触发任何 servlet 类加载 ✓
```

#### 修改清单

| # | 文件 | 操作 |
|---|---|---|
| 1 | `core/BrowserIdContext.java` | **新增**——公共门面，集中维护 ThreadLocal + 常量 |
| 2 | `BrowserIdFilter.java` | **改造**——内部委托 `BrowserIdContext`，保留旧静态 API 以兼容老调用方 |
| 3 | `BrowserIdFilterJakarta.java` | **改造**——同上，与 javax 版共享同一个 ThreadLocal |
| 4 | `utils/CloudPlatformHelper.java` | **修复**——移除 `import BrowserIdFilter`，改用 `BrowserIdContext.current()` |
| 5 | `template/RemoteSaSsoClientStrategy.java` | **修复**——同上 |

> 业务方接入方式**无任何变化**：依旧按 v1.2.0 一节引入依赖即可，无需修改任何业务代码或排除任何类。
> 该修复向后兼容：业务方升级 SDK 后，原有行为完全一致（Filter 已切换到委托门面，但 ThreadLocal 读写语义不变）。

### 7.6、登录白名单可配置

#### 背景与问题

此前全局鉴权 Filter（`SaServletFilterJakarta` / `SaServletFilter`）硬编码：

```java
.addInclude("/**")
.addExclude("/sso/*", "/favicon.ico")  // ← 只有这 2 个白名单
```

业务方想新增不需要登录的接口（如第三方回调、webhook、文件上传、健康检查），
必须**改 SDK 源码并重打包**，无法通过配置解决。

#### 改造内容

| # | 文件 | 改动 |
|---|---|---|
| 1 | `config/CloudProperties.java` | **新增**——`@ConfigurationProperties(prefix = "sa-token")`（与 sa-token 官方 `SaTokenConfig` 共享 namespace，bean 名不同不冲突） |
| 2 | `config/CloudTokenClientConfigureJakarta.java` | 加 `@EnableConfigurationProperties(CloudProperties.class)`，`getSaServletFilter` 注入 `CloudProperties` |
| 3 | `config/CloudTokenClientConfigure.java` | javax 版同样改造 |
| 4 | 两版 Configure 新增 `DEFAULT_PERMIT_PATHS` 常量 | 集中维护 SDK 硬默认白名单 |

#### 白名单合并规则

```
最终白名单 = DEFAULT_PERMIT_PATHS（硬默认）
          ∪ sa-token.permit-paths（SDK 配置项）
```

两组路径**取并集**，按上述顺序追加。

#### 业务方使用

```yaml
sa-token:
  permit-paths:
    - /admin-api/system/sms/callback/**
    - /admin-api/openapi/webhook/**
```

#### bean 冲突分析

`sa-token.permit-paths` 的 `prefix` 与 sa-token 官方 `SaTokenConfig` 都是 `sa-token`，
但 Spring 默认 bean 命名规则基于类全限定名，因此两个 bean 名字不同：

| 类 | bean 名字 |
|---|---|
| `cn.dev33.satoken.config.SaTokenConfig` | `sa-token-cn.dev33.satoken.config.SaTokenConfig` |
| `io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config.CloudProperties` | `sa-token-io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config.CloudProperties` |

两者无冲突。`sa-token.permit-paths` 字段在 `SaTokenConfig` 中不存在，业务方配置时 `SaTokenConfig` 忽略，SDK `CloudProperties` 正常绑定。

#### SDK 边界声明（重要）

**SDK 不耦合任何业务框架**。若调用方 starter（如芋道 `ems-spring-boot-starter-security`）
希望与本 SDK 共享白名单配置项（如芋道历史配置 `ems.security.permit-all-urls`），
**应由该 starter 内部自行实现 `EnvironmentPostProcessor`**，把自身配置项合并注入到
`sa-token.permit-paths`，而非反向污染 SDK 源码。

> **更新（2026-09，ln-ems 反馈）**：ln-ems 中曾实现的
> `YudaoPermitUrlsMergeProcessor`（`ems-spring-boot-starter-security` 模块，
> `META-INF/spring.factories` 自动注册，用于把芋道历史配置 `ems.security.permit-all-urls`
> 合并进 `sa-token.permit-paths`）**已删除**。
> 白名单现已统一走本 SDK 的 `sa-token.permit-paths` 配置项，
> 不再需要额外的 `EnvironmentPostProcessor` 做配置项合并。

#### 兼容性

| 升级前 | 升级后 | 业务方操作 |
|---|---|---|
| 没配 `sa-token.permit-paths` | 白名单 = `["/sso/*", "/favicon.ico"]`，与此前版本完全一致 | 无 |
| 新配 `sa-token.permit-paths` | 生效 | 无 |

**100% 向后兼容**。

> 详细配置说明与场景示例见 [5.4 登录白名单配置](#54登录白名单配置)。

---

### 7.7、SSO 转发链路三个高频问题排查（ln-ems 实战反馈）

网关以 `/admin-api/**`、`/app-api/**` 作为统一对外前缀，而本 SDK 的控制器映射**不带该前缀**
（`CloudTokenSsoClientController` 为 `@RequestMapping("/sso")` 与 `/authCenter`），
两者之间的落差在联调期集中暴露出三个问题。三者现象高度相似（都表现为 401 / 404），
**但根因完全独立**，逐个记录如下。

#### 7.7.1、问题一：浏览器弹出原生 Basic 登录框（Knife4j 网关文档误拦）

**现象**：请求 SSO 入口时，浏览器弹出**系统原生**的「登录以访问此站点」对话框（而非业务登录页），
网关日志中出现：

```
org.springframework.web.server.ResponseStatusException: 401 UNAUTHORIZED
  at com.github.xiaoymin.knife4j.spring.gateway.filter.basic.WebFluxSecurityBasicAuthFilter.writeForbiddenCode(WebFluxSecurityBasicAuthFilter.java:107)
```

**根因**：Knife4j 4.5.0 的 `WebFluxSecurityBasicAuthFilter#filter` 取的是
**含 query string 的完整 URI**：

```java
if (this.enableBasicAuth && this.match(exchange.getRequest().getURI().toString())) {
```

而父类 `AbstractBasicAuthFilter` 内置正则的首尾都是 `.*`，实际等价于 `contains`：

```
.*?/doc\.html.*        .*?/v2/api-docs.*      .*?/v2/api-docs-ext.*
.*?/swagger-resources.*                       .*?/swagger-ui.*
.*?/swagger-resources/configuration/ui.*      .*?/v3/api-docs.*
```

于是只要**请求参数值里出现** `/doc.html`、`/v3/api-docs`、`/swagger-ui` 等字样，
业务请求就会被误判为文档资源，返回 `401 + WWW-Authenticate: Basic realm="Restricted Area"`，
浏览器随即弹出 Basic 登录框。典型触发场景（回调地址恰好指向前端调试页）：

```
/admin-api/sso/getSsoAuthUrl?clientLoginUrl=http://127.0.0.1:18203/pc/enforcement/doc.html
```

**解决方案（覆盖过滤器，不改上游代码）**：利用 Knife4j
`@ConditionalOnMissingBean(WebFluxSecurityBasicAuthFilter.class)` 的装配顺序占位，
新增两个类：

| 类 | 职责 |
|---|---|
| `gateway/filter/basic/PathOnlyBasicAuthFilter` | 重写 `match(String)`，先剥离 query string 只保留 path，再委托父类正则 |
| `gateway/filter/basic/Knife4jBasicAuthConfiguration` | 将上述过滤器注册为 Bean，`knife4j.gateway.basic.*` 配置项与原实现完全一致 |

核心修正代码：

```java
@Override
protected boolean match(String uri) {
    if (uri == null) {
        return false;
    }
    // 仅剥离 query string：HTTP 请求 URI 不含 fragment，无需处理 '#'
    int queryIndex = uri.indexOf('?');
    String pathOnly = queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
    return super.match(pathOnly);
}
```

> **不要删除这两个类**：删除后 Knife4j 的 `@ConditionalOnMissingBean` 会重新创建默认过滤器，
> 参数值含文档路径关键字的业务请求会再次弹出 Basic 登录框。
> 真实文档路径（`/doc.html`、`/admin-api/system/v3/api-docs`）依然需要 Basic 认证，
> **文档保护能力不降级**。
>
> 备选方案是直接关闭 `knife4j.gateway.basic.enable`，但会一并失去聚合文档的访问保护，故未采用。

#### 7.7.2、问题二：404 `No static resource admin-api/sso/getSsoAuthUrl`

**现象**：前端调 SSO 入口返回：

```json
{"code":404,"msg":"No static resource admin-api/sso/getSsoAuthUrl."}
```

**根因（两个坑叠加）**：

**坑 1 —— 同一 route 下多条 `Path=` 是「与」关系。**
Spring Cloud Gateway 中同一 route 的多个 predicate 之间是**与**，多个路径必须写在
**同一个 `Path=` 内用逗号分隔**（逗号才是「或」）。若写成下面这样，则要求 URL 同时命中
两条路径，永远无法匹配，请求最终落到静态资源处理器：

```yaml
# 错误写法：永远不匹配
predicates:
  - Path=/admin-api/sso/**
  - Path=/admin-api/authCenter/**
```

**坑 2 —— SDK 控制器映射不带业务前缀。**
网关若原样转发 `/admin-api/sso/...`，System 服务侧并无该映射，
返回业务码 404 `请求地址不存在`（详见 7.7.3 实测数据）。

**解决方案**：显式路由到登录入口模块，并在 filter 内剥离业务前缀：

```yaml
- id: sso-auth-center
  uri: grayLb://system
  predicates:
    - Path=/admin-api/sso/**,/admin-api/authCenter/**,/app-api/sso/**,/app-api/authCenter/**
  filters:
    # 替换串刻意使用 $1/$2，避免被 Spring 占位符解析误伤
    - RewritePath=/(?:admin-api|app-api)/(sso|authCenter)/(.*), /$1/$2
```

> 该 route 显式指定 `grayLb://system`（登录入口模块），避免被随机的负载均衡
> 转发到 infra / openapi / app 等模块，引发 sa-token session 错乱。

#### 7.7.3、问题三：白名单不能替代路径改写（最容易误判）

**`sa-token.permit-paths` 与网关 `RewritePath` 解决的是两个不同层面的问题**，
只做一半会得到「请求能进来，但仍然是 404」的迷惑现象。

直连 System 服务的对照实验（此时 `permit-paths` 已放行 `/admin-api/sso/**`）：

| 请求路径 | HTTP 状态 | 业务码 |
|---|---|---|
| `/admin-api/sso/getSsoAuthUrl` | 200 | **404** `请求地址不存在:admin-api/sso/getSsoAuthUrl` |
| `/sso/getSsoAuthUrl` | 200 | **400** `请求参数缺失:clientLoginUrl`（已命中控制器） |

注意两行**HTTP 状态码都是 200**，错误信息藏在业务码里，
只看状态码极易误判成「链路已打通」。

| 机制 | 解决的问题 | 失效时的表现 |
|---|---|---|
| `sa-token.permit-paths` | **鉴权放行**（跳过 sa-token 拦截） | 401 |
| 网关 `RewritePath` | **路径映射**（剥掉业务前缀） | 404 |

**两者的配套关系**：

- **网关做 `RewritePath`（推荐）**：System 侧收到的是 `/sso/**`，命中 SDK 内置默认白名单。
  此时在 `permit-paths` 中配置 `/admin-api/sso/**` **不会生效**——路径已在网关被改写，
  业务服务侧从未见过 `admin-api` 这一段。
- **网关不做 `RewritePath`**：只能改由业务侧给控制器补 `/admin-api` 前缀，
  但会干扰 SDK 内部生成地址的逻辑，**不推荐**。

#### 7.7.4、排查顺序（建议固化）

| 序 | 现象 | 对应根因 |
|---|---|---|
| 1 | 浏览器弹**原生** Basic 框 | 7.7.1 · Knife4j 文档过滤器误拦（与 sa-token 无关） |
| 2 | 网关返回 `No static resource xxx` | 7.7.2 坑 1 · predicate 写法错误 |
| 3 | 业务码 404 `请求地址不存在` | 7.7.2 坑 2 · 缺少 `RewritePath` |
| 4 | 401 | 7.7.3 · 白名单未放行，或路径已被改写导致白名单不匹配 |

---

### 7.8、`getSsoAuthUrl` 接口签名变更（fallback 默认登录地址）

#### 变更背景

旧版 `/sso/getSsoAuthUrl` 强制要求 `clientLoginUrl`，否则返回 400。
应用层常遇到「用户没传时也能拿到合法地址」的场景（多入口应用、嵌入式登录按钮等），
由前端自己造一个地址既不规范，又会触发 [7.7.1 Knife4j 文档误拦](#771问题1浏览器弹出原生-basic-登录框knife4j-网关文档误拦)。
本次更新：SDK 与服务端协同提供默认地址兜底。

#### 改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `controller/CloudTokenSsoClientController.java` | `getSsoAuthUrl` 签名：两个参数均改为可选；`clientLoginUrl` 为空时自动调 `CloudPlatformHelper.getDefaultLoginUrl()` 兜底 |
| 2 | `utils/CloudPlatformHelper.java` | `getSsoAuthUrl` 增加 `(clientLoginUrl, back)` 重载；新增 `getDefaultLoginUrl()` |
| 3 | `cloud-auth-platform-auth` `SsoServerController.java` | 在 `configSso` 中注册 `getDefaultLoginUrl` 消息处理器：按 `clientId` 查绑定菜单 → `menu.path` |

#### 接口契约

```http
GET /sso/getSsoAuthUrl?clientLoginUrl=<可选>&back=<可选>
```

| 参数 | 必填 | 兜底策略 |
|---|---|---|
| `clientLoginUrl` | **否** | 为空时 SDK 自动按 `clientId` 调服务端 `getDefaultLoginUrl` 消息处理器，按 clientId 查绑定菜单取 `menu.path` 作为默认地址 |
| `back` | 否 | 空时授权地址尾部出现 `?back=`（sa-token 内部 `addParam` 智能拼接），业务方可忽略 |

#### 关键设计点

- **服务端 handler 不要求登录态**：走 sa-token SSO push message 通道，SDK 客户端在登录前调就能拿到；签 `client` 不签 `user`。
- **向下兼容**：旧调用方式（仅传 `clientLoginUrl`）行为不变；不传新 `back` 参数时透传空串。
- **业务方覆盖同名 controller 时务必按新签名实现**：仅引用旧单参版本时不会编译失败，但 fallback 行为会丢失；保持旧语义的可在覆盖版内不做 fallback。

#### 配套文档

- 前端调用方式与签名表：2.4 节
- Controller 实现位置与覆盖说明：3.4 节
- 工具方法清单：4.1 节

---

## 8、Sa-Token 模式对齐（v1.2.0 起自动生效）

### 8.1、背景：两处「静默失效」引发的故障

认证中心（`cloud-auth-platform`）与接入本 SDK 的业务系统各自持有独立的 `sa-token` 配置。
sa-token 的若干开关在两端**不一致时不会报错、不会启动失败，只会在运行时静默丢弃参数**，
表现为功能不生效或抛出难以定位的业务异常。v1.2.0 将两处关键配置由 SDK 统一对齐。

### 8.2、动态 activeTimeout —— 修复 `NotLoginException: token 已被冻结`

**现象**：ticket 登录成功后，携带 token 访问业务接口或认证中心接口，服务端抛异常：

```
cn.dev33.satoken.exception.NotLoginException: token 已被冻结
	at cn.dev33.satoken.stp.StpLogic.checkActiveTimeout(StpLogic.java:1742)
	at cn.dev33.satoken.stp.StpLogic.checkActiveTimeoutByConfig(StpLogic.java:1720)
	at cn.dev33.satoken.stp.StpLogic.getTokenSession(StpLogic.java:1526)
```

**根因**：sa-token 只在 `isOpenCheckActiveTimeout()` 为 true 时，才会在 `StpUtil.login()` 时
写入 token 的「最后活跃时间」缓存 key（`satoken:last-active-time:<token>`），
该判断条件为 `active-timeout != -1 || dynamic-active-timeout`：

| | `dynamic-active-timeout` | 登录时写 `last-active-time` | 读取时校验冻结 |
|---|---|---|---|
| 认证中心 | `true` | 写 | 校验 |
| 客户端（未对齐前） | 未配置 → `false` | **不写** | 不校验 |

客户端不写、服务端校验 → 服务端 `getTokenLastActiveTime()` 查不到该 key，返回 `-2` →
`isFreeze()` 判定 `-2` 即「已冻结」→ 抛异常。

客户端侧同时会出现被忽略的警告（即参数被静默丢弃的证据）：

```
SA [WARN] -->: 当前全局配置未开启动态 activeTimeout 功能，传入的 activeTimeout 参数将被忽略
```

**修复**：SDK 通过 `CloudSsoEnvironmentPostProcessor` 以最低优先级注入 `sa-token.dynamic-active-timeout=true`。

### 8.3、JWT 模式 —— 修复 token extra 静默失效（browserId 精准注销）

**现象**：启动/登录日志出现警告，且「同浏览器多 tab 一起登出」对客户端侧 token 不生效：

```
SA [WARN] -->: 当前 StpLogic 不支持 extra 扩展参数模式，传入的 extra 参数将被忽略
```

**根因**：token extra 由 `StpLogic.isSupportExtra()` 控制 —— 基类 `StpLogic` 返回 `false`，
仅 JWT 的三种实现（`StpLogicJwtForSimple` / `StpLogicJwtForMixin` / `StpLogicJwtForStateless`）返回 `true`。

认证中心 `SysLoginService.logout()` 按 browserId 精准踢人：

```java
tokens = StpUtil.getTokenValueListByLoginId(loginId);   // 该账号下所有 token（含客户端签发的）
for (String token : tokens) {
    Object tBid = StpUtil.getExtra(token, "browserId"); // 读 token extra
    if (myBrowserId.equals(tBidStr)) {
        StpUtil.logoutByTokenValue(token);
    }
} catch (Exception ignored) { }                         // 非 JWT token 的解析异常被静默吞掉
```

客户端 token 为 `random-32`（32 位随机串），不是 JWT 格式 → `SaJwtUtil.getPayloadsNotCheck()`
解析抛异常 → 被 `catch (Exception ignored)` 吞掉 → **该 token 永远匹配不上、永远踢不掉**。

**修复**：SDK 引入 `sa-token-jwt` 并注册 `StpLogicJwtForSimple`（与认证中心同款）。

> **为什么必须选 Simple 模式**：认证中心用的就是它。Simple 模式是「有状态 JWT」——
> token 本体为 JWT，但账号映射与会话仍存 Redis，因此两端 token 校验都读同一份 Redis 数据。

### 8.4、升级注意事项（重要）

| # | 事项 | 说明 |
|---|---|---|
| 1 | **存量 token 全部失效** | token 从 32 位随机串变为 JWT（200+ 字符），所有在线用户需重新登录，建议低峰期发布 |
| 2 | **`jwt-secret-key` 必须与认证中心一致** | SDK 默认值 `abcdefghijklmnopqrstuvwxyz` 与认证中心一致；认证中心更换密钥后业务方必须同步覆盖，否则 token 无法互认 |
| 3 | **`token-style` 失效** | JWT 模式下 token 由 `SaJwtUtil` 生成，该配置项不再生效 |
| 4 | **`is-share` 恒为 false** | `StpLogicJwtForSimple.isSupportShareToken()` 硬编码返回 false，每次登录创建新 token，不复用旧 token |
| 5 | **业务方自定义 StpLogic 会让位** | SDK 以 `@ConditionalOnMissingBean(StpLogic.class)` 注册；若业务方自行注册了非 JWT 的 StpLogic，SDK 会让位并打印 WARN |

### 8.5、自检与排查

**启动自检**：SDK 内置 `SmartInitializingSingleton` 自检，启动日志会输出当前生效的 StpLogic：

```
[cloud-auth-client-oauth] StpLogic 自检通过，当前生效：cn.dev33.satoken.jwt.StpLogicJwtForSimple
```

若出现以下告警，说明业务方自注册的 StpLogic 覆盖了 SDK 默认值（token extra 又会失效）：

```
[cloud-auth-client-oauth] 当前生效的 StpLogic 为 xxx，非 JWT 模式：登录时的 extra 扩展参数（browserId）将被静默丢弃...
```

处理方式：移除业务方自定义的 `StpLogic` Bean，或将其改为 `StpLogicJwtForSimple`。

**Redis 侧验证**：

```bash
# 1. 确认已写入最后活跃时间 key（值为 "时间戳,activeTimeout"，如 1790670204046,1780）
KEYS satoken:last-active-time:*
GET  satoken:last-active-time:<token>

# 2. 确认 token 本体已是 JWT（三段式、以 eyJ 开头）
KEYS satoken:login:token:*
```

**接口连通性验证**：ticket 登录成功后调用 `/authCenter/getLoginUserInfo`，
不再出现 `token 已被冻结` 即表示两端口径已对齐。