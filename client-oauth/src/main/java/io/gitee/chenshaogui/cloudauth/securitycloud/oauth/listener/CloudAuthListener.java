package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.listener;

import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudLoginUser;

/**
 * 子系统登录 / 登出监听器。
 *
 * <p>子系统只要提供一个实现本接口的 Spring Bean（如 {@code @Component}），SDK 会在启动时
 * 自动收集并注册，无需业务方手动调用任何注册方法——用法与 sa-token 原生
 * {@code SaTokenListener} 一致，但回调时机与入参更适合业务：</p>
 * <ul>
 *   <li><b>{@link #onLogin(CloudLoginUser)}</b>：在 SDK 完成本地登录<b>并拉取、写入全量用户数据之后</b>触发，
 *       入参为完整的 {@link CloudLoginUser}（不像原生 {@code doLogin} 事件那样早于用户数据加载）。</li>
 *   <li><b>{@link #onLogout(CloudLoginUser)}</b>：在 SDK 主动登出（{@code logout / logoutByAlone / ssoLogout}
 *       及权限失效本地登出）时触发；SDK 会<b>在销毁会话前</b>抓取用户快照并传入，
 *       因此即便会话已被清理，回调仍能拿到"是谁登出了"。</li>
 * </ul>
 *
 * <p>两个方法均为默认空实现，业务方只覆写关心的那一个即可。</p>
 *
 * <p><b>异常隔离</b>：SDK 对每个回调都做 try-catch 包裹，单个监听器抛异常不会影响登录 / 登出主流程，
 * 也不会影响其它监听器；但回调仍运行在请求线程内，<b>务必保持轻量</b>，耗时逻辑请自行异步。</p>
 *
 * <p><b>覆盖边界</b>：{@code onLogout} 只覆盖本 SDK 发起的登出入口。若登录态是被认证中心强制下线 / 全局
 * SSO 注销经 {@code logoutCall} 回调清理的，本回调不会触发；需要无死角监听所有登出时，请另行实现
 * sa-token 原生 {@code cn.dev33.satoken.listener.SaTokenListener#doLogout}。</p>
 *
 * @author sgchen
 * @since 1.3.5
 */
public interface CloudAuthListener {

    /**
     * 用户登录成功后回调（此时全量用户数据已写入 Session，可安全读取）。
     *
     * @param user 登录用户全量信息；正常情况下非空，若登录时用户数据拉取失败可能为 {@code null}
     */
    default void onLogin(CloudLoginUser user) {
        // 默认不处理，业务方按需覆写
    }

    /**
     * 用户登出后回调（SDK 已在销毁会话前抓取用户快照）。
     *
     * @param user 登出用户快照；若登出前 Session 中无缓存的 loginUser 则为 {@code null}
     */
    default void onLogout(CloudLoginUser user) {
        // 默认不处理，业务方按需覆写
    }
}
