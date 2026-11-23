package io.gitee.chenshaogui.cloudauth.securitycloud.oauth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.UUID;

/**
 * 浏览器维度识别过滤器（jakarta.servlet 版，SpringBoot3.x 专用）。
 *
 * <p>本类是同包 {@link BrowserIdFilter}（javax.servlet 版）的 jakarta 适配版，
 * 功能与逻辑完全一致，仅替换 servlet 包名，供 SpringBoot3.x 项目使用。
 * 两个版本由 {@code CloudTokenClientConfigure} / {@code CloudTokenClientConfigureJakarta}
 * 按 classpath 中实际存在的 servlet API 条件装配，运行时只有一个生效。</p>
 *
 * <p>解析优先级（以 cookie 为唯一真源）：cookie {@code __sdk_browser_id__} &gt; header {@code X-Browser-Id} &gt; 生成新 UUID；
 * 只要 cookie 缺失或与最终值不一致，就回写 cookie。
 * 这样即使前端拦截器从 localStorage 生成了一个与 cookie 不同的 {@code X-Browser-Id} 头，
 * 也一律以 cookie 为准，保证同一浏览器在授权中心与各子系统写入 token 的 browserId 完全一致
 * （这是“只退同一浏览器”成立的关键），跨浏览器则天然独立。</p>
 *
 * <p><b>无默认值</b>：未拿到 header/cookie 时立即生成新 UUID，每个浏览器天然独立，
 * 避免"共享桶"导致登出误杀。</p>
 *
 * <p><b>ThreadLocal 收敛</b>：本类与 {@link BrowserIdFilter}（javax 版）不再独立持有
 * {@code ThreadLocal}，全部委托给公共门面 {@link BrowserIdContext}，
 * 这样业务代码（{@code CloudPlatformHelper} / {@code RemoteSaSsoClientStrategy}）
 * 永远不需要静态 import 任何 Filter 实现，从根上避免
 * {@code NoClassDefFoundError: javax/servlet/Filter}。</p>
 *
 * @author sgchen
 * @since 1.2.0
 */
public class BrowserIdFilterJakarta implements Filter, Ordered {

    /** 当前线程的 browserId 已迁出至 {@link BrowserIdContext}，本类不再持有 ThreadLocal。 */

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        HttpServletResponse out = (HttpServletResponse) res;

        String browserId = null;
        String fromCookie = null;

        // 1. 优先 cookie：cookie 是同一浏览器在授权中心与各子系统之间唯一共享的介质，作为唯一真源。
        //    注意不能 header 优先：前端拦截器可能从 localStorage 生成一个与 cookie 不同的 X-Browser-Id，
        //    若 header 优先，会把那个值写进 token extra，导致与其它系统的 browserId 不一致、登出杀不掉。
        Cookie[] cookies = http.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (BrowserIdContext.COOKIE_NAME.equals(c.getName())) {
                    fromCookie = c.getValue();
                    break;
                }
            }
        }
        browserId = fromCookie;

        // 2. 没有 cookie 再用 header（兼容前端显式传入 / 服务端转发）
        if (browserId == null || browserId.isEmpty()) {
            browserId = http.getHeader(BrowserIdContext.HEADER_NAME);
        }

        // 3. 仍没有则生成新 id（首次访问）
        if (browserId == null || browserId.isEmpty()) {
            browserId = UUID.randomUUID().toString();
        }

        // 4. cookie 缺失，或 cookie 与最终解析值不一致时回写，让所有系统最终收敛到同一个 cookie 值。
        //    例如首次由 header 种下时，把该值固化到 cookie，后续其它系统一律以它为准。
        if (fromCookie == null || !fromCookie.equals(browserId)) {
            Cookie cookie = new Cookie(BrowserIdContext.COOKIE_NAME, browserId);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24 * 365); // 1 年
            cookie.setHttpOnly(false); // 允许 JS 读取，便于排查
            out.addCookie(cookie);
        }

        BrowserIdContext.set(browserId);
        try {
            chain.doFilter(req, res);
        } finally {
            BrowserIdContext.clear();
        }
    }

    /**
     * 过滤器顺序：最高优先级（与 javax 版通过 FilterRegistrationBean.setOrder(HIGHEST_PRECEDENCE) 等效）。
     * <p>jakarta 版无法使用 FilterRegistrationBean 注册（其泛型上界在 SDK 编译期
     * 绑定的是 javax.servlet.Filter，与 jakarta 类型不兼容），
     * 因此改为 @Bean 直接注册 + Ordered 控序，SpringBoot3 会自动注册 Filter Bean
     * 并按本值排序。</p>
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
