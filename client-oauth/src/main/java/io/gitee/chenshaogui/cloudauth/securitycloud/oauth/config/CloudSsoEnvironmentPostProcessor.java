package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config;

import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.SpringContextUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * [Sa-Token 权限认证] 环境默认值处理器。
 *
 * <p>以「最低优先级」向 Environment 注入若干默认配置，用于让接入本 SDK 的业务系统
 * 在<b>零配置</b>的情况下与认证中心（cloud-auth-platform）行为对齐；业务方若在
 * 配置中心显式配置了同名项，<b>一律以其配置为准</b>（{@code addLast} 优先级最低）。</p>
 *
 * <h3>默认值 1：{@code sa-token.dynamic-active-timeout=true}</h3>
 * <p>sa-token 只有在 {@code isOpenCheckActiveTimeout()} 为 true 时，才会在
 * {@code StpUtil.login()} 时写入 token 的 [最后活跃时间] 缓存 key
 * （{@code satoken:last-active-time:<token>}），该判断条件为：</p>
 * <pre>
 *     active-timeout != -1  ||  dynamic-active-timeout
 * </pre>
 * <p>客户端不开启、服务端开启的场景下：客户端登录时不写该 key，服务端读取同一
 * token 时 {@code getTokenLastActiveTime()} 返回 {@code -2 (NOT_VALUE_EXPIRE)}，
 * {@code isFreeze()} 据此判定 token「已被冻结」，最终抛出
 * {@code NotLoginException: token 已被冻结}。</p>
 *
 * <h3>默认值 2：{@code sa-token.jwt-secret-key=abcdefghijklmnopqrstuvwxyz}</h3>
 * <p>本 SDK 使用 {@code StpLogicJwtForSimple}（与认证中心一致），token 由 JWT 生成，
 * <b>密钥必须与认证中心完全相同</b>，否则客户端签发的 token 认证中心无法解析。
 * 认证中心该值配置于 {@code application-common.yml} 的
 * {@code sa-token.jwt-secret-key}，此处给出同一默认值以保证开箱可用。</p>
 * <p><b>安全提示</b>：该默认值仅为与当前认证中心对齐而提供，认证中心若更换密钥，
 * 业务方必须同步在配置中心配置 {@code sa-token.jwt-secret-key} 覆盖本默认值，否则
 * 登录态将无法被认证中心识别。</p>
 *
 * <h3>实现方式说明</h3>
 * <ul>
 *   <li>业务方<b>零配置</b>即可与认证中心行为对齐；</li>
 *   <li>业务方若在配置中心显式配置同名项，<b>以其配置为准</b>；</li>
 *   <li>与 {@code @ConfigurationProperties} 动态刷新兼容——属性源常驻，
 *       不会因 Nacos 刷新重新绑定而被重置为框架默认值。</li>
 * </ul>
 *
 * <h3>附带职责：提前交接 Environment 引用</h3>
 * <p>本处理器执行于容器刷新<b>之前</b>，是 SDK 能拿到 {@code Environment} 的最早时机，
 * 因此顺手把引用交给 {@link SpringContextUtil#initEnvironment}。这样
 * {@code CloudPlatformHelper.getValue} 等静态入口在所有 Bean 生命周期内都已可用，
 * 不再受“静态持有者所在 Bean 较晚初始化”的时序影响。交接的是<b>引用而非快照</b>：
 * 即使早于 {@code ConfigDataEnvironmentPostProcessor} 执行、此时
 * {@code application.yml} 尚未加载，后续追加的属性源仍对同一引用可见。</p>
 *
 * <p>注册位置：{@code META-INF/spring.factories} 的
 * {@code org.springframework.boot.env.EnvironmentPostProcessor}
 * （SpringBoot2 / SpringBoot3 均支持该注册方式）。</p>
 *
 * @author sgchen
 * @since 1.2.0
 */
public class CloudSsoEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /**
     * 属性源名称，保持唯一避免与业务方冲突
     */
    private static final String PROPERTY_SOURCE_NAME = "cloudauthSsoClientDefaults";

    /**
     * 默认开启动态 activeTimeout 的配置键
     */
    private static final String DYNAMIC_ACTIVE_TIMEOUT_KEY = "sa-token.dynamic-active-timeout";

    /**
     * JWT 密钥配置键（StpLogicJwtForSimple 生成/解析 token 时使用）
     */
    private static final String JWT_SECRET_KEY = "sa-token.jwt-secret-key";

    /**
     * JWT 默认密钥：与认证中心 cloud-auth-platform 的 application-common.yml 保持一致
     */
    private static final String DEFAULT_JWT_SECRET_KEY = "abcdefghijklmnopqrstuvwxyz";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 交接 Environment 引用（早于任何 Bean 初始化，消除静态读取的时序不确定性）
        SpringContextUtil.initEnvironment(environment);

        Map<String, Object> defaults = new HashMap<>(4);
        // 默认值 1：开启动态 activeTimeout，保证登录时写入 token 的最后活跃时间 key
        putDefaultIfAbsent(environment, defaults, DYNAMIC_ACTIVE_TIMEOUT_KEY, true);
        // 默认值 2：JWT 密钥，必须与认证中心一致，否则客户端 token 无法被认证中心解析
        putDefaultIfAbsent(environment, defaults, JWT_SECRET_KEY, DEFAULT_JWT_SECRET_KEY);

        if (!defaults.isEmpty()) {
            // addLast：优先级最低，业务方任意配置源（Nacos / application.yml）均可覆盖
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    /**
     * 仅当业务方未配置该键时，才写入默认值
     *
     * @param environment 当前环境
     * @param defaults    待注入的默认值集合
     * @param key         配置键
     * @param value       默认值
     */
    private void putDefaultIfAbsent(ConfigurableEnvironment environment, Map<String, Object> defaults,
                                    String key, Object value) {
        if (!environment.containsProperty(key)) {
            defaults.put(key, value);
        }
    }
}
