package io.gitee.chenshaogui.cloudauth.auth.util;

import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheConstants;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * 短信发送限流守卫（Redis，按手机号维度）。
 *
 * <p>与框架的 {@code @RateLimiter} 注解不同：本守卫只在【短信实际发送成功后】才写入冷却锁，
 * 发送失败不占用额度、用户可立即重试，避免出现“验证码没发出去却被锁 60 秒”的问题。</p>
 *
 * @author sgchen
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SmsSendGuard {

    /** 冷却时间（秒）：同一号码发送成功后需等待的时间 */
    public static final int COOLDOWN_SECONDS = 60;

    /**
     * 发送前检查：是否处于冷却期。
     *
     * <p>本方法<b>只读不写</b>，不会消耗额度；额度在发送成功后由 {@link #markSent(String)} 记入。</p>
     *
     * @param target 限流维度（一般是手机号）
     * @return 允许发送返回 {@code null}；否则返回可直接展示给前端的失败原因
     */
    public static String checkBeforeSend(String target) {
        String limitKey = CacheConstants.SMS_SEND_LIMIT_KEY + target;
        if (RedisUtils.hasKey(limitKey)) {
            long ttl = RedisUtils.getTimeToLive(limitKey);
            long sec = ttl > 0 ? (ttl + 999) / 1000 : COOLDOWN_SECONDS;
            return "验证码发送过于频繁，请 " + sec + " 秒后再试";
        }
        return null;
    }

    /**
     * 发送成功后调用：写入冷却锁。
     *
     * <p>发送失败时<b>不要</b>调用本方法，以保留用户立即重试的能力。</p>
     *
     * @param target 限流维度（一般是手机号）
     */
    public static void markSent(String target) {
        RedisUtils.setCacheObject(CacheConstants.SMS_SEND_LIMIT_KEY + target, 1, Duration.ofSeconds(COOLDOWN_SECONDS));
    }
}
