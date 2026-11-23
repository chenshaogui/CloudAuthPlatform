package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config;

import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.Map;

/**
 * 权限同步自动配置
 *
 * <p>业务方引入本 SDK 后无需任何配置即可获得权限实时同步能力：SDK 在鉴权过滤器
 * （{@link CloudTokenClientConfigure}）中按节流周期检查服务端是否有权限变更，
 * 有变更时自动重载登录用户并触发
 * {@link CloudPlatformHelper#onUserInfoChanged(java.util.function.Consumer)} 监听器。</p>
 *
 * <p><b>为什么不是定时任务</b>：SDK 集成在业务系统中，sa-token 的登录态是"每请求"的。
 * 后台定时任务线程没有 SaTokenContext，{@code StpUtil.isLogin()} 会抛异常并导致
 * {@code scheduleWithFixedDelay} 永久取消任务，因此检查必须发生在请求线程内。</p>
 *
 * <p>可用配置：
 * <ul>
 *   <li>{@code sa-token.permission-sync.enabled=false}：关闭自动同步，
 *       此时需业务方在合适时机手动调用 {@link CloudPlatformHelper#checkPermissionChange()}</li>
 *   <li>{@code sa-token.cache.ttl-seconds}：检查间隔（默认 20 秒，最小 5 秒），
 *       与「权限相关档」接口的缓存 TTL 共用同一个值</li>
 * </ul>
 *
 * @author sgchen
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
@ConditionalOnProperty(prefix = "sa-token.permission-sync", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CloudPermissionSyncAutoConfigure {

    /**
     * 应用启动完成后输出生效配置，便于排查"配置未生效"类问题
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("[permission-sync] 权限同步已启用：由鉴权过滤器按请求链路节流检查，间隔 {} 秒",
                CloudPlatformHelper.getRemoteCacheTtlSeconds());
        // 档位摘要：业务方一眼看出各档生效 TTL 及对应配置键
        log.info("[cache] 远程调用结果缓存 TTL（秒）：{}",
                summarizeTtlMap(CloudPlatformHelper.getApiCacheTtlTiers()));
        // 明细：需要排查"某个接口到底缓存多久"时开 DEBUG
        if (log.isDebugEnabled()) {
            log.debug("[cache] 各接口内置 TTL 明细：{}",
                    summarizeTtlMap(CloudPlatformHelper.getDefaultApiCacheTtl()));
        }
    }

    /**
     * 输出 TTL 映射（仅展示用途）
     */
    private static String summarizeTtlMap(Map<String, Long> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append('=').append(e.getValue()).append("s");
            first = false;
        }
        return sb.append('}').toString();
    }
}
