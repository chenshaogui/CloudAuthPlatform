package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config;

import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.listener.CloudAuthListener;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * [Sa-Token 权限认证] 配置类
 *
 * @author sgchen
 */
@Slf4j
@ComponentScan(basePackages = {"io.gitee.chenshaogui.cloudauth.securitycloud.oauth"})
@Configuration
public class CloudTokenClientAutoConfigure {

    /**
     * 启动自检：确认 {@link SpringContextUtil} 静态持有者已就绪。
     *
     * <p>本 SDK 大量静态入口在读不到容器时做了“返回 null / 回落默认值”的静默降级，
     * 一旦装配链路失效不会有任何异常，只表现为“配置改了不生效”，定位成本极高，
     * 因此参照 hutool {@code SpringUtil.afterPropertiesSet} 的做法，在全部单例初始化
     * 完成后显式校验一次并告警。</p>
     *
     * <p>持有者不再由本类注册：{@code Environment} 由
     * {@code CloudSsoEnvironmentPostProcessor} 交接、{@code ApplicationContext} 由
     * {@code CloudSsoContextInitializer} 交接，两者均早于任何 Bean；本类
     * {@code @ComponentScan} 扫到的 {@code SpringContextUtil}（{@code @Component} +
     * {@code ApplicationContextAware}）仅作为非 Boot 环境的兜底。</p>
     *
     * @return 启动自检回调
     */
    @Bean
    public SmartInitializingSingleton springContextUtilSelfCheck() {
        return () -> {
            if (!SpringContextUtil.isInitialized()) {
                log.warn("[cloud-auth-client-oauth] SpringContextUtil 静态持有者未就绪："
                        + "EnvironmentPostProcessor / ApplicationContextInitializer / @ComponentScan 装配均未生效，"
                        + "SDK 读配置将全部回落默认值、getBean 将返回 null。"
                        + "请确认未排除 SDK 自动配置，且 jar 内 META-INF/spring.factories 完整。");
            }
        };
    }

    /**
     * 注册 JWT 版 StpLogic，与认证中心（cloud-auth-platform）的
     * {@code StpLogicJwtForSimple} 对齐。
     *
     * <p><b>为什么必须用 JWT 模式</b>：sa-token 中 token extra 扩展参数由
     * {@code StpLogic.isSupportExtra()} 控制，基类返回 {@code false}，
     * 仅 JWT 的三种实现返回 {@code true}。本 SDK 在登录时会写入
     * {@code setExtra(BROWSER_ID_KEY, browserId)}，若该参数被丢弃，
     * 认证中心 {@code SysLoginService.logout()} 遍历账号下所有 token、
     * 按 browserId 过滤踢人时，读取本 SDK 签发的 token 会因非 JWT 解析异常
     * 而被静默跳过（异常被 catch 吞掉），导致「同浏览器多 tab 一起登出」
     * 对客户端侧 token 失效。</p>
     *
     * <p><b>为什么是 Simple 而不是 Mixin / Stateless</b>：认证中心用的就是
     * {@code StpLogicJwtForSimple}（有状态 JWT：token 是 JWT，但账号映射与会话
     * 仍存 Redis）。两端必须同一模式且 {@code jwt-secret-key} 一致，token 才能互认。</p>
     *
     * <p><b>副作用</b>：JWT 模式下 {@code is-support-share} 恒为 false
     * （{@code StpLogicJwtForSimple.isSupportShareToken()} 硬编码返回 false），
     * 即每次都创建新 token，不复用旧 token。</p>
     *
     * <p>使用 {@code @ConditionalOnMissingBean} 让位于业务方自行定义的 StpLogic，
     * 避免容器中出现多个 StpLogic 导致 {@code SaBeanInject} 注入时抛
     * {@code NoUniqueBeanDefinitionException}。</p>
     *
     * @return JWT 简单模式的 StpLogic
     */
    @Bean
    @ConditionalOnMissingBean(StpLogic.class)
    public StpLogic stpLogicJwtForSimple() {
        log.info("[cloud-auth-client-oauth] 已启用 StpLogicJwtForSimple（JWT 简单模式），token 支持 extra 扩展参数");
        return new StpLogicJwtForSimple();
    }

    /**
     * 启动自检：确认最终生效的是 JWT 版 StpLogic。
     *
     * <p>若业务方自行注册了非 JWT 的 StpLogic（如 sa-token 默认实现），
     * 上面的 {@code @ConditionalOnMissingBean} 会跳过注册，此时会出现
     * 「配置看似正确但 token extra 静默失效」的隐性故障——该故障没有异常、
     * 没有报错，仅在「同浏览器多 tab 一起登出」等场景表现为功能不生效，
     * 排查成本极高，故此处显式告警。</p>
     *
     * @return 启动自检回调
     */
    @Bean
    public SmartInitializingSingleton ssoClientStpLogicSelfCheck() {
        return () -> {
            StpLogic logic = StpUtil.stpLogic;
            if (!isJwtMode(logic)) {
                log.warn("[cloud-auth-client-oauth] 当前生效的 StpLogic 为 {}，"
                                + "非 JWT 模式：登录时的 extra 扩展参数（browserId）将被静默丢弃，"
                                + "认证中心按 browserId 精准注销时无法匹配本系统签发的 token。"
                                + "请移除业务方自定义的 StpLogic Bean，或改为 StpLogicJwtForSimple，"
                                + "并确保 sa-token.jwt-secret-key 与认证中心一致。",
                        logic == null ? "null" : logic.getClass().getName());
            } else {
                log.info("[cloud-auth-client-oauth] StpLogic 自检通过，当前生效：{}", logic.getClass().getName());
            }
        };
    }

    /**
     * 自动收集并注册子系统定义的 {@link CloudAuthListener} Bean。
     *
     * <p>子系统只要把实现类声明为 Spring Bean（如 {@code @Component}），就会被 SDK 自动注册，
     * 登录 / 登出时自动回调，<b>无需业务方手动调用任何注册方法</b>（与 sa-token
     * 原生 {@code SaTokenListener} 的声明式用法对齐）。使用 {@link ObjectProvider} 延迟收集，
     * 容器内无任何 {@code CloudAuthListener} Bean 时为空流，不会报错。</p>
     *
     * @param listeners 容器内所有 {@link CloudAuthListener} Bean（按 {@code @Order} 排序）
     * @return 启动完成后执行的注册回调
     */
    @Bean
    public SmartInitializingSingleton cloudAuthListenerRegistrar(ObjectProvider<CloudAuthListener> listeners) {
        return () -> {
            listeners.orderedStream().forEach(listener -> {
                CloudPlatformHelper.registerAuthListener(listener);
                log.info("[cloud-auth-client-oauth] 已注册登录/登出监听器：{}", listener.getClass().getName());
            });
        };
    }

    /**
     * 判断是否为 JWT 版 StpLogic（token extra 可用）
     *
     * @param logic 当前 StpLogic
     * @return true 代表 JWT 模式
     */
    private boolean isJwtMode(StpLogic logic) {
        return logic instanceof StpLogicJwtForSimple
                || logic instanceof StpLogicJwtForMixin
                || logic instanceof StpLogicJwtForStateless;
    }
}
