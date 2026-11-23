package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config;

import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.cache.SharedRemoteCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 共享远程调用结果缓存自动配置。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class CloudCacheAutoConfigure {

    @Bean
    public SharedRemoteCache sharedRemoteCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            log.info("[cache] 未检测到 StringRedisTemplate Bean，远程调用结果缓存降级为不缓存模式");
        } else {
            log.info("[cache] 共享远程调用结果缓存已启用：Redis key 前缀 '{}'", SharedRemoteCache.KEY_PREFIX);
        }
        return new SharedRemoteCache(template);
    }
}