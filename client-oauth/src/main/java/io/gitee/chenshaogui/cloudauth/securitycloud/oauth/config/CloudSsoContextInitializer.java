package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config;

import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.SpringContextUtil;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * [SDK 上下文早置入] {@code ApplicationContext} 提前交接器。
 *
 * <p>解决的是「静态持有者初始化时序不确定」这一类隐性故障：SDK 对外大量提供静态方法
 * （{@code CloudPlatformHelper.getValue} 等），只能经由
 * {@link SpringContextUtil} 的静态字段访问容器。而本 SDK 的自动配置在 Spring Boot
 * 装配顺序中<b>排在业务方配置之后</b>，加之 {@code ApplicationContextAware} 回调发生在
 * 该 Bean 自身初始化时刻，因此业务方 Bean 的构造器 / {@code @PostConstruct} 内调用
 * SDK 静态方法时，可能早于持有者就绪，拿到 {@code null} 并被各处的降级逻辑静默吞掉
 * （读配置回落默认值、取 Bean 返回 null），没有异常也没有日志，排查成本极高。</p>
 *
 * <p><b>本类的时机优势</b>：{@code ApplicationContextInitializer#initialize} 在
 * {@code SpringApplication#refreshContext} 之前执行，即 <b>早于所有 Bean 的实例化</b>，
 * 因此容器引用在此刻交接后，任何 Bean 生命周期的任意时刻调用
 * {@link SpringContextUtil#getBean} 都能拿到有效引用（Bean 本身要到刷新后期才存在，
 * 但调用方均在运行期，不受影响）。</p>
 *
 * <p><b>注册方式</b>：{@code META-INF/spring.factories} 的
 * {@code org.springframework.context.ApplicationContextInitializer}
 * （SpringBoot2 / SpringBoot3 均支持该注册方式，无需业务方任何配置）。</p>
 *
 * <p><b>与 Spring Cloud bootstrap 的兼容</b>：存在 bootstrap 上下文时本类会被执行两次，
 * 顺序为 bootstrap 先、主上下文后；{@link SpringContextUtil#initContext} 采用
 * last-wins 覆盖语义，最终持有的一定是承载业务 Bean 的主上下文，无需额外判重。</p>
 *
 * @author sgchen
 * @since 1.5.0
 */
public class CloudSsoContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // 仅交接引用，此处不读取任何 Bean，避免在容器刷新前触发实例化
        SpringContextUtil.initContext(applicationContext);
    }
}
