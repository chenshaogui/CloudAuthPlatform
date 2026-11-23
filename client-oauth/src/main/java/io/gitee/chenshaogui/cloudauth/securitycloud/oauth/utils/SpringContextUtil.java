package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring 上下文工具类（无注解获取Environment）
 * 提供静态获取能力，Spring 容器未初始化时返回 null 而非抛异常
 *
 * <h3>为什么需要静态持有者</h3>
 * <p>SDK 对外能力大量以静态方法提供（{@code CloudPlatformHelper.getValue}、
 * {@code SsoClientContext.getValue}、{@code CloudTokenClientConfigure.readCorsConfig} 等），
 * 静态方法无实例，无法使用构造器注入，只能靠静态持有者中转。</p>
 *
 * <h3>装配路径（按生效时机从早到晚）</h3>
 * <ol>
 *   <li><b>主路径</b>：{@code CloudSsoEnvironmentPostProcessor}（容器刷新<b>之前</b>）调用
 *       {@link #initEnvironment}，将 {@code Environment} <b>引用</b>（非快照）存入，
 *       因此读配置在任何 Bean 的构造器 / {@code @PostConstruct} 里都不会拿到 null，
 *       后续 {@code application.yml}、Nacos 追加的属性源对同一引用依然可见
 *       （容器初始化时会被容器自带的同一个 Environment 实例覆盖，语义更权威）；</li>
 *   <li><b>主路径</b>：{@code CloudSsoContextInitializer}（早于所有 Bean 实例化）调用
 *       {@link #initContext}，使 {@link #getBean} 在任何 Bean 生命周期内都能拿到容器引用；</li>
 *   <li><b>兜底</b>：本类标 {@code @Component} 并实现 {@code ApplicationContextAware}，
 *       供不走 {@code SpringApplication} 的场景（如手工
 *       {@code AnnotationConfigApplicationContext}、非 Boot 的集成测试）通过容器回调
 *       完成交接——这些场景下 spring.factories 里的两个钩子根本不会执行。</li>
 * </ol>
 * <p>各路径均只写同一组静态字段，重复调用幂等无副作用；存在 Spring Cloud
 * bootstrap 上下文时会被多次调用，以最后写入（主上下文）为准。</p>
 */
@Slf4j
@Component
public class SpringContextUtil implements ApplicationContextAware {

    /**
     * 字段必须为 {@code volatile}：写入发生在启动线程（initializer / Bean 初始化），
     * 读取发生在 Tomcat 工作线程，两者之间没有 JMM 定义的 happens-before 边；
     * 不加 volatile 属于数据竞争，已写入的引用可能长期对读线程不可见。读开销近乎为零（x86 上
     * volatile 读就是普通 load），不值得为微优化牺牲正确性。
     */
    private static volatile ApplicationContext applicationContext;

    /**
     * 早于容器阶段存入的 Environment 引用；为空时回退到 applicationContext 获取
     */
    private static volatile Environment environment;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        initContext(context);
    }

    /**
     * 交接 {@code Environment} 引用（{@code EnvironmentPostProcessor} 调用）。
     *
     * <p><b>存引用而非快照</b>：{@code EnvironmentPostProcessor} 可跑在 Boot 2.4+
     * 的 {@code ConfigDataEnvironmentPostProcessor} 之前，此刻 {@code application.yml}
     * 可尚未加载；但只要持有同一个 {@code MutablePropertySources} 对象，后续追加的
     * 属性源在运行期读取时全部可见，因此不存在时机风险，也天然兼容配置中心热更新。</p>
     *
     * @param env 环境对象，为 {@code null} 时忽略（不清空已有引用）
     */
    public static void initEnvironment(Environment env) {
        if (env == null) {
            return;
        }
        boolean firstInit = environment == null;
        environment = env;
        if (firstInit) {
            // 首次注入打印一次，便于在不打断点的情况下确认 SDK 装配链路已生效
            log.info("[cloud-auth-client-oauth] SpringContextUtil 已提前持有 Environment（EnvironmentPostProcessor 路径）");
        }
    }

    /**
     * 交接 {@code ApplicationContext} 引用（{@code CloudSsoContextInitializer} 主路径、
     * {@code ApplicationContextAware} 兜底路径均走此入口）。
     *
     * <p>幂等设计：传入 {@code null} 不会清空已有引用，保证后到的异常回调
     * 不会覆盖已生效的上下文；存在 Spring Cloud bootstrap 上下文时，
     * 后到的主上下文会覆盖 bootstrap 上下文（last-wins）。</p>
     *
     * @param context Spring 容器，为空时忽略
     */
    public static void initContext(ApplicationContext context) {
        if (context == null) {
            return;
        }
        boolean firstInit = applicationContext == null;
        applicationContext = context;
        // 容器自带的 Environment 比 EnvironmentPostProcessor 早期存入的引用更权威：
        // Spring Cloud bootstrap 场景下两者是两个不同对象，bootstrap 在前、主上下文在后，
        // 这里始终以后到（最后初始化）容器的 Environment 覆盖，避免持有 bootstrap 的残留引用。
        // getEnvironment() 在刷新前即可用，不会触发任何 Bean 创建。
        if (context instanceof ConfigurableApplicationContext) {
            Environment contextEnv = ((ConfigurableApplicationContext) context).getEnvironment();
            if (contextEnv != null) {
                environment = contextEnv;
            }
        }
        if (firstInit) {
            log.info("[cloud-auth-client-oauth] SpringContextUtil 已持有 applicationContext={}", context.getId());
        }
    }

    /**
     * 持有者是否已就绪（供启动自检使用，不抛异常）
     *
     * @return {@code true} 表示至少已持有 Environment 或 ApplicationContext
     */
    public static boolean isInitialized() {
        return environment != null || applicationContext != null;
    }

    /**
     * 获取 Environment：优先用提前存入的引用，回退到容器获取；容器未初始化时返回 null
     */
    public static Environment getEnvironment() {
        Environment env = environment;
        if (env != null) {
            return env;
        }
        ApplicationContext context = applicationContext;
        if (context == null) {
            return null;
        }
        return context.getEnvironment();
    }

    /**
     * 按类型获取 Bean。
     * <p>容器未初始化或 Bean 不存在时返回 null（不抛异常），
     * 调用方应自行处理 null 场景（如缓存降级）。</p>
     *
     * @param requiredType Bean 类型
     * @param <T>          泛型
     * @return Bean 实例；不存在或容器未就绪时返回 null
     */
    public static <T> T getBean(Class<T> requiredType) {
        ApplicationContext context = applicationContext;
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(requiredType);
        } catch (BeansException e) {
            // 业务未启用对应 Bean（如 StringRedisTemplate 未装配），返回 null 让调用方降级
            return null;
        }
    }
}