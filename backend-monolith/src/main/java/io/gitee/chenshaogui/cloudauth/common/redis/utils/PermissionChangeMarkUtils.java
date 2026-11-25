package io.gitee.chenshaogui.cloudauth.common.redis.utils;

import cn.hutool.core.convert.Convert;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheConstants;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * 权限变更标记工具（权限同步机制的统一协议定义）
 *
 * <p><b>为什么需要这个类</b>：标记由 system 服务写入、由 auth 服务读取，
 * 若两边各自拼装 key，极易出现"写入 key ≠ 读取 key"导致同步静默失效。
 * 因此 key 结构、TTL、读写方法统一收敛在这里。</p>
 *
 * <p><b>数据模型</b>：每个标记的值是"变更时间戳（毫秒）"，而不是简单的存在性布尔值。
 * 客户端上报自己上次已处理的时间戳，服务端返回最新时间戳，客户端据此判断是否需要刷新。
 * 这样设计解决了三个问题：
 * <ol>
 *   <li>服务端不需要删除标记 → 客户端刷新失败也不会丢通知（TTL 120s 内可重试）</li>
 *   <li>客户端按时间戳去重 → 全网广播不会造成 refresh 风暴</li>
 *   <li>标记不会被外部请求删除 → 无法伪造 checkPermission 实施拒绝服务</li>
 * </ol>
 *
 * <p><b>关键坑</b>：{@code LoginUser.getLoginId()} 是 {@code userType:userId}（例如
 * {@code sys_user:1001}），与数据库的 {@code userId}（1001）不是同一个值。
 * 本工具统一以 {@code userId} 作为用户维度 key，客户端 SDK 必须上报 {@code userId}。</p>
 *
 * @author sgchen
 */
@Slf4j
public class PermissionChangeMarkUtils {

    private PermissionChangeMarkUtils() {
    }

    /**
     * 标记 TTL：远大于客户端检查周期，保证客户端刷新失败后仍能在窗口内重试
     */
    public static final Duration MARK_TTL = Duration.ofSeconds(120L);

    /**
     * 精确用户标记前缀：{@code perm_changed:u:{userId}}
     */
    public static final String USER_KEY_PREFIX = CacheConstants.PERMISSION_CHANGED_KEY_PREFIX + "u:";

    /**
     * 租户维度标记前缀：{@code perm_changed:t:{tenantId}}
     */
    public static final String TENANT_KEY_PREFIX = CacheConstants.PERMISSION_CHANGED_KEY_PREFIX + "t:";

    /**
     * 全网广播 key：菜单等影响范围广的变更使用
     * <p>用 {@code _ALL_} 而非 {@code ALL}，避免与真实 userId / tenantId 撞车</p>
     */
    public static final String ALL_KEY = CacheConstants.PERMISSION_CHANGED_KEY_PREFIX + "_ALL_";

    /**
     * 用户维度标记 key
     */
    public static String buildUserKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    /**
     * 租户维度标记 key
     */
    public static String buildTenantKey(String tenantId) {
        return TENANT_KEY_PREFIX + tenantId;
    }

    /**
     * 标记指定用户权限变更
     */
    public static void markUser(Long userId) {
        if (userId == null) {
            return;
        }
        writeMark(buildUserKey(userId));
    }

    /**
     * 标记指定租户下所有用户权限变更
     */
    public static void markTenant(String tenantId) {
        if (!hasText(tenantId)) {
            return;
        }
        writeMark(buildTenantKey(tenantId));
    }

    /**
     * 全网广播：标记所有用户权限变更
     */
    public static void markAll() {
        writeMark(ALL_KEY);
    }

    /**
     * 写入标记（值为当前时间戳）
     *
     * @return 写入成功返回 true
     */
    public static boolean writeMark(String key) {
        try {
            RedisUtils.setCacheObject(key, System.currentTimeMillis(), MARK_TTL);
            return true;
        } catch (Exception e) {
            log.warn("[permission-sync] 写入权限变更标记失败 key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 解析指定用户当前生效的"最新变更时间戳"
     *
     * <p>取用户维度、租户维度、全网广播三类标记中的最大时间戳；
     * 全部不存在时返回 0，表示无变更</p>
     *
     * @param userId   用户ID（精确维度）
     * @param tenantId 租户ID（租户维度，可为空）
     * @return 最新变更时间戳（毫秒），无变更返回 0
     */
    public static long resolveChangeTime(Long userId, String tenantId) {
        long max = 0L;
        if (userId != null) {
            max = Math.max(max, readChangeTime(buildUserKey(userId)));
        }
        if (hasText(tenantId)) {
            max = Math.max(max, readChangeTime(buildTenantKey(tenantId)));
        }
        return Math.max(max, readChangeTime(ALL_KEY));
    }

    /**
     * 读取单个标记中的时间戳；不存在或异常返回 0
     */
    public static long readChangeTime(String key) {
        try {
            Object value = RedisUtils.getCacheObject(key);
            if (value == null) {
                return 0L;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Convert.toLong(value, 0L);
        } catch (Exception e) {
            log.warn("[permission-sync] 读取权限变更标记失败 key={}, err={}", key, e.getMessage());
            return 0L;
        }
    }

    private static boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
}
