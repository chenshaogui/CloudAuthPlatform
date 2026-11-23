package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.core;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.sso.SaSsoManager;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

/**
 * 归属客户端上下文（1.3.9 新增）。
 *
 * <p><b>要解决的问题</b>：{@code sa-token.sso-client.client} 是<b>服务级</b>配置——一个子系统启动后
 * 只认一个客户端；而一次请求携带的 token 属于<b>请求级</b>属性。当一个客户端（如 app 端）签发的
 * token 打到另一个客户端的服务（如继承 application-common.yml 的 infra / pc）时，两者对
 * "我是谁"的理解不一致，会引发两类故障：</p>
 * <ol>
 *   <li>权限同步用本服务的 client 去授权中心查会话，查不到 → 401 → SDK 触发本地登出，
 *       把<b>共享 Redis 里的整个登录态</b>摧毁（表现为"调一次别的模块，之后再调哪里都 401"）；</li>
 *   <li>角色剪裁用本服务的 client 剥 {@code {clientId}-} 前缀，外来 token 被剥空 → 403。</li>
 * </ol>
 *
 * <p><b>归属信息来源链</b>（按可信度排序）：</p>
 * <ol>
 *   <li>token extra {@value #TOKEN_EXTRA_CLIENT_KEY}：登录时由
 *       {@code CloudPlatformHelper#doLogin} 写入 JWT payload，受 JWT 签名保护，
 *       跨服务可读（{@code StpLogicJwtForSimple.getExtra} 只解签不查 Redis）；</li>
 *   <li>请求头 {@value #HEADER_SSO_CLIENT}：仅当 {@code sa-token.cross-client.trust-header=true}
 *       时参与归属判定，用于 RPC 链路透传与升级前老 token 的兼容；</li>
 *   <li>都取不到返回 {@code null}，表示"归属未知"，调用方按本服务配置处理（等价于 1.3.8 行为）。</li>
 * </ol>
 *
 * <p><b>为什么默认不信 header</b>：header 由调用方自由构造，若默认采信，任何人都能声明
 * "我属于别人的客户端"从而跳过远程登录态校验，等于把跨端放行变成可伪造的越权入口。
 * 默认只信 JWT extra（服务端签发、签名保护）。</p>
 *
 * <p><b>事实判定与开关解耦</b>：{@link #hasForeignOwner()} 是客观事实，不受
 * {@code cross-client.enabled} 影响；开关只决定外来票是 skip 还是 reject。
 * "绝不因远程查不到就登出外来 token"这条防御始终生效。</p>
 *
 * @author cloudauth
 * @since 1.3.9
 */
@Slf4j
public class SsoClientContext {

    /** token extra 键：本 token 的归属客户端 ID（登录时写入 JWT payload） */
    public static final String TOKEN_EXTRA_CLIENT_KEY = "ssoClient";

    /** 请求头名：跨服务透传的归属客户端声明，仅在 trust-header=true 时参与归属判定 */
    public static final String HEADER_SSO_CLIENT = "X-Sso-Client";

    /** 配置键：是否开启跨客户端适配，默认 true（外来票放行并跳过需对方密钥的远程校验）；置 false 回滚到 1.3.8 行为 */
    public static final String CONFIG_ENABLED = "sa-token.cross-client.enabled";

    /** 配置键：外来 token 策略，skip（默认）/ reject */
    public static final String CONFIG_MODE = "sa-token.cross-client.mode";

    /** 配置键：是否允许请求头参与归属判定，默认 false（只信 JWT extra） */
    public static final String CONFIG_TRUST_HEADER = "sa-token.cross-client.trust-header";

    /** 策略：外来 token 放行，但跳过依赖对方客户端密钥的远程登录态校验 */
    public static final String MODE_SKIP = "skip";

    /** 策略：外来 token 直接判为未登录（401） */
    public static final String MODE_REJECT = "reject";

    private SsoClientContext() {
    }

    // ================== 本服务身份 ==================

    /**
     * 本服务配置的客户端 ID（{@code sa-token.sso-client.client}），取不到返回 {@code null}。
     * <p>only-login 模式（未配置 SSO 客户端）不抛异常，静默降级。</p>
     */
    public static String primaryClient() {
        try {
            SaSsoClientConfig config = SaSsoManager.getClientConfig();
            if (config == null) {
                return null;
            }
            String client = config.getClient();
            return CharSequenceUtil.isBlank(client) ? null : client;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ================== token 归属解析 ==================

    /**
     * 从当前 token 的 JWT extra 读取归属客户端（最可信来源，不查 Redis、不要求登录态）。
     *
     * @return 归属客户端 ID；未登录 / 非 JWT 模式 / 老 token 无该 extra 时返回 {@code null}
     */
    public static String tokenOwnerClient() {
        return readExtra(TOKEN_EXTRA_CLIENT_KEY);
    }

    /**
     * 从请求头 {@value #HEADER_SSO_CLIENT} 读取归属客户端声明（非请求上下文静默返回 null）。
     */
    public static String headerClient() {
        try {
            SaRequest request = SaHolder.getRequest();
            if (request == null) {
                return null;
            }
            String header = request.getHeader(HEADER_SSO_CLIENT);
            return CharSequenceUtil.isBlank(header) ? null : header.trim();
        } catch (Exception ignored) {
            // 非请求上下文（异步线程 / 定时任务）SaHolder 不可用
            return null;
        }
    }

    /**
     * 当前请求 token 的归属客户端（综合判定）：JWT extra → （开关允许的）请求头 → null。
     */
    public static String currentOwnerClient() {
        String owner = tokenOwnerClient();
        if (CharSequenceUtil.isNotBlank(owner)) {
            return owner;
        }
        if (isTrustHeader()) {
            return headerClient();
        }
        return null;
    }

    /**
     * 当前 token 是否明确归属于<b>其他</b>客户端（外来票）。
     *
     * <p><b>事实判定，不受任何开关影响。</b>归属未知时返回 {@code false}——
     * 信息不足不误判，等价于改造前行为，避免把正常请求拦在门外。</p>
     */
    public static boolean hasForeignOwner() {
        String primary = primaryClient();
        if (CharSequenceUtil.isBlank(primary)) {
            return false;
        }
        String owner = currentOwnerClient();
        return CharSequenceUtil.isNotBlank(owner) && !owner.equals(primary);
    }

    /**
     * 缓存 key 用的归属标签：优先归属客户端，未知时回退本服务客户端，都为空时 {@code "none"}。
     * <p>保证"同一 loginId 在不同授权客户端下"的登录态类远程结果互不串味。</p>
     */
    public static String cacheOwnerLabel() {
        String owner = currentOwnerClient();
        if (CharSequenceUtil.isBlank(owner)) {
            owner = primaryClient();
        }
        return CharSequenceUtil.isBlank(owner) ? "none" : owner;
    }

    // ================== 策略开关 ==================

    /** 是否开启跨客户端适配（{@code sa-token.cross-client.enabled}，默认 true） */
    public static boolean isCrossClientEnabled() {
        return BooleanUtil.toBoolean(getValue(CONFIG_ENABLED, "true"));
    }

    /** 外来 token 处理策略（{@code sa-token.cross-client.mode}，默认 skip；非法值按 skip 兜底） */
    public static String getCrossClientMode() {
        String mode = getValue(CONFIG_MODE, MODE_SKIP);
        if (CharSequenceUtil.isBlank(mode)) {
            return MODE_SKIP;
        }
        mode = mode.trim().toLowerCase();
        return MODE_REJECT.equals(mode) ? MODE_REJECT : MODE_SKIP;
    }

    /** 是否拒绝外来 token（等价于未登录） */
    public static boolean isRejectForeign() {
        return isCrossClientEnabled() && MODE_REJECT.equals(getCrossClientMode());
    }

    /** 是否跳过外来 token 的远程登录态校验 */
    public static boolean isSkipForeign() {
        return isCrossClientEnabled() && MODE_SKIP.equals(getCrossClientMode());
    }

    /** 是否允许请求头参与归属判定（默认 false） */
    public static boolean isTrustHeader() {
        return BooleanUtil.toBoolean(getValue(CONFIG_TRUST_HEADER, "false"));
    }

    // ================== 内部工具 ==================

    /** 读取当前 token 的 JWT extra（任何异常静默返回 null） */
    private static String readExtra(String key) {
        try {
            Object value = StpUtil.getExtra(key);
            if (value == null) {
                return null;
            }
            String s = String.valueOf(value);
            return (CharSequenceUtil.isBlank(s) || "null".equals(s)) ? null : s.trim();
        } catch (Exception e) {
            // 非 JWT 模式 / 无 token / payload 解析失败：归属未知
            log.debug("[sso-client] 读取 token extra[{}] 失败: {}", key, e.getMessage());
            return null;
        }
    }

    /** 读取配置（容器未初始化时返回默认值），支持 Nacos 热更新 */
    private static String getValue(String key, String defaultValue) {
        try {
            Environment env = SpringContextUtil.getEnvironment();
            if (env == null) {
                return defaultValue;
            }
            return env.getProperty(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
