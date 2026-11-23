package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 共享远程调用结果缓存。
 *
 * <p><b>设计特点</b></p>
 * <ul>
 *   <li><b>写入开销 O(1)</b>：直接写 Redis 单 key，不触碰 SaSession 全量 dataMap 序列化。</li>
 *   <li><b>内存可控</b>：靠 Redis TTL 自动失效，不在 Session 中堆积过期条目。</li>
 *   <li><b>跨用户共享</b>：所有用户共享同一份 Redis key（如「同 deptId」只存一份）。</li>
 *   <li><b>防击穿</b>：TTL 到期点同秒内 N 个并发请求全部 miss 时，通过 <b>JVM 内单飞</b>
 *       （per-key {@link ReentrantLock}）合并为 1 次远程调用。</li>
 * </ul>
 *
 * <p><b>降级策略</b>：当 {@link StringRedisTemplate} 不存在（业务未集成 Redis）时，
 * {@link #isAvailable()} 返回 false，调用方应直接执行 loader，不要走缓存路径。</p>
 *
 * <p><b>线程模型</b>：loader 在加锁线程同步执行（不是 {@code ForkJoinPool}），
 * 保留当前请求线程的 ThreadLocal 上下文（如 sa-token / browserId 等），
 * 避免 SSO 鉴权所需 header 丢失。</p>
 *
 * @author sgchen
 */
@Slf4j
public class SharedRemoteCache {

    /** 共享缓存 Redis key 前缀（全局唯一，便于一键清理整个缓存命名空间，见 {@link #clearAll()}）。
     *  <p>前缀与 Maven artifactId（cloud-auth-client-oauth）一一对应：
     *  Redis 中看到该前缀的 key 即可定位到本 SDK，与平台级 "cloudauth:cloud:" 命名、
     *  服务端 cloud-auth-platform-auth 及业务方自定义 key 区分，避免共用 Redis 时撞名。</p> */
    public static final String KEY_PREFIX = "cloudauth:security-client-oauth:cache:";

    /** 清空缓存时的 SCAN 分批大小：兼顾 JVM 内存与 Redis 往返次数 */
    private static final int SCAN_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;

    /**
     * per-key 单飞锁。
     * <p>用后即删，map 不会无限增长；下次同 key miss 时重新创建。</p>
     */
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public SharedRemoteCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 缓存是否可用（Redis 模板存在即视为可用，读写失败会在内部降级为不打缓存、不抛异常）
     */
    public boolean isAvailable() {
        return redisTemplate != null;
    }

    /**
     * 构造完整 Redis key。
     *
     * @param apiName  API 名称（建议与 {@code remoteCache(apiName, ...)} 一致）
     * @param inputKey 入参标识（建议与 {@code remoteCache(..., inputKey, ...)} 一致）
     * @return 形如 {@code "cloudauth:security-client-oauth:cache:<apiName>:<inputKey>"} 的 Redis key
     */
    public String buildKey(String apiName, String inputKey) {
        if (apiName == null || apiName.isEmpty() || inputKey == null || inputKey.isEmpty()) {
            throw new IllegalArgumentException("apiName/inputKey 不能为空: apiName=" + apiName + ", inputKey=" + inputKey);
        }
        return KEY_PREFIX + apiName + ":" + inputKey;
    }

    /**
     * 读取或加载 JSON 字符串（带单飞）。
     *
     * <p>典型用法：</p>
     * <pre>{@code
     * String json = cache.getOrLoadJson(fullKey, ttl, () -> {
     *     MyResult fresh = remoteCall();
     *     return fresh == null ? null : JsonUtils.toJsonString(fresh);
     * });
     * if (json == null) return null;
     * return JsonUtils.parseObject(json, MyResult.class);
     * }</pre>
     *
     * @param fullKey    完整 Redis key（推荐 {@link #buildKey} 构造）
     * @param ttlSeconds 缓存 TTL 秒数
     * @param jsonLoader 缓存未命中时调用，必须返回 JSON 字符串；返回 null 表示「无结果」（不写缓存）
     * @return 缓存命中或 loader 加载的 JSON 字符串；loader 返回 null 时也返回 null
     */
    public String getOrLoadJson(String fullKey, long ttlSeconds, Supplier<String> jsonLoader) {
        if (redisTemplate == null) {
            // 降级：不走缓存、不走单飞（同一 JVM 多次并发会各自去抖，依赖业务自行限制）
            return jsonLoader.get();
        }
        // 1. 快速路径：直接读 Redis
        String cached = safeGet(fullKey);
        if (cached != null) {
            if (log.isDebugEnabled()) {
                log.debug("[cache] HIT  {}", fullKey);
            }
            return cached;
        }
        // 2. 单飞：per-key 加锁，JVM 内并发请求合并为 1 次远程调用
        ReentrantLock lock = lockMap.computeIfAbsent(fullKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // 3. 双重检查：可能在加锁期间已有其他线程完成加载并写入 Redis
            cached = safeGet(fullKey);
            if (cached != null) {
                if (log.isDebugEnabled()) {
                    log.debug("[cache] HIT (after lock) {}", fullKey);
                }
                return cached;
            }
            // 4. 加载（请求线程同步执行，ThreadLocal 上下文保留）
            String fresh = jsonLoader.get();
            if (fresh != null) {
                safeSet(fullKey, fresh, ttlSeconds);
                if (log.isDebugEnabled()) {
                    log.debug("[cache] MISS -> loaded & set ttl={}s key={}", ttlSeconds, fullKey);
                }
            } else if (log.isDebugEnabled()) {
                log.debug("[cache] MISS -> loader 返回 null，不写入缓存 key={}", fullKey);
            }
            return fresh;
        } finally {
            lock.unlock();
            // 5. 用后即删：单飞已完成，无人再需要此锁对象
            //    即使有线程随后因 cache miss 又来 computeIfAbsent，也只是新建一个独立锁对象，
            //    与当前 unlock 后的旧锁无任何关系，逻辑安全。
            lockMap.remove(fullKey);
        }
    }

    /**
     * 批量失效 Redis key。删除失败仅打日志，不抛异常。
     *
     * @param fullKeys 完整 Redis key 列表
     */
    public void invalidate(String... fullKeys) {
        if (redisTemplate == null || fullKeys == null || fullKeys.length == 0) {
            return;
        }
        try {
            Long deleted = redisTemplate.delete(Arrays.asList(fullKeys));
            if (log.isDebugEnabled()) {
                log.debug("[cache] 失效 Redis key: {} -> 删除 {} 个", Arrays.asList(fullKeys), deleted);
            }
        } catch (Exception e) {
            log.debug("[cache] Redis DEL 失败: {}", e.getMessage());
        }
    }

    /**
     * 清空本缓存命名空间下的全部 key（{@link #KEY_PREFIX} 前缀）。
     *
     * <p>用 {@code SCAN} 游标分批删除（每批 {@link #SCAN_BATCH_SIZE} 个），
     * 避免 {@code KEYS} 命令在大 key 空间下阻塞 Redis。</p>
     *
     * <p>清理失败只打日志不抛异常：缓存清理属于旁路能力，不应影响调用方。</p>
     *
     * @return 实际删除的 key 数量；未集成 Redis（降级不缓存）时返回 0
     */
    public long clearAll() {
        if (redisTemplate == null) {
            return 0L;
        }
        long deleted = 0L;
        List<String> batch = new ArrayList<>(SCAN_BATCH_SIZE);
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(SCAN_BATCH_SIZE).build())) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH_SIZE) {
                    deleted += deleteBatch(batch);
                }
            }
            deleted += deleteBatch(batch);
        } catch (Exception e) {
            log.warn("[cache] 清空共享缓存失败已中断，已删除 {} 个 key: {}", deleted, e.getMessage());
        }
        return deleted;
    }

    /**
     * 批量删除并清空传入的 key 列表（列表会被清空以便复用）
     *
     * @return 本批实际删除的 key 数量
     */
    private long deleteBatch(List<String> keys) {
        if (keys.isEmpty()) {
            return 0L;
        }
        try {
            // redisTemplate.delete(Collection) 受 org.springframework.data.redis.core 包级
            // @NonNullApi 约束，契约上不会返回 null，无需判空
            return redisTemplate.delete(keys);
        } catch (Exception e) {
            log.debug("[cache] Redis DEL 批量失败: {}", e.getMessage());
            return 0L;
        } finally {
            keys.clear();
        }
    }

    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.debug("[cache] Redis GET 失败 {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void safeSet(String key, String value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[cache] Redis SET 失败 {}: {}", key, e.getMessage());
        }
    }
}