# SSO 中转页 ticket 重复消费处理说明（客户端前端改造）

> 用途：SDK 已移除 `CloudPlatformHelper.doLoginByTicket` 的 ticket 幂等重放缓存，**同一张 ticket 只提交一次改由客户端 SSO 中转页保证**。
> 本文档给 **接入统一认证平台的各子系统前端** 使用；后端 SDK 无需改动、认证中心无强制改动。
> 配套阅读：`cloud-auth-frontend/third.md`《SSO 接入优化指南》（中转页创建、路由白名单、退出登录）。

---

## 1. 背景与现象

以 `GET /sso/getEncryptedSsoAuthUrlForSilent` 触发的静默授权为例，完整链路：

1. 业务系统 401 / 主动进入中转页 → 调 SDK 拿到认证中心登录地址（参数收进 `auth_request` 密文，带 `auth_mode=silent`）；
2. 认证中心已登录 → 前端自动调 `/sso/getRedirectUrl` → 服务端 `buildRedirectUrl` 校验白名单、**签发一次性 ticket**，拼成 `...?back=<业务页>&ticket=<64位>` 回跳；
3. 客户端 SSO 中转页 `/sso` 拿到 `ticket`，调 `GET /sso/doLoginByTicket?ticket=xxx` → SDK 经 sa-token 向认证中心校验票据，**认证中心校验后立即删票** → 返回 `CloudLoginVo.accessToken`；
4. 前端把 token 写入本地存储，跳 `back` 进入业务页。

**问题**：ticket 是明文 query 参数，会残留在浏览器地址栏与历史记录里；`back` 为空、F5 刷新、浏览器后退、请求双发都会用同一张票再次请求 `/sso/doLoginByTicket`，此时票据已失效，返回「无效 ticket」。若前端把该错误统一按「登录超时 / 401」处理，就会 `removeToken()` → 重新跳转认证中心 → silent 又免登回来 → 表现为**登录态反复消失、闪屏、循环重新登录**。

---

## 2. 结论：后端不做幂等重放

曾在 `CloudPlatformHelper.doLoginByTicket` 加过「首次消费成功缓存登录结果、5 分钟 TTL、256 条上限、重放前校验本地 token」的本地缓存，**现已移除**，原因：

- 缓存是 JVM 本地 `ConcurrentHashMap`，**多实例部署时重复请求落到其它节点仍然失败**，进程重启即丢失，无法作为可靠保证；
- 达到 256 条上限会整体 `clear()`，突发登录时幂等窗口会突然失效；
- 缓存的是含 `accessToken` 的整个 `CloudLoginVo`，属于「复用旧 token」而非重新签发，语义上也不理想；
- 最关键：根因是「地址栏残留 ticket」+「前端把无效 ticket 误判为登录失效」，后端缓存只兜住重复请求这一半场景，治标不治本。

当前 SDK 保持 sa-token **ticket 单次有效**的安全语义，恢复为一次性消费：

```java
// src/main/java/io/gitee/chenshaogui/cloudauth/securitycloud/oauth/utils/CloudPlatformHelper.java
public static CloudLoginVo doLoginByTicket(String ticket) {
    SaCheckTicketResult ctr = SaSsoClientProcessor.instance.checkTicket(ticket);
    return doLogin(ctr);
}
```

> 后续**不要**在 SDK 或网关侧再加本地结果缓存来"兜底"该场景；需要兜底时应使用共享 Redis 且明确幂等键语义，但首选仍是本节的前端改造。

---

## 3. 前端必须落实的 4 点

| 序号 | 动作 | 落点 | 解决的问题 |
|---|---|---|---|
| ① | 消费后**立即清洗 URL**：`history.replaceState(null, '', location.pathname + location.hash)`，成功/失败两条路径都要执行，且在跳转前执行 | SSO 中转页 | 刷新、后退时地址栏不再有 ticket |
| ② | 跳 `back` 用 `location.replace(back \|\| '/')`，**back 为空兜底业务首页**；不要用 `location.href` / `router.push` | SSO 中转页 | 避免 `decodeURIComponent(undefined)` 停在带票页面，且历史记录不留回跳页 |
| ③ | **已登录短路**：进入中转页先校验本地登录态，已登录直接跳 `back`，不再消费 ticket | SSO 中转页 + 请求拦截器 | 刷新/后退/多标签进入时天然幂等 |
| ④ | **错误分类**：把「无效 ticket / 票已消费」与「登录超时」区分开；本地 token 仍有效时按已登录处理，不 `removeToken()`、不立即重新发起 silent 授权 | SSO 中转页 + 全局 401 拦截 | 直接消除「登录消失」 |

**③ 的重要前提**：本地登录态探测（可用 `GET /authCenter/getLoginUserInfo`）在未登录时会返回 401，**该探测请求必须排除在全局 401 拦截器之外**，否则探测本身就会把有效 token 清掉，反而制造问题。

**④ 的配套检查**：`request.ts` / `permission.ts` 中「401 → 清 token → 跳 /sso」的逻辑要加限定，只有本地 token 确实失效才触发重新授权；`third.md` 第 4 步「Token 过期处理：401 错误时跳转到 /sso」需按此理解。

---

## 4. 参考实现（Vue3 中转页）

以下为 `onMounted` 逻辑示意，按各项目实际的 `userStore` / 请求封装调整：

```ts
const ticket = getParam('ticket');
const back = getParam('back') || '/';

// ① 无论成败，先清 URL 再跳转
const cleanUrl = () => history.replaceState(null, '', location.pathname + location.hash);

// ③ 轻量探测：请求需跳过全局 401 拦截（skipAuthRedirect 等标记）
const isLocalLoginValid = async () => {
  try {
    await request({ url: '/authCenter/getLoginUserInfo', skipAuthRedirect: true });
    return !!getToken();
  } catch {
    return false;
  }
};

// ④ 只把「票据已消费」类错误归为可忽略，其余失败仍走重新授权
const isTicketInvalid = (e) =>
  /无效\s*ticket|ticket.*(已消费|失效|无效)|30004/i.test(e?.msg || e?.message || '');

onMounted(async () => {
  if (!ticket) { goSsoAuthUrl(); return; }

  // ③ 已登录短路：刷新 / 后退 / 多标签进入时不再消费票据
  if (await isLocalLoginValid()) {
    cleanUrl();
    location.replace(back);
    return;
  }

  // 防重：同一张票在并发或重复挂载下只提交一次
  const key = 'sso_ticket_' + ticket;
  if (sessionStorage.getItem(key)) {
    cleanUrl();
    location.replace(back);
    return;
  }
  sessionStorage.setItem(key, '1');

  try {
    const res = await userStore.ticketLogin({ ticket });   // POST /sso/doLoginByTicket
    localStorage.setItem('satoken', res.data.accessToken);
  } catch (e) {
    // 票已消费但本地 token 有效 → 按已登录继续，不清理登录态
    if (!isTicketInvalid(e) || !(await isLocalLoginValid())) {
      sessionStorage.removeItem(key);
      cleanUrl();
      goSsoAuthUrl();   // 真失败才重新授权
      return;
    }
  }

  // ①② 先清洗地址，再 replace 到业务页（back 为空时进首页）
  cleanUrl();
  location.replace(back);
});
```

`goSsoAuthUrl()` 保持原有逻辑：`/sso/getSsoAuthUrl` 或 `/sso/getEncryptedSsoAuthUrlForSilent`，`clientLoginUrl` 传中转页地址，`back` 传用户原本要去的业务页。

---

## 5. 验收清单

| 场景 | 期望结果 |
|---|---|
| 首次 silent 授权回跳 | 登录成功，地址栏无 `ticket`，直接进业务页 |
| 登录成功后 F5 刷新 | 不再请求 `/sso/doLoginByTicket`，或由已登录短路直接跳回；登录态保持 |
| 浏览器后退到回跳地址 | 不带票 / 短路跳过，不出现「无效 ticket」与重新登录 |
| `back` 为空或为 `undefined` | 进入业务首页，不停留在带票页面 |
| 主动登出后再访问 | 清本地 token 与 `sessionStorage` 防重标记，重新走 SSO 能正常登录 |
| 多实例部署 / 服务重启 | 行为一致（与后端节点无关） |
| 认证中心 `getRedirectUrl` 偶发失败 | 前端提示或重试，不清空有效登录态、不触发全局单点注销 |

---

## 6. 可选：认证中心前端配套优化（`cloud-auth-frontend`）

以下不阻塞本次改造，但能减少重复签票与误清登录态：

- `src/views/auth/SsoLoginPage.vue` 的 `silentAuthorizeTriggered` 只在单次页面生命周期内有效，刷新会重置并再次调 `/sso/getRedirectUrl`；该接口每次会删除同 client 的旧票，可能作废刚下发、尚未消费的 ticket。建议改用 `sessionStorage` 做跨刷新防重，或对同一 `client` 复用尚未消费的有效票。
- `handleSilentAuthorize()` 在 `getRedirectUrl` 失败时执行 `userStore.token = ''; removeToken()`；网络抖动时建议改为提示/重试，避免一次失败就清掉认证中心登录态（注意仍**不要**改调 `ssoLogout()`，否则会触发全局单点注销）。
- `src/utils/request.ts` / `src/permission.ts` 在 401 后重建 `/ssologin` 时保留 `auth_mode`，建议加节流，避免「401 → silent → 回跳 → 401」循环。

---

## 7. 相关代码索引

| 文件 | 位置 | 说明 |
|---|---|---|
| `cloud-auth-client-oauth/src/main/java/io/gitee/chenshaogui/cloudauth/securitycloud/oauth/utils/CloudPlatformHelper.java` | `doLoginByTicket` | 一次性消费 ticket，Javadoc 已写明前端接入要求 |
| `cloud-auth-client-oauth/src/main/java/io/gitee/chenshaogui/cloudauth/securitycloud/oauth/controller/CloudTokenSsoClientController.java` | `/sso/getEncryptedSsoAuthUrlForSilent`、`/sso/doLoginByTicket` | 生成静默授权地址 / 消费票据端点 |
| `cloud-auth-client-oauth/src/main/java/io/gitee/chenshaogui/cloudauth/securitycloud/oauth/controller/CloudAuthCenterController.java` | `/authCenter/getLoginUserInfo` | 本地登录态轻量探测可用端点 |
| `cloudauth-security-client-demo/cloudauth-security-client-vue3/src/views/sso-login.vue` | `doLoginByTicket` | demo 中转页，目前仍是 `location.href = decodeURIComponent(back)`，需按本文改造 |
| `cloud-auth-frontend/third.md` | 第 2、4 步 | 接入指南：已有 `replaceState` 写法，但缺 back 兜底 / 短路 / 错误分类 |
| `cloud-auth-platform/cloud-auth-platform-auth/.../SsoServerController.java` | `/sso/getRedirectUrl` | 签发一次性 ticket 并拼进回跳 URL |
| `sa-token-plugin/sa-token-sso/.../SaSsoServerTemplate.java` | `buildRedirectUrl`、`checkTicketParamAndDelete` | 签票（会删同 client 旧票）与校验后删票 |

---

## 8. 变更记录

- SDK `CloudPlatformHelper.java`：删除 `TICKET_REPLAY_TTL_MS` / `TICKET_REPLAY_MAX_SIZE` / `TICKET_REPLAY_CACHE` / `TicketReplay` / `findTicketReplay` / `saveTicketReplay` 及 `doLoginByTicket` 中的缓存分支；`mvn -o compile` 通过。
- 本文档：新增客户端前端改造说明。
- `third.md`：补充「ticket 一次性消费：中转页防重」一节，指向本文档。
