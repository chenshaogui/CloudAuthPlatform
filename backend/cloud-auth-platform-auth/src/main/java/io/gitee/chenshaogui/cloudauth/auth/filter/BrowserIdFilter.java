package io.gitee.chenshaogui.cloudauth.auth.filter;

import cn.hutool.core.util.IdUtil;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.BrowserIdContext;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 浏览器维度识别过滤器（auth server 端）
 * <p>
 * 与客户端 SDK 的 BrowserIdFilter 对称（cookie 优先）：
 * 1. 优先从 cookie __sdk_browser_id__ 取：cookie 是同一浏览器在授权中心与各子系统之间
 *    唯一共享的介质，作为 browserId 的唯一真源
 * 2. 否则用请求头 X-Browser-Id（兼容前端显式传入 / 服务端转发）
 * 3. 都没有则生成新 UUID
 * 4. cookie 缺失或与最终解析值不一致时回写 cookie，让所有系统最终收敛到同一个值
 * </p>
 *
 * <p>识别结果写入 {@link BrowserIdContext}（线程级），
 * 供 {@link io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper#login} 读取并写入 sa-token token extra。</p>
 *
 * @author sgchen
 */
public class BrowserIdFilter implements Filter {

    /** 与 SDK 端保持一致 */
    public static final String COOKIE_NAME = "__sdk_browser_id__";
    public static final String HEADER_NAME = "X-Browser-Id";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        HttpServletResponse out = (HttpServletResponse) res;

        String browserId = null;
        String fromCookie = null;

        // 1. 优先 cookie：cookie 是同一浏览器在授权中心与各子系统之间唯一共享的介质，作为唯一真源。
        //    不能 header 优先：前端拦截器可能从 localStorage 生成一个与 cookie 不同的 X-Browser-Id，
        //    若 header 优先，会把那个值写进 token extra，导致与其它系统的 browserId 不一致、登出杀不掉。
        Cookie[] cookies = http.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    fromCookie = c.getValue();
                    break;
                }
            }
        }
        browserId = fromCookie;

        // 2. 没有 cookie 再用 header（兼容前端显式传入 / 服务端转发）
        if (browserId == null || browserId.isEmpty()) {
            browserId = http.getHeader(HEADER_NAME);
        }

        // 3. 仍没有则生成新 id（首次访问）
        if (browserId == null || browserId.isEmpty()) {
            browserId = IdUtil.simpleUUID();
        }

        // 4. cookie 缺失，或 cookie 与最终解析值不一致时回写，让所有系统最终收敛到同一个 cookie 值。
        //    例如首次由 header 种下时，把该值固化到 cookie，后续其它系统一律以它为准。
        if (fromCookie == null || !fromCookie.equals(browserId)) {
            Cookie cookie = new Cookie(COOKIE_NAME, browserId);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24 * 365); // 1 年
            cookie.setHttpOnly(false);
            out.addCookie(cookie);
        }

        BrowserIdContext.set(browserId);
        try {
            chain.doFilter(req, res);
        } finally {
            BrowserIdContext.clear();
        }
    }
}