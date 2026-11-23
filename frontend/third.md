# SSO 接入优化指南
## 实现步骤
1. 创建 SSO 中转页面
  * 创建页面文件：新建 sso.vue 页面作为 SSO 中转处理页面
  * 配置路由：创建对应的 /sso 路由，确保该路由可以被访问
  * 功能职责：
    * 检查 URL 中是否存在 ticket 参数
    * 存在 ticket 时，通过 ticket 获取用户登录信息
    * 不存在 ticket 时，获取统一平台登录页面 URL 并跳转

2. 实现核心登录逻辑
新增以下 API 接口：
   * /sso/doLoginByTicket - 通过 ticket 进行登录
   * /sso/getSsoAuthUrl - 获取 SSO 认证 URL
代码实现
   ```
     // 通过 ticket 获取用户登录信息
     
    const doLoginByTicket = async () => {
     const [err] = await to(userStore.ticketLogin({ ticket: ticket, platformIndex: Config.getPlatformIndex() }));
        history.replaceState(null, '', location.pathname + location.hash);
        await router.push('/');
     };

    // 获取 SSO 认证 URL 并跳转
    const getSsoAuthUrl = async () => {
       const response = await getSsoAuthUrlApi({ clientLoginUrl: location.origin + location.pathname });
      if (response && response.data) {
        location.href = response.data;
      }
    };
   
    // 页面加载时判断 ticket 是否存在
    onMounted(() => {
      if (ticket) {
        doLoginByTicket();
      } else {
        getSsoAuthUrl();
      }
    });
   ```

3. 配置路由白名单
   * 在 permission.ts 中
     * 配置 SSO 路由白名单（此配置确保：/sso 路由不会被登录拦截）：
     ```
        const whiteList = ['sso'];
     ```
     * 统一拦截跳转到 /sso并携带ticket参数:
     ```
     next({ path: '/sso', query: { ticket: to.query.ticket } });
     ```

4. 处理退出登录
   在相关退出登录操作中统一跳转至 /sso 页面：
   * 顶部退出登录：点击退出时跳转到 /sso
   * Token 过期处理：401 错误时跳转到 /sso
   * 其他退出场景：统一使用 SSO 退出流程

5. ticket 一次性消费：中转页防重（必做）

   认证中心回跳 URL 形如 `...?back=<业务页>&ticket=<64位>`，ticket 由 sa-token 保证单次有效、校验后立即删除，但会残留在地址栏与浏览器历史中。**SDK 后端不再做 ticket 幂等重放缓存**（JVM 本地缓存多实例下无效，且无法解决前端误判登录失效），因此上面第 2 步的示例还需补齐以下约束，否则 `back` 为空、F5 刷新、浏览器后退都会用同一张票二次请求 `/sso/doLoginByTicket`，被判为「登录超时」→ 清 token → 反复重新登录：

   * `history.replaceState` 必须在**成功和失败两条路径**都执行，且在跳转前执行；
   * 跳转用 `location.replace(back || '/')`，back 为空时兜底业务首页；不要用 `router.push('/')` / `location.href`，避免历史记录留下带票页面；
   * 进入中转页先校验本地登录态（可用 `/authCenter/getLoginUserInfo` 轻量探测），已登录直接跳 back、不再消费 ticket；**该探测请求的 401 必须排除在全局 401 拦截器之外**，否则探测本身会清掉有效 token；
   * 把「无效 ticket / 票已消费」与「登录超时」区分开：票已消费但本地 token 仍有效时按已登录处理，不要 `removeToken()`、不要立即重新发起 silent 授权；只有本地 token 确实失效才按第 4 步跳 /sso 重新授权；
   * 用 `sessionStorage` 记录正在消费 / 已消费的 ticket，防止并发与重复挂载双发。

   > 完整示例、验收清单与代码索引见同级项目 `cloud-auth-client-oauth/doc/SSO中转页-ticket重复消费处理说明.md`。