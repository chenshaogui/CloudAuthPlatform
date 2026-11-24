# 统一认证平台客户端sdk 接口文档

> 适用版本：`cloud-auth-client-oauth 1.3.3+`
> 文档基线日期：2026-10-21
> 文档用途：供开发人员对接鉴权平台 SDK 接口时使用

---

## 0. 通用说明

### 0.1 鉴权（登录）说明

- **免登录接口**：可匿名访问，常见于登录前流程和 SSO 回调。
- **需登录接口**：未登录访问会返回 HTTP 401，前端需先跳转到鉴权中心完成登录。
- 所有接口都受 sa-token 登录态保护，前端调用**需登录**接口时**需携带登录态 cookie**（sa-token 默认 token 名称 `satoken`），浏览器会自动带上同域 cookie。

### 0.2 通用响应结构（SaResult）

绝大多数接口统一返回 `SaResult`：

```json
{ "code": 200, "msg": "ok", "data": <业务数据> }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码：`200` 成功，其他值失败 |
| `msg`  | String | 业务消息 |
| `data` | Object | 业务数据（具体结构见各端点定义） |

> ⚠️ **重要约定**：`SaResult` 业务码 `200` 与 HTTP 200 不一定一致。sa-token 默认下业务失败也返回 HTTP 200，由 `code` 字段判断。

### 0.3 常见错误响应

| 场景 | code | msg 示例 |
|------|------|----------|
| 未登录 | 401 | `请先登录` |
| 登录态过期 | 401 | `token 已过期` |
| 参数缺失 | 500 | `<参数名> 不能为空` |
| 远程调用失败 | 500 | `...` |

### 0.4 接口总览（按业务功能分类）

| 业务模块 | 端点数 | 鉴权 |
|----------|--------|------|
| [1. 登录认证](#1-登录认证) | 15 | 1 需 / 14 免 |
| [2. 退出登录](#2-退出登录) | 2 | 免 |
| [3. 当前用户信息](#3-当前用户信息) | 3 | 需 |
| [4. 菜单路由](#4-菜单路由) | 1 | 需 |
| [5. 基础数据 - 区域](#5-基础数据---区域) | 2 | 需 |
| [6. 基础数据 - 公告](#6-基础数据---公告) | 2 | 需 |
| [7. 文件存储](#7-文件存储) | 4 | 需 |
| [8. 后台维护](#8-后台维护) | 1 | 需 |
| [9. SSO 内部协议（仅部署参考）](#9-sso-内部协议仅部署参考前端无需调用) | 3 | 免 |
| **合计** | **33** | — |

### 0.5 典型登录调用流程

```
1) GET /sso/getSsoAuthUrl?back=/dashboard
   → 拿到鉴权中心登录 URL → window.location.href 跳转
2) 鉴权中心登录页 → 用户输入账号密码 → 鉴权中心校验
3) 鉴权中心 302 回跳到 /sso/doLoginByTicket?ticket=xxx&back=/dashboard
   → 后端消费 ticket、本地登录、设置 cookie
4) 业务页面初始化时调用：
   - GET /authCenter/getLoginUserInfo（用户信息、权限）
   - GET /authCenter/getMenuRouters（路由）
   - 缓存到前端 store
```

---

## 1. 登录认证

> 本节 1.1~1.3 为**传统 SSO 跳转登录**（浏览器可跳转认证中心的场景）；
> 1.4~1.13 为 **v1.3.3 免登直连接口**（小程序 / Android 等无法跳转认证中心、需自定义登录注册的客户端），
> 统一经 sa-token **pushS 消息总线**调用认证中心，自动验签、**无需 RSA 公钥**、**不受 `@ApiEncrypt` 影响**。
> 免登接口均为**免登录**，请求体为 `application/json`。

> ⚠️ **`clientId` / `tenantId` 前端一律无需传**：SDK 会兜底注入——`clientId` 自动取 SDK 自身配置的 sa-token `client`，`tenantId` 不填时默认主租户 `000000`，`grantType` 有默认值。实体类里不含 `clientId` 字段，故不会误传。

### 1.0 免登接口清单

| 端点 | 方法 | 用途 | 请求体 | 响应 data |
|------|------|------|------|-----------|
| `/sso/captcha` | GET | 图形验证码 | 无 | [CloudCaptchaVo](#cloudcaptchavo--图形验证码) |
| `/sso/sendSmsCode` | POST | 发短信码（用户名+密码校验） | [CloudSendSmsBo](#cloudsendsmsbo--发短信码入参-用户名密码) | — |
| `/sso/sendSmsCodeByPhone` | POST | 发短信码（手机号登录） | [CloudSendSmsByPhoneBo](#cloudsendsmsbyphonebo--发短信码入参-手机号) | — |
| `/sso/sendSmsCodeForRegister` | POST | 发短信码（注册，号码未注册） | [CloudSendSmsForRegisterBo](#cloudsendsmsforregisterbo--发短信码入参-注册) | — |
| `/sso/sendSmsCodeByReset` | POST | 发短信码（重置密码） | [CloudSendSmsByResetBo](#cloudsendsmsbyresetbo--发短信码入参-重置密码) | — |
| `/sso/resetPassword` | POST | 忘记密码（短信码改密） | [CloudResetPasswordBo](#cloudresetpasswordbo--忘记密码入参) | — |
| `/sso/register` | POST | 用户注册（通用超集） | [CloudRegisterBo](#cloudregisterbo--注册入参) | — |
| `/sso/registerByCaptcha` | POST | 用户注册（图形验证码） | [CloudRegisterByCaptchaBo](#cloudregisterbycaptchabo--注册入参-图形验证码) | — |
| `/sso/registerBySms` | POST | 用户注册（手机号短信码） | [CloudRegisterBySmsBo](#cloudregisterbysmsbo--注册入参-手机号短信) | — |
| `/sso/loginByPassword` | POST | 账号密码登录 | [CloudLoginBo](#cloudloginbo--账号密码登录入参) | [CloudLoginVo](#cloudloginvo--登录验证信息) |
| `/sso/loginByPasswordNoCaptcha` | POST | 账号密码登录（免图形验证码） | [CloudLoginNoCaptchaBo](#cloudloginnocaptchabo--账号密码登录入参免图形验证码) | [CloudLoginVo](#cloudloginvo--登录验证信息) |
| `/sso/loginByPhone` | POST | 手机号+短信码登录 | [CloudPhoneLoginBo](#cloudphoneloginbo--手机号登录入参) | [CloudLoginVo](#cloudloginvo--登录验证信息) |
| `/sso/loginByWechat` | POST | 微信小程序登录 | [CloudWechatLoginBo](#cloudwechatloginbo--微信小程序登录入参) | [CloudLoginVo](#cloudloginvo--登录验证信息) |

> 登录类接口（`loginByPassword` / `loginByPhone`）返回的 `data` 是**子系统本地 token**（ticket 桥接），拿到后前端按普通登录态保存即可，后续接口与 SSO 登录后完全一致。

---

### 1.1 获取鉴权中心登录地址 `/sso/getSsoAuthUrl`

**功能**：拼出 SSO 鉴权中心登录地址（**免登录**，最常用的跳转登录接口）

**请求参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clientLoginUrl` | String | 否 | 客户端登录地址（票据投递地址）；传空时由 SDK 自动按 `clientId` 从服务端拉取默认地址 |
| `back` | String | 否 | 客户端登录成功后再次回跳到哪（用户原本想去的业务页面） |

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | String | SSO 认证中心登录完整 URL（含签名参数） |

**响应示例**：

```json
{ "code": 200, "msg": "ok", "data": "https://auth.example.com/securitySystem/sso/auth?client=xxx&redirect=...&timestamp=...&nonce=...&sign=..." }
```

**前端典型用法**：

```javascript
// 方式 1：拿到 URL 后跳转
const { data } = await api.get('/sso/getSsoAuthUrl?back=' + encodeURIComponent('/dashboard'))
window.location.href = data

// 方式 2：直接页面跳转（最简）
window.location.href = '/sso/getSsoAuthUrl?back=' + encodeURIComponent('/dashboard')
```

---

### 1.2  消费 ticket 完成本地登录 `/sso/doLoginByTicket`

**功能**：消费 ticket 完成本地登录（**免登录**，由鉴权中心 302 回跳触发）

**接口说明**：浏览器被鉴权中心 302 跳转到此 URL（query 中带 `ticket`），后端自动设置登录态 cookie，**前端无需处理 token**。

**请求参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ticket` | String | **是** | 授权码（由鉴权中心生成，登录回跳时携带） |

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | [CloudLoginVo](#cloudloginvo--登录验证信息) | 登录验证信息（实际回传 `accessToken`、`expireIn`、`clientId`；`refreshToken`/`scope` 等为预留字段） |

**响应示例**：

```json
{
  "code": 200, "msg": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expireIn": 7200,
    "clientId": "system"
  }
}
```

---

### 1.3 获取鉴权中心前端地址 `/authCenter/getSsoUrl`

**功能**：获取鉴权中心登录地址（已登录后调用，返回 `client` / `serverUrl` / `authUrl` 三元组）

**请求**：GET `/authCenter/getSsoUrl`，**需登录**，自版本 1.3.0

**请求参数**：无

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | [CloudSsoUrlVo](#cloudssourlvo--鉴权中心-url-配置) | 鉴权中心 URL 配置 |

**响应示例**：

```json
{
  "code": 200, "msg": "ok",
  "data": {
    "client": "system",
    "serverUrl": "https://auth.example.com",
    "authUrl": "https://auth.example.com/securitySystem/sso/auth"
  }
}
```

**典型用法**：

```javascript
const { data } = await api.get('/authCenter/getSsoUrl')
const clientLoginUrl = `${location.origin}/sso/doLoginByTicket`
window.location.href = `${data.authUrl}?client=${data.client}&clientLoginUrl=${encodeURIComponent(clientLoginUrl)}&back=${encodeURIComponent(location.pathname + location.search)}`
```

---

### 1.4 获取图形验证码 `/sso/captcha`

**功能**：获取图形验证码（**免登录**，自 v1.3.3）。走 pushS 总线调认证中心 `captcha`。

**请求**：GET `/sso/captcha`，无参数。

**响应**：`data` 为 [CloudCaptchaVo](#cloudcaptchavo--图形验证码)。

```json
{ "code": 200, "msg": "ok", "data": { "captchaEnabled": true, "uuid": "a1b2c3...", "img": "base64字符串" } }
```

**用法**：把 `uuid` 连同用户输入的 `code` 一起放进 1.8 注册 / 1.9 密码登录 body 回传；`captchaEnabled=false` 时认证中心未开启验证码，可省略 `code`/`uuid`。

---

### 1.5 发送短信验证码（用户名+密码）`/sso/sendSmsCode`

**功能**：登录前按“用户名+密码”校验收发短信验证码（**免登录**，自 v1.3.3）。

**请求体**（[CloudSendSmsBo](#cloudsendsmsbo--发短信码入参-用户名密码)）：`username`、`password`。

**响应**：`{ "code": 200, "msg": "ok" }`（无 `data`）。

---

### 1.6 发送短信验证码（手机号登录）`/sso/sendSmsCodeByPhone`

**功能**：仅按手机号下发登录短信验证码（**免登录**，自 v1.3.3），配合 1.10 手机号登录使用。

**请求体**（[CloudSendSmsByPhoneBo](#cloudsendsmsbyphonebo--发短信码入参-手机号)）：`phonenumber`。

**限流**：同一手机号发送成功后 60 秒冷却，当日总量上限；发送失败不锁定、可立即重试。

---

### 1.7 发送短信验证码（重置密码）`/sso/sendSmsCodeByReset`

**功能**：重置密码场景下发短信验证码（**免登录**，自 v1.3.3）。

**请求体**（[CloudSendSmsByResetBo](#cloudsendsmsbyresetbo--发短信码入参-重置密码)）：`username`、`oldPassword`、`newPassword`。

> 本接口只负责“发短信码”；若为“忘记密码”（仅凭手机号 + 短信码改密），请改用 1.6 下发短信码后调 1.11 提交改密。

---

### 1.8 用户注册 `/sso/register`

**功能**：用户注册（**免登录**，自 v1.3.3）。服务端 `register` 为**单端点、按字段数据驱动**，图形码与短信码共用 `code` 字段。

**请求体**（[CloudRegisterBo](#cloudregisterbo--注册入参)）：`username`、`password`，再按注册方式：
- **图形验证码**：加 `code`（图形码）+ `uuid`（来自 `/sso/captcha`），`phonenumber` 留空。
- **手机号短信**：加 `phonenumber` + `code`（短信码，先调 `/sso/sendSmsCodeForRegister` 收码），**`uuid` 留空属正常**。

**HTTP 端点**（三者等价，均映射服务端 `register`，按前端需要选其一）：
- `POST /sso/register` —— 通用超集 [CloudRegisterBo](#cloudregisterbo--注册入参)（图形/短信共用一个 body，`CloudPlatformHelper.register(...)`）
- `POST /sso/registerByCaptcha` —— 图形码 [CloudRegisterByCaptchaBo](#cloudregisterbycaptchabo--注册入参-图形验证码)：`username`、`password`、`code`、`uuid`（`CloudPlatformHelper.registerByCaptcha(...)`）
- `POST /sso/registerBySms` —— 短信码 [CloudRegisterBySmsBo](#cloudregisterbysmsbo--注册入参-手机号短信)：`username`、`password`、`phonenumber`、`code`（`CloudPlatformHelper.registerBySms(...)`）

**响应**：`{ "code": 200, "msg": "ok" }`。

---

### 1.9 账号密码登录 `/sso/loginByPassword`

**功能**：账号密码登录（**免登录**，自 v1.3.3）。走总线调认证中心 `login`，成功后以一次性 ticket 桥接本地登录，返回**子系统本地 token**。

**请求体**（[CloudLoginBo](#cloudloginbo--账号密码登录入参)）：`username`、`password`；开启图形码时加 `code`、`uuid`。

> 若业务属于“子系统自有风控、无需图形验证码”的密码登录，可改走 [1.14 `/sso/loginByPasswordNoCaptcha`](#114-账号密码登录免图形验证码-ssologinbypasswordnocaptcha)（入参一致，不校图形码）。

**响应**：`data` 为 [CloudLoginVo](#cloudloginvo--登录验证信息)。

```json
{
  "code": 200, "msg": "ok",
  "data": { "accessToken": "eyJ...", "expireIn": 7200, "clientId": "system" }
}
```

---

### 1.10 手机号+短信码登录 `/sso/loginByPhone`

**功能**：手机号 + 短信验证码登录（**免登录**，自 v1.3.3）。同样以 ticket 桥接本地登录。

**请求体**（[CloudPhoneLoginBo](#cloudphoneloginbo--手机号登录入参)）：`phonenumber`、`smsCode`。

**响应**：`data` 为 [CloudLoginVo](#cloudloginvo--登录验证信息)。

---

### 1.11 忘记密码 / 重置密码 `/sso/resetPassword`

**功能**：凭手机号 + 短信验证码提交新密码完成改密（**免登录**，自 v1.3.3）。走总线调认证中心 `resetPassword`。

**典型流程**：先调 1.6 [`/sso/sendSmsCodeByPhone`](#16-发送短信验证码手机号登录-sso-sendSmsCodeByPhone) 用手机号收取短信码 → 再调本接口提交。

**请求体**（[CloudResetPasswordBo](#cloudresetpasswordbo--忘记密码入参)）：`phonenumber`、`smsCode`、`newPassword`。

**响应**：`{ "code": 200, "msg": "ok" }`。

> 短信码与 1.6 下发的是同一个（服务端按 `phonenumber` 校码）；校验通过后改密。若需“知道旧密码”的改密场景，用 1.7 发短信码 + 对应提交接口；已登录用户改密用 [`/authCenter/updatePassword`](#32-修改密码登录态-authcenterupdatepassword)。

---

### 1.12 发送短信验证码（注册）`/sso/sendSmsCodeForRegister`

**功能**：注册场景按手机号下发短信验证码（**免登录**，自 v1.3.3）。

**与 1.6 的区别**：`sendSmsCodeByPhone` 要求手机号**已注册**（登录用）；本接口面向**尚未注册**的手机号，不校验存在性，配合 1.8 注册使用。

**请求体**（[CloudSendSmsForRegisterBo](#cloudsendsmsforregisterbo--发短信码入参-注册)）：`phonenumber`。

**响应**：`{ "code": 200, "msg": "ok" }`。

---

### 1.13 微信小程序登录 `/sso/loginByWechat`

**功能**：微信小程序登录（**免登录**，自 v1.3.3）。小程序 `wx.login` 拿临时凭证，走总线调认证中心**通用 `login` handler**（`grantType=xcx`，由服务端 `IAuthStrategy` 路由到 `XcxAuthStrategy`）：认证中心用 xcxCode 换 openid、匹配/绑定用户，成功后以一次性 ticket 桥接本地登录。

> 本端点与 [`/sso/loginByPassword`](#111-账号密码登录-ssologinbypassword) 内部复用同一个服务端 `login` handler（仅 `grantType` 不同），**服务端无需单独注册 `loginByWechat` handler**。

**请求体**（[CloudWechatLoginBo](#cloudwechatloginbo--微信小程序登录入参)）：`xcxCode`（小程序临时凭证，**注意字段名不是 `code`**；`appid` 可选）。

**响应**：`data` 为 [CloudLoginVo](#cloudloginvo--登录验证信息)。

> 依赖服务端 `XcxAuthStrategy` 已接入微信 code2session（`appSecret`、`getUserInfoByOpenid` 需按环境补完），且调用方 client 的授权类型（`sys_client.grant_type`）需包含 `xcx`；否则调用会失败。

---

### 1.14 账号密码登录（免图形验证码）`/sso/loginByPasswordNoCaptcha`

**功能**：账号密码登录（**免登录**，自 v1.3.4）。与 [1.9 `/sso/loginByPassword`](#19-账号密码登录-ssologinbypassword) 入参完全一致，但走服务端独立的 `loginByPasswordNoCaptcha` handler，**明确不校验图形验证码**；成功后同样以一次性 ticket 桥接本地登录。

**请求体**（[CloudLoginNoCaptchaBo](#cloudloginnocaptchabo--账号密码登录入参免图形验证码)）：`username`、`password`（**无需 `code`、`uuid`**；`clientId`、`grantType`、`tenantId` 由 SDK 自动兜底）。

**响应**：`data` 为 [CloudLoginVo](#cloudloginvo--登录验证信息)。

> **安全说明**：是否跳过验证码由“调用哪个端点”决定，而**非**前端传某个字段，因此 1.9 `/sso/loginByPassword` 的图形验证码无法被构造报文绕过。本端点仅适用于子系统自身已有其他风控（如 IP / 设备维度限流）的场景；是否对外暴露由子系统自行决定。依赖服务端已注册 `loginByPasswordNoCaptcha` handler（见服务端交接文档 §4.3/§4.4）。

---

## 2. 退出登录

### 2.1  全局退出登录 `/sso/logout`

**功能**：全局退出登录（同时退出鉴权中心，踢下线所有已登录应用）

**请求**： `/sso/logout`，**免登录**

**响应**：由 sa-token 框架返回，通常为 302 重定向到鉴权中心

**前端典型用法**：

```javascript
// 用户点击「退出登录」按钮
window.location.href = '/sso/logout'
```

---

### 2.2 单独退出当前应用 `/sso/logoutByAlone`

**功能**：单独退出当前应用（不影响其他已登录应用）

**请求**： `/sso/logoutByAlone`，**免登录**

**请求参数**：无

**响应**：由 sa-token 框架返回

**前端典型用法**：

```javascript
// 用户点击「切换账号」（仅当前应用退出，不影响其他应用）
window.location.href = '/sso/logoutByAlone'
```

---

## 3. 当前用户信息

### 3.1 获取当前登录用户信息 `/authCenter/getLoginUserInfo`

**功能**：获取当前登录用户信息（基本信息 + 权限集合 + 角色集合）

**接口说明**：登录后调用一次，自行缓存。**返回的 `permissions` 集合可用于按钮级别权限控制**（如 `v-permission="'system:user:add'"`）。

**请求**：GET `/authCenter/getLoginUserInfo`，**需登录**

**请求参数**：无

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | [CloudUserInfoVo](#clouduserinfovo--登录用户信息) | 用户信息（含基本信息、权限集合、角色集合） |

**响应示例**：

```json
{
  "code": 200, "msg": "ok",
  "data": {
    "user": {
      "userId": 1, "deptId": 100,
      "userName": "admin", "nickName": "管理员",
      "userType": "sys_user",
      "email": "admin@xxx.com", "phonenumber": "13800000000",
      "sex": "0", "status": "0",
      "createTime": "2025-01-01 00:00:00",
      "desensitizeFlag": 0,
      "belongCode": "110000", "belongName": "北京市",
      "clientId": "system"
    },
    "permissions": ["system:user:list", "system:user:add", "system:user:edit"],
    "roles": ["admin", "common"]
  }
}
```

---

### 3.2 修改密码（登录态）`/authCenter/updatePassword`

**功能**：当前登录用户凭旧密码提交新密码完成改密（**需登录**，自 v1.3.3）。走总线调认证中心 `updatePassword`。

**与免登 resetPassword 的区别**：`/sso/resetPassword` 是“忘记密码”（手机号 + 短信码）；本接口适用于已登录、知道旧密码的场景，SDK 自动注入当前 `loginId`，前端无法伪造。

**请求**：POST `/authCenter/updatePassword`，Content-Type `application/json`，**需登录**。

**请求体**（[CloudUpdatePasswordBo](#cloudupdatepasswordbo--修改密码入参-登录态)）：`oldPassword`、`newPassword`。

**响应**：`{ "code": 200, "msg": "ok" }`。

---

### 3.3 刷新令牌 / 续期（登录态）`/authCenter/refreshToken`

**功能**：走总线请认证中心校验当前会话仍有效后重新签发一次性 ticket，再桥接本地登录，刷新本地 token 时长（**需登录**，自 v1.3.3）。

**请求**：POST `/authCenter/refreshToken`，**需登录**，无请求体。

**响应**：`data` 为 [CloudLoginVo](#cloudloginvo--登录验证信息)（含刷新后的本地 accessToken）。

> 适用于小程序 / Android 长期持有 token 的续期。若认证中心未提供 refreshToken 机制，本接口等效于“以当前会话重新登录一次”；具体语义由服务端 `refreshToken` handler 决定。

---

## 4. 菜单路由

### 4.1 获取当前登录用户的菜单路由树 `/authCenter/getMenuRouters`

**功能**：获取当前登录用户的菜单路由树（远程调用认证中心）

**接口说明**：登录后调用一次，自行缓存路由。返回结果是**树形**结构，**配合 vue-router 的 `addRoute` 动态注册**。

**请求**：GET `/authCenter/getMenuRouters`，**需登录**

**请求参数**：无

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | Array\<[CloudRouterVo](#cloudroutervo--路由信息)\> | 路由树（一级菜单列表，含子菜单） |

**响应示例**：

```json
{
  "code": 200, "msg": "ok",
  "data": [
    {
      "name": "System", "path": "/system",
      "hidden": false, "redirect": "noRedirect",
      "component": "Layout", "query": "", "alwaysShow": false,
      "meta": { "title": "系统管理", "icon": "system", "noCache": false, "link": "", "activeMenu": "" },
      "children": [
        { "name": "User", "path": "user", "hidden": false, "component": "system/user/index", "meta": { "title": "用户管理", "icon": "user" } }
      ]
    }
  ]
}
```

---

## 5. 基础数据 - 区域

适用于前端**下拉框 / 级联选择**等场景（省 / 市 / 区 / 街道），后端带 300s 缓存。

### 5.1 查询行政地区编码列表 `/authCenter/listAreas`

**功能**：查询行政地区编码列表（按可选条件过滤）

**请求**：POST `/authCenter/listAreas`，Content-Type `application/json`，**需登录**，自版本 1.3.0

**请求参数**（Body，可空；不传时传 `{}`）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 否 | 主键 |
| `areaCode` | String | 否 | 地区编码（精确匹配） |
| `areaName` | String | 否 | 区域名称（模糊匹配） |
| `areaLevel` | Integer | 否 | 区域级别：`0`-国家 / `1`-省 / `2`-市 / `3`-区 / `4`-街道 |
| `parentCode` | String | 否 | 父级编码（精确匹配） |
| `compartment` | String | 否 | 区划代码 |

**请求示例**：

```json
{ "areaLevel": 1, "areaName": "省" }
```

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | Array\<[CloudAreaVo](#cloudareavo--行政地区编码)\> | 区域列表 |

**响应示例**：

```json
{
  "code": 200, "msg": "ok",
  "data": [
    { "id": 1, "areaCode": "110000", "areaName": "北京市", "areaLevel": 1, "parentCode": "0", "compartment": "110000", "syncFlag": 0 },
    { "id": 2, "areaCode": "120000", "areaName": "天津市", "areaLevel": 1, "parentCode": "0", "compartment": "120000", "syncFlag": 0 }
  ]
}
```

---

### 5.2 根据区域编码查询单个区域详情 `/authCenter/getAreaByCode`

**功能**：根据区域编码查询单个区域详情

**请求**：GET `/authCenter/getAreaByCode?areaCode=xxx`，**需登录**，自版本 1.3.0

**请求参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `areaCode` | String | **是** | 区域编码，如 `"110000"` |

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | [CloudAreaVo](#cloudareavo--行政地区编码) | 区域详情（未找到时为 `null`） |

---

## 6. 基础数据 - 公告

### 6.1 分页查询通知公告列表 `/authCenter/listNotices`

**功能**：分页查询通知公告列表

**接口说明**：`pageNum` 默认 `1`，`pageSize` 默认 `10`。所有过滤条件均可空。返回分页结构（含 `rows` 数据列表和 `total` 总数）。

**请求**：POST `/authCenter/listNotices`，Content-Type `application/json`，**需登录**，自版本 1.3.0

**请求参数**（Body，可空；不传时传 `{}`）：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `noticeId` | Long | 否 | — | 公告 ID |
| `noticeTitle` | String | 否 | — | 公告标题（模糊匹配） |
| `noticeType` | String | 否 | — | 公告类型：`1`-通知 / `2`-公告 |
| `status` | String | 否 | — | 公告状态：`0`-正常 / `1`-关闭 |
| `createByName` | String | 否 | — | 创建人名称（模糊匹配） |
| `pageNum` | Integer | 否 | `1` | 当前页（从 1 开始） |
| `pageSize` | Integer | 否 | `10` | 每页大小 |

**请求示例**：

```json
{ "noticeType": "1", "status": "0", "pageNum": 1, "pageSize": 10 }
```

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data.rows` | Array\<[CloudNoticeVo](#cloudnoticevo--通知公告)\> | 当前页数据 |
| `data.total` | Long | 总记录数 |

**响应示例**：

```json
{
  "code": 200, "msg": "ok",
  "data": {
    "rows": [
      { "noticeId": 1, "noticeTitle": "系统升级通知", "noticeType": "1", "noticeContent": "系统将于本周日凌晨 2:00-4:00 升级维护...", "status": "0", "remark": "运维通知", "createBy": 1, "createByName": "管理员", "createTime": "2025-01-01 09:00:00" }
    ],
    "total": 1
  }
}
```

---

### 6.2 根据公告 ID 查询公告详情 `/authCenter/getNoticeById`

**功能**：根据公告 ID 查询公告详情

**请求**：GET `/authCenter/getNoticeById?noticeId=xxx`，**需登录**，自版本 1.3.0

**请求参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `noticeId` | Long | **是** | 公告 ID |

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | [CloudNoticeVo](#cloudnoticevo--通知公告) | 公告详情（未找到时为 `null`） |

---

## 7. 文件存储

**约定**：
- 上传时返回的 `ossId` 是字符串，需**保存到业务表**，后续通过 `ossId` 查 URL。
- 私有桶返回的 URL 是**带签名的临时 URL**（默认 120s 过期），公网桶是原始 URL。

### 7.1 上传文件（form-data 单文件） `/authCenter/oss/upload`

**功能**：上传文件（form-data 单文件）

**注意**：文件大小上限受 Tomcat `maxPostSize=2MB` 限制（base64 编码后 1.4 倍膨胀），**单文件内容建议 < 1.4MB**。更大文件请业务方直传自有 OSS。

**请求**：POST `/authCenter/oss/upload`，Content-Type `multipart/form-data`，**需登录**，自版本 1.3.0

**请求参数**（multipart/form-data）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | Binary | **是** | 表单文件，字段名必须为 `file` |

**请求示例**（Element UI / Ant Design Vue）：

```javascript
const formData = new FormData()
formData.append('file', file.raw) // file 是 el-upload 回调对象
const { data } = await axios.post('/authCenter/oss/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
// data = { url, fileName, ossId }，保存 ossId 到业务表
```

**请求示例**（curl）：

```bash
curl -X POST -H "Cookie: satoken=xxx" -F "file=@/path/to/test.png" \
  https://your-domain/authCenter/oss/upload
```

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | [CloudOssUploadVo](#cloudossuploadvo--oss-文件上传响应) | 上传结果（URL / fileName / ossId） |

**响应示例**：

```json
{
  "code": 200, "msg": "ok",
  "data": { "url": "https://your-bucket.oss-cn-beijing.aliyuncs.com/2026/10/10/abc123.png", "fileName": "2026/10/10/abc123.png", "ossId": "100" }
}
```

---

### 7.2 根据 ossId 获取单个文件 URL `/authCenter/oss/getUrlById`

**功能**：根据 ossId 获取单个文件 URL

**接口说明**：按 ossId 在共享远程缓存中查询（参考数据档 300s TTL），命中时直接返回本地缓存。

**请求**：GET `/authCenter/oss/getUrlById?ossId=xxx`，**需登录**，自版本 1.3.0

**请求参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ossId` | Long | **是** | 文件主键 |

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | String | 文件 URL（私有桶为 120s 临时 URL，公网桶为原始 URL） |

---

### 7.3 根据 ossId 串（逗号分隔）批量获取多个文件 URL `/authCenter/oss/getUrlByIds`

**功能**：根据 ossId 串（逗号分隔）批量获取多个文件 URL

**接口说明**：服务端按入参顺序返回（与 `ossIds` 一一对应），未找到时该位为空串。

**请求**：GET `/authCenter/oss/getUrlByIds?ossIds=1,2,3`，**需登录**，自版本 1.3.0

**请求参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ossIds` | String | **是** | 逗号分隔的 ossId 串，如 `"1,2,3"` |

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | String | 逗号分隔的 URL 串（与 `ossIds` 一一对应） |

**前端典型用法**：

```javascript
const { data: urlStr } = await api.get('/authCenter/oss/getUrlByIds?ossIds=1,2,3')
const urls = urlStr.split(',') // ['url1', 'url2', '']
```

---

### 7.4 根据 ossId 串查询文件完整信息列表 `/authCenter/oss/listByIds`

**功能**：根据 ossId 串查询文件完整信息列表（文件名 / 后缀 / 上传人 / 创建时间等）

**接口说明**：返回的 `CloudOssVo` 列表按入参顺序排列（未找到时该位缺失），**适用于需要展示文件明细的场景**（如附件管理列表）。

**请求**：GET `/authCenter/oss/listByIds?ossIds=1,2,3`，**需登录**，自版本 1.3.0

**请求参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ossIds` | String | **是** | 逗号分隔的 ossId 串 |

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | Array\<[CloudOssVo](#cloudossvo--oss-对象存储)\> | 文件明细列表 |

---

## 8. 后台维护

### 8.1 清除 SDK 全部缓存 `/authCenter/clearCache`

**功能**：清除 SDK 全部缓存（后台维护用）

**接口说明**：清空共享远程调用结果缓存（Redis 命名空间 `cloudauth:security-client-oauth:cache:*`），使后续读取立即重新拉取认证中心最新数据。

**典型场景**：后台刚改完部门 / 字典 / 参数配置 / 区域 / 公告 / 文件，希望业务侧马上生效。

**不会清除 SaSession 中的登录用户快照**，用户无需重新登录。

**请求**：POST `/authCenter/clearCache`，**需登录**

**请求参数**：无

**响应**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务码 |
| `data` | Long | 实际删除的 Redis key 数量 |

---

## 9. SSO 内部协议（仅部署参考，前端无需调用）

> ⚠️ **本节端点前端开发**：
> - **不需要调用**
> - **不需要理解内部逻辑**
> - **仅在部署时确认端点可访问**（鉴权中心会回调这些地址，不能删除/拦截）

| 端点 | 调用方 | 前端行为 |
|------|--------|----------|
| [`/sso/login`](#91--ssologin) | SDK 内部 | 无需调用，跳转登录请用 [`/sso/getSsoAuthUrl`](#12--ssogetssoauthurl) |
| [`/sso/logoutCall`](#92--ssologoutcall) | 鉴权中心回调 | **必须实现**否则 SSO 全局注销失败 |
| [`/sso/pushC`](#93--ssopushc) | 鉴权中心回调 | **必须实现**否则消息推送失败 |

### 9.1  `/sso/login`

**功能**：登录地址（SDK 跳转到鉴权中心时使用）

**说明**：方法任意，路径 `/sso/login`，**免登录**。请求参数由 sa-token SSO 标准约定（通常由 `redirect` Query 参数指定登录后回跳地址）。响应由 sa-token 框架返回，通常为 302 重定向到鉴权中心。**推荐使用 [`/sso/getSsoAuthUrl`](#12--ssogetssoauthurl) 替代**。

### 9.2  `/sso/logoutCall`

**功能**：单点注销回调（鉴权中心调用客户端完成注销）

**说明**：方法任意，路径 `/sso/logoutCall`，**免登录**。**仅由鉴权中心回调**，前端不调用。请求参数由 sa-token SSO 标准约定（鉴权中心回调时携带 `ticket` 等）。响应由 sa-token 框架处理。**必须实现**否则 SSO 全局注销失败，**端点必须可访问**（部署时不能删除/拦截）。

### 9.3  `/sso/pushC`

**功能**：接收鉴权中心的消息推送

**说明**：方法任意，路径 `/sso/pushC`，**免登录**。**仅由鉴权中心回调**，前端不调用。请求参数由 sa-token SSO 标准约定（鉴权中心推送时携带 `type` / `data` 等）。响应由 sa-token 框架处理。**必须实现**否则消息推送失败，**端点必须可访问**。

---

## 10. 通用对象定义

### CloudRouterVo — 路由信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 否 | 路由名字（建议与组件名一致） |
| `path` | String | 否 | 路由地址 |
| `hidden` | Boolean | 否 | 是否隐藏路由，当设置 `true` 的时候该路由不会再侧边栏出现 |
| `redirect` | String | 否 | 重定向地址，当设置 `noRedirect` 的时候该路由在面包屑导航中不可被点击 |
| `component` | String | 否 | 组件地址 |
| `query` | String | 否 | 路由参数：如 `{"id": 1, "name": "ry"}`（字符串形式） |
| `alwaysShow` | Boolean | 否 | 当一个路由下面的 children 声明的路由大于 1 个时，自动会变成嵌套的模式（如组件页面） |
| `meta` | [CloudMetaVo](#cloudmetavo--路由显示信息) | 否 | 路由显示信息 |
| `children` | Array\<CloudRouterVo\> | 否 | 子路由（递归结构） |

### CloudMetaVo — 路由显示信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | String | 否 | 设置该路由在侧边栏和面包屑中展示的名字 |
| `icon` | String | 否 | 设置该路由的图标，对应路径 `src/assets/icons/svg` |
| `noCache` | Boolean | 否 | 设置为 `true`，则不会被 `<keep-alive>` 缓存 |
| `link` | String | 否 | 内链地址（`http(s)://` 开头） |
| `activeMenu` | String | 否 | 激活菜单 |

### CloudUserInfoVo — 登录用户信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `user` | [CloudUserVo](#clouduservo--用户信息) | 否 | 用户基本信息 |
| `permissions` | Array\<String\> | 否 | 菜单权限标识集合 |
| `roles` | Array\<String\> | 否 | 角色权限标识集合 |

### CloudUserVo — 用户信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | 否 | 用户 ID |
| `deptId` | Long | 否 | 部门 ID |
| `userName` | String | 否 | 用户账号（登录名） |
| `nickName` | String | 否 | 用户昵称 |
| `userType` | String | 否 | 用户类型（如 `sys_user` 系统用户） |
| `email` | String | 否 | 用户邮箱 |
| `phonenumber` | String | 否 | 手机号码 |
| `sex` | String | 否 | 用户性别：`0`-男 / `1`-女 / `2`-未知 |
| `status` | String | 否 | 帐号状态：`0`-正常 / `1`-停用 |
| `createTime` | Date（String） | 否 | 创建时间（格式 `yyyy-MM-dd HH:mm:ss`） |
| `desensitizeFlag` | Integer | 否 | 数据是否脱敏：`0`-否 / `1`-是 |
| `belongCode` | String | 否 | 归属地编码 |
| `belongName` | String | 否 | 管辖归属地名称 |
| `clientId` | String | 否 | 客户端 ID |

### CloudSsoUrlVo — 鉴权中心 URL 配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `client` | String | 否 | 当前客户端标识 |
| `serverUrl` | String | 否 | 鉴权中心主机地址（不含 path） |
| `authUrl` | String | 否 | 鉴权中心登录完整 URL |

### CloudAreaBo — 行政地区编码查询条件

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 否 | 主键 |
| `areaCode` | String | 否 | 地区编码（精确匹配） |
| `areaName` | String | 否 | 区域名称（模糊匹配） |
| `areaLevel` | Integer | 否 | `0`-国家 / `1`-省 / `2`-市 / `3`-区 / `4`-街道 |
| `parentCode` | String | 否 | 父级编码（精确匹配） |
| `compartment` | String | 否 | 区划代码 |

### CloudAreaVo — 行政地区编码

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 否 | 主键 |
| `areaCode` | String | 否 | 地区编码 |
| `areaName` | String | 否 | 区域名称 |
| `areaLevel` | Integer | 否 | 区域级别 |
| `parentCode` | String | 否 | 父级编码 |
| `compartment` | String | 否 | 区划代码 |
| `syncFlag` | Byte | 否 | 更新标记 |

### CloudNoticeBo — 通知公告查询条件

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `noticeId` | Long | 否 | — | 公告 ID |
| `noticeTitle` | String | 否 | — | 公告标题（模糊匹配） |
| `noticeType` | String | 否 | — | 公告类型：`1`-通知 / `2`-公告 |
| `status` | String | 否 | — | 公告状态：`0`-正常 / `1`-关闭 |
| `createByName` | String | 否 | — | 创建人名称（模糊匹配） |
| `pageNum` | Integer | 否 | `1` | 当前页（从 1 开始） |
| `pageSize` | Integer | 否 | `10` | 每页大小 |

### CloudNoticeVo — 通知公告

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `noticeId` | Long | 否 | 公告 ID |
| `noticeTitle` | String | 否 | 公告标题 |
| `noticeType` | String | 否 | 公告类型：`1`-通知 / `2`-公告 |
| `noticeContent` | String | 否 | 公告内容 |
| `status` | String | 否 | 公告状态：`0`-正常 / `1`-关闭 |
| `remark` | String | 否 | 备注 |
| `createBy` | Long | 否 | 创建者 ID |
| `createByName` | String | 否 | 创建人名称 |
| `createTime` | Date（String） | 否 | 创建时间 |

### CloudNoticePageVo — 通知公告分页结果

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `rows` | Array\<[CloudNoticeVo](#cloudnoticevo--通知公告)\> | 否 | 当前页数据 |
| `total` | Long | 否 | 总记录数 |

### CloudOssUploadVo — OSS 文件上传响应

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | String | 否 | URL 地址 |
| `fileName` | String | 否 | 文件名（不含原始后缀） |
| `ossId` | String | 否 | 对象存储主键（**需保存到业务表**） |

### CloudOssVo — OSS 对象存储

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ossId` | Long | 否 | 对象存储主键 |
| `fileName` | String | 否 | 文件名（不含原始后缀） |
| `originalName` | String | 否 | 原名（含后缀） |
| `fileSuffix` | String | 否 | 文件后缀名 |
| `url` | String | 否 | URL 地址 |
| `ext1` | String | 否 | 扩展字段 |
| `createTime` | Date（String） | 否 | 创建时间 |
| `createBy` | Long | 否 | 上传人 ID |
| `createByName` | String | 否 | 上传人名称 |
| `service` | String | 否 | 服务商（如 `aliyun`、`minio`） |

### CloudLoginVo — 登录验证信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `accessToken` | String | 否 | 授权令牌（子系统本地 token，登录成功后由 SDK 填充） |
| `refreshToken` | String | 否 | **预留，恒为 null**：ticket 桥接模型下无 OAuth 刷新令牌，续期请调 `/authCenter/refreshToken` |
| `expireIn` | Long | 否 | 授权令牌有效期（秒，SDK 填充） |
| `refreshExpireIn` | Long | 否 | **预留，恒为 null** |
| `clientId` | String | 否 | 应用 ID（SDK 填充） |
| `scope` | String | 否 | **预留，恒为 null** |
| `openid` | String | 否 | 仅微信小程序等透传 openid 的登录场景有值，普通登录为 null |

> 当前 `doLogin` 只填充 `accessToken`、`expireIn`、`clientId`；`refreshToken`/`refreshExpireIn`/`scope` 为预留字段。

### CloudCaptchaVo — 图形验证码

| 字段 | 类型 | 说明 |
|------|------|------|
| `captchaEnabled` | Boolean | 是否开启验证码；`false` 时无需传 `code`/`uuid` |
| `uuid` | String | 验证码唯一标识，登录/注册时原样回传 |
| `img` | String | base64 图片（不含 `data:image` 前缀） |

### CloudLoginBo — 账号密码登录入参

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |
| `code` | String | 否 | 图形验证码（认证中心开启时必填） |
| `uuid` | String | 否 | 来自 `/sso/captcha` |

> `clientId`、`grantType`（默认 `password`）、`tenantId`（默认 `000000`）由 SDK 自动兜底，实体类不含 `clientId`。

### CloudLoginNoCaptchaBo — 账号密码登录入参（免图形验证码）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |

> **与 [CloudLoginBo](#cloudloginbo--账号密码登录入参) 的区别**：本实体刻意不含 `code`、`uuid` 图形验证码字段，专用于 [1.14 `/sso/loginByPasswordNoCaptcha`](#114-账号密码登录免图形验证码-ssologinbypasswordnocaptcha)。`clientId`、`grantType`（默认 `password`）、`tenantId`（默认 `000000`）由 SDK 自动兜底。

### CloudPhoneLoginBo — 手机号登录入参

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phonenumber` | String | 是 | 手机号 |
| `smsCode` | String | 是 | 短信验证码（由 `/sso/sendSmsCodeByPhone` 下发） |

> `clientId`、`grantType`（默认 `sms`）、`tenantId` 由 SDK 自动兜底。

### CloudRegisterBo — 注册入参

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |
| `phonenumber` | String | 否 | 手机号（开启短信码时必填） |
| `code` | String | 否 | 图形验证码（开启时必填） |
| `uuid` | String | 否 | 来自 `/sso/captcha` |
| `userType` | String | 否 | 用户类型 |

> `clientId`、`grantType`、`tenantId` 由 SDK 自动兜底。

### CloudRegisterByCaptchaBo — 注册入参-图形验证码

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |
| `code` | String | 条件 | 图形验证码（认证中心开启时必填，用户根据图片输入） |
| `uuid` | String | 条件 | 来自 `/sso/captcha`；服务端按 `CAPTCHA_CODE_KEY + uuid` 校验 |
| `userType` | String | 否 | 用户类型 |

> 不含 `phonenumber`。`clientId`、`grantType`、`tenantId` 由 SDK 自动兜底。对应 `CloudPlatformHelper.registerByCaptcha(...)`，与 `CloudRegisterBo`、`CloudRegisterBySmsBo` 底层同为服务端 `register`。

### CloudRegisterBySmsBo — 注册入参-手机号短信

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |
| `phonenumber` | String | 是 | 手机号（服务端按 `CAPTCHA_CODE_KEY + phonenumber` 校验；成功后写入 sys_user.phonenumber） |
| `code` | String | 条件 | 短信验证码（`/sso/sendSmsCodeForRegister` 下发；**字段名是 `code`，非 `smsCode`**） |
| `userType` | String | 否 | 用户类型 |

> 不含 `uuid`（短信场景 `uuid` 为空属正常）。`clientId`、`grantType`、`tenantId` 由 SDK 自动兜底。对应 `CloudPlatformHelper.registerBySms(...)`。

### CloudSendSmsBo — 发短信码入参-用户名密码

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码（发送前先校验账号密码） |

> `tenantId` 由 SDK 自动兜底。

### CloudSendSmsByPhoneBo — 发短信码入参-手机号

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phonenumber` | String | 是 | 手机号 |

> `clientId`、`tenantId` 由 SDK 自动兜底。

### CloudSendSmsByResetBo — 发短信码入参-重置密码

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `oldPassword` | String | 是 | 旧密码 |
| `newPassword` | String | 是 | 新密码（发送前校验强度） |

> `clientId`、`tenantId` 由 SDK 自动兜底。

### CloudResetPasswordBo — 忘记密码入参

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phonenumber` | String | 是 | 手机号（收码的号码，用于定位用户） |
| `smsCode` | String | 是 | 短信验证码（`/sso/sendSmsCodeByPhone` 下发） |
| `newPassword` | String | 是 | 新密码 |

> `clientId`、`tenantId` 由 SDK 自动兜底。

### CloudSendSmsForRegisterBo — 发短信码入参-注册

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phonenumber` | String | 是 | 手机号（**尚未注册**的号码） |

> `clientId`、`tenantId` 由 SDK 自动兜底。与 [CloudSendSmsByPhoneBo](#cloudsendsmsbyphonebo--发短信码入参-手机号) 的区别：本实体用于注册场景，服务端不校验号码是否已存在。

### CloudWechatLoginBo — 微信小程序登录入参

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `grantType` | String | 否 | 固定 `xcx`（默认已填，认证中心据此路由到 `XcxAuthStrategy`） |
| `xcxCode` | String | 是 | 小程序 `wx.login` 返回的临时登录凭证（**字段名是 `xcxCode`，不是 `code`**） |
| `appid` | String | 否 | 小程序 appid；缺省由认证中心按 client 配置解析（服务端字段全小写 `appid`） |

> `clientId`、`tenantId` 由 SDK 自动兜底。本实体走总线 type=`login`（非 `loginByWechat`），与账号密码登录共用同一个服务端 handler。

### CloudUpdatePasswordBo — 修改密码入参-登录态

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `oldPassword` | String | 是 | 旧密码 |
| `newPassword` | String | 是 | 新密码（服务端校验强度） |

> `clientId`、`tenantId` 由 SDK 自动兜底；当前用户 `loginId` 由 SDK 自动注入（前端不可伪造）。与 [CloudResetPasswordBo](#cloudresetpasswordbo--忘记密码入参)（忘记密码、走短信码）不同，本实体用于已登录场景。

---

## 11. 接口快速索引

### 11.1 按业务功能

| 业务模块 | 端点 | 鉴权 |
|----------|------|------|
| **1. 登录认证** | [1.1 `GET /authCenter/getSsoUrl`](#11-get-authcenterssourl) | 需登录 |
| | [1.2 `/sso/getSsoAuthUrl`](#12--ssogetssoauthurl) | 免登录 |
| | [1.3 `/sso/doLoginByTicket`](#13--ssodologinbyticket) | 免登录 |
| | `GET /sso/captcha` | 免登录 |
| | `POST /sso/sendSmsCode` | 免登录 |
| | `POST /sso/sendSmsCodeByPhone` | 免登录 |
| | `POST /sso/sendSmsCodeForRegister` | 免登录 |
| | `POST /sso/sendSmsCodeByReset` | 免登录 |
| | `POST /sso/resetPassword` | 免登录 |
| | `POST /sso/register` | 免登录 |
| | `POST /sso/registerByCaptcha` | 免登录 |
| | `POST /sso/registerBySms` | 免登录 |
| | `POST /sso/loginByPassword` | 免登录 |
| | `POST /sso/loginByPasswordNoCaptcha` | 免登录（免图形验证码） |
| | `POST /sso/loginByPhone` | 免登录 |
| | `POST /sso/loginByWechat` | 免登录 |
| **2. 退出登录** | [2.1 `/sso/logout`](#21--ssologout) | 免登录 |
| | [2.2 `/sso/logoutByAlone`](#22--ssologoutbyalone) | 免登录 |
| **3. 当前用户** | [3.1 `GET /authCenter/getLoginUserInfo`](#31-get-authcentergetloginuserinfo) | 需登录 |
| | `POST /authCenter/updatePassword` | 需登录 |
| | `POST /authCenter/refreshToken` | 需登录 |
| **4. 菜单路由** | [4.1 `GET /authCenter/getMenuRouters`](#41-get-authcentergetmenurouters) | 需登录 |
| **5. 区域** | [5.1 `POST /authCenter/listAreas`](#51-post-authcenterlistareas) | 需登录 |
| | [5.2 `GET /authCenter/getAreaByCode`](#52-get-authcentergetareabycode) | 需登录 |
| **6. 公告** | [6.1 `POST /authCenter/listNotices`](#61-post-authcenterlistnotices) | 需登录 |
| | [6.2 `GET /authCenter/getNoticeById`](#62-get-authcentergetnoticebyid) | 需登录 |
| **7. 文件存储** | [7.1 `POST /authCenter/oss/upload`](#71-post-authcenterossupload) | 需登录 |
| | [7.2 `GET /authCenter/oss/getUrlById`](#72-get-authcenterossgeturlbyid) | 需登录 |
| | [7.3 `GET /authCenter/oss/getUrlByIds`](#73-get-authcenterossgeturlbyids) | 需登录 |
| | [7.4 `GET /authCenter/oss/listByIds`](#74-get-authcenterosslistbyids) | 需登录 |
| **8. 后台维护** | [8.1 `POST /authCenter/clearCache`](#81-post-authcenterclearcache) | 需登录 |
| **9. SSO 内部** | [9.1 `/sso/login`](#91--ssologin) | 免登录（SDK 内部） |
| | [9.2 `/sso/logoutCall`](#92--ssologoutcall) | 免登录（鉴权中心回调） |
| | [9.3 `/sso/pushC`](#93--ssopushc) | 免登录（鉴权中心回调） |

### 11.2 按 HTTP 方法

| 方法 | 路径 |
|------|------|
| GET | `/authCenter/getMenuRouters`, `/authCenter/getLoginUserInfo`, `/authCenter/getSsoUrl`, `/authCenter/getAreaByCode`, `/authCenter/getNoticeById`, `/sso/captcha` |
| GET | `/authCenter/oss/getUrlById`, `/authCenter/oss/getUrlByIds`, `/authCenter/oss/listByIds` |
| POST | `/authCenter/clearCache`, `/authCenter/listAreas`, `/authCenter/listNotices`, `/authCenter/oss/upload`, `/authCenter/updatePassword`, `/authCenter/refreshToken` |
| POST | `/sso/loginByPassword`, `/sso/loginByPasswordNoCaptcha`, `/sso/loginByPhone`, `/sso/loginByWechat`, `/sso/register`, `/sso/registerByCaptcha`, `/sso/registerBySms`, `/sso/sendSmsCode`, `/sso/sendSmsCodeByPhone`, `/sso/sendSmsCodeForRegister`, `/sso/sendSmsCodeByReset`, `/sso/resetPassword` |
| 任意 | `/sso/getSsoAuthUrl`, `/sso/doLoginByTicket`, `/sso/login`, `/sso/logout`, `/sso/logoutCall`, `/sso/pushC`, `/sso/logoutByAlone` |

---

## 12. 附录

### 12.1 版本变更记录

| 版本 | 变更内容 |
|------|----------|
| 1.2.0 | 增加 `sa-token.expose-controller` 多模块注册开关 |
| 1.3.0 | 新增 `CloudOssController`（OSS 文件上传 / 链接 / 列表） |
| 1.3.0 | 新增区域编码端点 `/authCenter/listAreas`、`/authCenter/getAreaByCode` |
| 1.3.0 | 新增通知公告端点 `/authCenter/listNotices`、`/authCenter/getNoticeById` |
| 1.3.0 | 新增鉴权中心 URL 配置端点 `/authCenter/getSsoUrl` |
| 1.3.3 | 新增免登直连接口（走 pushS 总线）：`/sso/captcha`、`/sso/sendSmsCode`、`/sso/sendSmsCodeByPhone`、`/sso/sendSmsCodeForRegister`、`/sso/sendSmsCodeByReset`、`/sso/resetPassword`、`/sso/register`（及图形/短信类型化端点 `/sso/registerByCaptcha`、`/sso/registerBySms`）、`/sso/loginByPassword`、`/sso/loginByPhone`、`/sso/loginByWechat`（供小程序/Android 自定义登录注册/忘记密码，无需 RSA 公钥） |
| 1.3.3 | 新增登录态账户操作（走 pushS 总线、SDK 自动注入 loginId）：`/authCenter/updatePassword`（修改密码）、`/authCenter/refreshToken`（续期） |
| 1.3.4 | 新增免图形验证码的密码登录端点 `/sso/loginByPasswordNoCaptcha`（入参同 `/sso/loginByPassword`，走服务端独立 `loginByPasswordNoCaptcha` handler）；是否免验证码由“命中哪个端点”决定，`/sso/loginByPassword` 的图形验证码不会被绕过 |

### 12.2 常见问题

**Q1：为什么我没有传 cookie，但是接口调用成功了？**
浏览器会自动带上同域 cookie。请确认后端服务、鉴权中心服务、前端页面的**根域**是否一致（cookie 是按域隔离的）。

**Q2：登录态过期后接口返回什么？**
HTTP 401 + 业务消息 `token 已过期`。前端应捕获后跳转到登录页（参见 [`/sso/getSsoAuthUrl`](#12--ssogetssoauthurl)）。

**Q3：上传文件超过 1.4MB 怎么办？**
请联系后端开发协商大文件分片上传方案，或使用第三方对象存储直传。

**Q4：oss 上传时是同步还是异步？**
本 SDK 是**同步**上传（依赖鉴权中心的 push 通道），前端需等待接口返回才能继续。
