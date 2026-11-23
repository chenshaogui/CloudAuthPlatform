package io.gitee.chenshaogui.cloudauth.securitycloud.oauth;

import java.util.UUID;

/**
 * 浏览器维度 ID 公共门面（无 servlet 依赖）。
 *
 * <p>SDK 内部各 Filter（{@code BrowserIdFilter} / {@code BrowserIdFilterJakarta}）
 * 把当前请求线程的 {@code browserId} 写入本类的 {@link ThreadLocal}，
 * 业务代码（{@link io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper}、
 * {@link io.gitee.chenshaogui.cloudauth.securitycloud.oauth.template.RemoteSaSsoClientStrategy} 等）
 * 通过 {@link #current()} 与 {@link #HEADER_NAME} 与本门面交互，
 * <b>完全避开对 {@code javax.servlet.Filter} 的静态引用</b>。</p>
 *
 * <p><b>为什么必须有这个门面</b>：SpringBoot3.x 把 servlet API 从 {@code javax.servlet}
 * 迁移到 {@code jakarta.servlet}。如果业务代码直接静态引用 javax 版的
 * {@code BrowserIdFilter}，即使该 Filter 仅作为注解里的 @link 出现，
 * 也会触发其父接口 {@code javax.servlet.Filter} 的类加载，导致
 * {@code NoClassDefFoundError: javax/servlet.Filter}。把 servlet-API 收敛
 * 到 Filter 实现层、业务层只依赖本门面后，问题从根上消失。</p>
 *
 * <p><b>API 分层（参考 v1.2.0 双版本适配思路）</b>：本类与两个 Filter 实现放
 * 在同一个包 {@code io.gitee.chenshaogui.cloudauth.securitycloud.oauth}，因此
 * {@link #set(String)} / {@link #clear()} 仅 package-private，
 * <b>只有同包 Filter 实现类可以写入</b>；对外只暴露
 * {@link #current()} / {@link #HEADER_NAME} / {@link #COOKIE_NAME}
 * 三个 public 成员，业务代码无法污染 ThreadLocal。</p>
 *
 * @author sgchen
 * @since 1.2.0
 */
public final class BrowserIdContext {

    /**
     * HTTP Header 名称（透传给 auth server），与 javax / jakarta 版 Filter 保持一致。
     */
    public static final String HEADER_NAME = "X-Browser-Id";

    /**
     * Cookie 名称（同源自动跨 tab 共享），与 javax / jakarta 版 Filter 保持一致。
     */
    public static final String COOKIE_NAME = "__sdk_browser_id__";

    /**
     * 当前线程的 browserId（由对应 Filter 在请求进入时 set，请求结束时 remove）。
     */
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private BrowserIdContext() {
        // 工具类不允许实例化
    }

    /**
     * 取当前请求线程的 browserId。
     * <p>未设置（非请求线程，如定时任务 / 测试代码 / 启动初始化）时返回一个新 UUID，
     * 避免多个调用方共享同一个值造成误杀。</p>
     */
    public static String current() {
        String v = CURRENT.get();
        return (v == null || v.isEmpty()) ? UUID.randomUUID().toString() : v;
    }

    /**
     * 由对应 Filter 在请求进入时调用，把当前线程与 browserId 绑定。
     * <p>package-private：仅 {@code io.gitee.chenshaogui.cloudauth.securitycloud.oauth} 包内的
     * {@link BrowserIdFilter} / {@link BrowserIdFilterJakarta} 可以调用，
     * 业务侧（{@code utils/}、{@code template/}）无写入权限。</p>
     */
    static void set(String browserId) {
        CURRENT.set(browserId);
    }

    /**
     * 由对应 Filter 在请求结束时调用，清理 ThreadLocal，避免线程复用泄漏。
     * <p>package-private：仅 {@code io.gitee.chenshaogui.cloudauth.securitycloud.oauth} 包内的
     * {@link BrowserIdFilter} / {@link BrowserIdFilterJakarta} 可以调用。</p>
     */
    static void clear() {
        CURRENT.remove();
    }
}
