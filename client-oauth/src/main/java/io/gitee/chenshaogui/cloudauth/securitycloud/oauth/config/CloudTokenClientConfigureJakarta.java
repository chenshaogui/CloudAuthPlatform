package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.fun.strategy.SaCorsHandleFunction;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import cn.dev33.satoken.util.SaResult;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.BrowserIdFilterJakarta;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.core.CloudAuthPermissionImpl;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.filter.SaServletFilterJakarta;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.SpringContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * [Sa-Token 权限认证] 配置类（jakarta.servlet 版，SpringBoot3.x 专用）。
 *
 * <p>本类是同包 {@link CloudTokenClientConfigure}（javax.servlet / SpringBoot2 版）的
 * jakarta 适配版，功能与逻辑 1:1 一致，仅存在以下两处实现差异：</p>
 * <ol>
 *   <li>全局鉴权过滤器使用自带的 {@link SaServletFilterJakarta}，而非
 *       {@code sa-token-spring-boot-starter} 的 javax 版 SaServletFilter
 *       （两者类名相同无法同时在 SDK 编译期引入，详见 SaServletFilterJakarta 的类注释）；</li>
 *   <li>{@code rtnNoAuthRsp} 中获取原生 HttpServletRequest 改用
 *       {@code SaHolder.getRequest().getSource()}（sa-token-core API，运行时由
 *       sa-token-spring-boot3-starter 提供的上下文返回 jakarta 实现），
 *       而非 {@code SpringMVCUtil.getRequest()}（该类同样存在 javax/jakarta 双版本类名冲突）。</li>
 * </ol>
 *
 * <p><b>条件装配</b>：本类标注 {@code @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")}，
 * 当且仅当 classpath 存在 jakarta servlet API（SpringBoot3.x 环境）时由
 * {@code CloudTokenClientAutoConfigure} 的 {@code @ComponentScan} 注册；
 * SpringBoot2 环境自动跳过（条件评估基于 ASM 读取字节码，不会真正加载本类，
 * 因此不会触发 jakarta 类缺失异常）。</p>
 *
 * @author sgchen
 * @since 1.2.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
@ConditionalOnProperty(
        prefix = "sa-token",
        name = "only-login",
        havingValue = "false",
        matchIfMissing = true
)
@EnableConfigurationProperties(CloudProperties.class)
public class CloudTokenClientConfigureJakarta implements WebMvcConfigurer {

    /**
     * SDK 硬默认白名单（始终生效，不受外部配置影响）。
     * - /sso/* ：SDK 自身 SSO Controller（doLoginByTicket 等），登录前必须可访问
     * - /favicon.ico：浏览器自动请求
     *
     * <p>使用 {@code Collections.unmodifiableList(Arrays.asList(...))} 而非 Java 9+ 的
     * {@code List.of(...)}，SDK 编译目标为 1.8。不可变包装保证运行期不会被外部误改。</p>
     */
    private static final List<String> DEFAULT_PERMIT_PATHS =
            Collections.unmodifiableList(Arrays.asList("/sso/*", "/favicon.ico"));

    /**
     * CORS 配置读取键名（业务方可在 application.yml 中覆盖，与 javax 版一致）：
     *   sa-token.client.cors.allowed-origins=http://a.com,http://b.com
     *   sa-token.client.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
     *   sa-token.client.cors.allowed-headers=Authorization,Content-Type
     *   sa-token.client.cors.max-age=3600
     */
    private static final String CORS_ORIGINS_KEY = "sa-token.client.cors.allowed-origins";
    private static final String CORS_METHODS_KEY = "sa-token.client.cors.allowed-methods";
    private static final String CORS_HEADERS_KEY = "sa-token.client.cors.allowed-headers";
    private static final String CORS_MAX_AGE_KEY = "sa-token.client.cors.max-age";

    /**
     * 权限接口实现（使用 bean 注入方便用户替换）
     */
    @Bean
    public StpInterface stpInterface() {
        return new CloudAuthPermissionImpl();
    }

    /**
     * 浏览器维度识别 Filter：为每个浏览器分配稳定 browserId（cookie 持久化），
     * 透传到 sa-token SSO 调用的 X-Browser-Id header，实现"同浏览器多 tab 一起登出"。
     * <p>
     * 注册为 Servlet Filter 而不是 WebMvc Interceptor，是因为 sa-token SSO
     * 的 push/checkTicket 调用走底层 HTTP，需要在最早时机拿到 browserId。
     * 此处注册 jakarta 版 {@link BrowserIdFilterJakarta}。
     * </p>
     * <p><b>注册方式说明</b>：与 javax 版不同，这里不能使用
     * {@code FilterRegistrationBean<BrowserIdFilterJakarta>}——SDK 编译期基于
     * SpringBoot 2.7，该泛型类的上界是 javax.servlet.Filter，与 jakarta 类型不兼容。
     * 改为 @Bean 直接注册：SpringBoot3 的 ServletContextInitializerBeans 会自动
     * 包装任意 jakarta Filter Bean 并按默认 urlPattern /* 生效，顺序由
     * {@link BrowserIdFilterJakarta#getOrder()} 提供（HIGHEST_PRECEDENCE）。</p>
     */
    @Bean
    public BrowserIdFilterJakarta browserIdFilter() {
        return new BrowserIdFilterJakarta();
    }

    /**
     * CORS 跨域处理策略
     * 接口签名：void execute(SaRequest req, SaResponse res, SaStorage sto)
     */
    @Bean
    public SaCorsHandleFunction corsHandle() {
        return (req, res, sto) -> {
            String origins = readCorsConfig(CORS_ORIGINS_KEY, "*");
            String methods = readCorsConfig(CORS_METHODS_KEY, "POST, GET, OPTIONS, DELETE");
            String headers = readCorsConfig(CORS_HEADERS_KEY, "*");
            String maxAge = readCorsConfig(CORS_MAX_AGE_KEY, "3600");

            res.setHeader("Access-Control-Allow-Origin", origins)
                    .setHeader("Access-Control-Allow-Methods", methods)
                    .setHeader("Access-Control-Max-Age", maxAge)
                    .setHeader("Access-Control-Allow-Headers", headers);

            // OPTIONS 预检请求只打印日志，无需额外处理（过滤器逻辑由 SaServletFilterJakarta 控制）
            if (req.isMethod(SaHttpMethod.OPTIONS)) {
                log.debug("CORS 预检请求: {}", req.getUrl());
            }
        };
    }

    /**
     * 全局过滤器：仅做本地 token 校验，避免每个请求都远程调用 isLogin
     * 远程校验请在业务侧通过 CloudPlatformHelper.isRemoteLogin() 显式触发
     * <p>
     * 此处使用 SDK 自带的 jakarta 版 {@link SaServletFilterJakarta}。
     * </p>
     * <p><b>白名单合并规则</b>：合并两类路径：
     * <ol>
     *   <li>{@link #DEFAULT_PERMIT_PATHS} —— SDK 硬默认（{@code /sso/*}、{@code /favicon.ico}），永远生效；</li>
     *   <li>{@code sa-token.permit-paths} —— SDK 自身配置项（{@link CloudProperties#getPermitPaths()}）。</li>
     * </ol>
     * 第三方框架（如芋道 starter-security）若需与本 SDK 共享白名单，应自行实现
     * {@link org.springframework.boot.env.EnvironmentPostProcessor}，将自身配置项合并到
     * {@code sa-token.permit-paths}，避免 SDK 反向耦合业务框架。
     * </p>
     */
    @Bean
    public SaServletFilterJakarta getSaServletFilter(CloudProperties cloudProperties) {
        List<String> excludeList = new ArrayList<>(DEFAULT_PERMIT_PATHS);
        if (cloudProperties.getPermitPaths() != null) {
            excludeList.addAll(cloudProperties.getPermitPaths());
        }
        return new SaServletFilterJakarta()
                .addInclude("/**")
                .addExclude(excludeList.toArray(new String[0]))
                .setAuth(obj -> {
                    // ★ 动态白名单兜底（支持配置中心热更新，如 Nacos / Apollo）：
                    //   excludeList 是本 Bean 创建时的「启动快照」，配置中心变更后不会同步到它；
                    //   此处每次请求都重新读取 CloudProperties.permitPaths ——
                    //   Spring Cloud 的 ConfigurationPropertiesRebinder 会把属性重新绑定到
                    //   「同一个 CloudProperties 实例」（destroy + initialize 同一对象），
                    //   因此这里读到的永远是最新值，运行期新增的白名单无需重启即可生效。
                    //   注意：SDK 无需引入任何配置中心依赖，配置源与刷新事件均由业务工程提供。
                    if (isDynamicPermitPath(cloudProperties.getPermitPaths())) {
                        return;
                    }
                    if (!StpUtil.isLogin()) {
                        rtnNoAuthRsp();
                        SaRouter.back();
                    }
                    // 权限同步：按节流周期（默认 20 秒）检查服务端是否有权限变更，
                    // 未到周期时零开销；必须放在请求线程内执行，因为后台定时任务线程
                    // 没有 Sa-Token 请求上下文，StpUtil.isLogin() 会直接抛异常
                    try {
                        CloudPlatformHelper.checkPermissionChangeIfNeeded();
                    } catch (NotLoginException e) {
                        // 用户已被禁用 / 删除 / 强退（SDK 已同时执行本地登出），
                        // 立即中断本次请求，避免已失效的会话继续访问业务数据
                        rtnNoAuthRsp();
                        SaRouter.back();
                    }
                });
    }

    /**
     * 动态白名单匹配（每次请求实时读取配置，支持配置中心热更新）。
     *
     * <p>与 {@link #getSaServletFilter(CloudProperties)} 中写入 {@code excludeList} 的
     * 「启动快照」互补：excludeList 只包含 Bean 创建那一刻的配置，
     * 本方法负责兜住运行期（配置中心变更后）新增的白名单。</p>
     *
     * <p>路径匹配使用 sa-token 的 {@link SaFoxUtil#vagueMatch(String, String)}
     * （{@code *} 通配任意字符，{@code /**} 通配多层路径），与
     * {@code addExclude} 的匹配口径完全一致。</p>
     *
     * @param permitPaths 最新的白名单配置，可能为 {@code null} / 空集合
     * @return {@code true} 表示当前请求命中白名单，应跳过登录校验
     */
    private static boolean isDynamicPermitPath(List<String> permitPaths) {
        if (permitPaths == null || permitPaths.isEmpty()) {
            return false;
        }
        String path = SaHolder.getRequest().getRequestPath();
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String pattern : permitPaths) {
            if (pattern != null && !pattern.isEmpty() && SaFoxUtil.vagueMatch(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 401 响应（仅写状态码与 body，不重复设置 CORS 头，由 corsHandle 统一处理）
     * <p>jakarta 版：通过 {@code SaHolder.getRequest().getSource()} 获取原生请求对象，
     * 避免依赖 {@code SpringMVCUtil}（该类在 sa-token 双 starter 中同名冲突）。</p>
     */
    private void rtnNoAuthRsp() {
        try {
            SaResponse saResponse = SaHolder.getResponse();
            HttpServletResponse response = (HttpServletResponse) saResponse.getSource();
            response.setContentType("application/json;charset=UTF-8");
            // 1. 设置 401 状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            // 2. 构建 JSON 响应体
            Object source = SaHolder.getRequest().getSource();
            String queryString = (source instanceof HttpServletRequest)
                    ? ((HttpServletRequest) source).getQueryString() : null;
            String currentUrl = SaHolder.getRequest().getUrl();
            String backUrl = SaFoxUtil.joinParam(currentUrl, queryString);
            String loginUrl = "/sso/login?back=" + SaFoxUtil.encodeUrl(backUrl);
            String resJson = SaResult.get(HttpServletResponse.SC_UNAUTHORIZED,
                    "登录状态已失效，请重新登录", loginUrl).toString();

            // 3. 写入响应体
            try (PrintWriter writer = response.getWriter()) {
                writer.write(resJson);
                writer.flush();
            }
        } catch (BackResultException e) {
            // 框架捕获后用于中断链路
            throw e;
        } catch (Exception e) {
            throw new BackResultException(e.getMessage());
        }
    }

    /**
     * 读取 CORS 配置项，缺省时返回 fallback
     */
    private static String readCorsConfig(String key, String fallback) {
        try {
            Environment env = SpringContextUtil.getEnvironment();
            if (env == null) {
                return fallback;
            }
            String value = env.getProperty(key);
            return (value == null || value.isEmpty()) ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }

}