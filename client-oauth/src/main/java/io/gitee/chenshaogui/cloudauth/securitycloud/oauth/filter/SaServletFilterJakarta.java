package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.filter;

import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.exception.StopMatchException;
import cn.dev33.satoken.filter.SaFilter;
import cn.dev33.satoken.filter.SaFilterAuthStrategy;
import cn.dev33.satoken.filter.SaFilterErrorStrategy;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.util.SaTokenConsts;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 全局鉴权过滤器（jakarta.servlet 版）。
 *
 * <p>本类是 sa-token 官方 {@code cn.dev33.satoken.filter.SaServletFilter} 的 jakarta 适配版，
 * 实现逻辑与官方 javax 版 1:1 一致，仅将 servlet 包名从 {@code javax.servlet} 替换为
 * {@code jakarta.servlet}。</p>
 *
 * <p><b>为什么 SDK 需要自带此类的 jakarta 版本</b>：
 * {@code sa-token-spring-boot-starter}（javax）与 {@code sa-token-spring-boot3-starter}（jakarta）
 * 内含同名类 {@code SaServletFilter}，两者无法同时在编译期引入 SDK（类路径冲突）。
 * 因此 SDK 以 {@code sa-token-core}（无 servlet 依赖）为基准编译本类，
 * 仅引入 {@code jakarta.servlet-api} 满足编译期类型引用；运行时由业务方
 * 按其 SpringBoot 版本自动装配（见 {@code CloudTokenClientConfigureJakarta}）。</p>
 *
 * <p>官方 javax 版中的 {@code SaServletOperateUtil.writeResult} 依赖
 * {@code sa-token-servlet}（javax 包），此处改为内联实现，逻辑一致：
 * 设置 JSON Content-Type 后写出结果。</p>
 *
 * @author click33（官方原版） / jakarta 适配：cloudauth
 * @since 1.2.0
 */
@Order(SaTokenConsts.ASSEMBLY_ORDER)
public class SaServletFilterJakarta implements SaFilter, Filter {

    // ------------------------ 设置此过滤器 拦截 & 放行 的路由

    /**
     * 拦截路由
     */
    public List<String> includeList = new ArrayList<>();

    /**
     * 放行路由
     */
    public List<String> excludeList = new ArrayList<>();

    @Override
    public SaServletFilterJakarta addInclude(String... paths) {
        includeList.addAll(Arrays.asList(paths));
        return this;
    }

    @Override
    public SaServletFilterJakarta addExclude(String... paths) {
        excludeList.addAll(Arrays.asList(paths));
        return this;
    }

    @Override
    public SaServletFilterJakarta setIncludeList(List<String> pathList) {
        includeList = pathList;
        return this;
    }

    @Override
    public SaServletFilterJakarta setExcludeList(List<String> pathList) {
        excludeList = pathList;
        return this;
    }


    // ------------------------ 钩子函数

    /**
     * 认证函数：每次请求执行
     */
    public SaFilterAuthStrategy auth = r -> {};

    /**
     * 异常处理函数：每次[认证函数]发生异常时执行此函数
     */
    public SaFilterErrorStrategy error = e -> {
        throw new SaTokenException(e);
    };

    /**
     * 前置函数：在每次[认证函数]之前执行
     *      <b>注意点：前置认证函数将不受 includeList 与 excludeList 的限制，所有路由的请求都会进入 beforeAuth</b>
     */
    public SaFilterAuthStrategy beforeAuth = r -> {};

    @Override
    public SaServletFilterJakarta setAuth(SaFilterAuthStrategy auth) {
        this.auth = auth;
        return this;
    }

    @Override
    public SaServletFilterJakarta setError(SaFilterErrorStrategy error) {
        this.error = error;
        return this;
    }

    @Override
    public SaServletFilterJakarta setBeforeAuth(SaFilterAuthStrategy beforeAuth) {
        this.beforeAuth = beforeAuth;
        return this;
    }


    // ------------------------ doFilter

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            // 执行全局过滤器
            beforeAuth.run(null);
            SaRouter.match(includeList).notMatch(excludeList).check(r -> {
                auth.run(null);
            });
        }
        catch (StopMatchException ignored) {}
        catch (BackResultException e) {
            writeResult(response, e.getMessage());
            return;
        }
        catch (Throwable e) {
            writeResult(response, String.valueOf(error.run(e)));
            return;
        }

        // 执行
        chain.doFilter(request, response);
    }


    // ------------------------ 内部工具

    /**
     * 写出结果到响应（对应官方 javax 版 SaServletOperateUtil.writeResult 的逻辑）
     */
    private static void writeResult(ServletResponse response, String result) {
        try {
            response.setContentType("application/json;charset=UTF-8");
            try (PrintWriter writer = response.getWriter()) {
                writer.write(result);
                writer.flush();
            }
        } catch (Exception ignored) {
        }
    }

}