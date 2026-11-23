package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.util.SaResult;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudOperLogVo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 子系统操作日志缓冲上报器（防大量写入的核心）
 *
 * <p><b>设计目标</b>：多个业务系统接入后，操作日志天然是高频数据。本组件通过
 * 四层手段把写入放大压到常数级，保证业务线程零阻塞、授权中心写入量可控：</p>
 *
 * <ol>
 *   <li><b>调用线程 O(1)</b>：{@link #record(CloudOperLogVo)} 仅一次有界队列
 *       {@code offer}，无网络、无序列化，任何异常静默吞掉，绝不影响业务请求。</li>
 *   <li><b>单条语义 + 有界队列</b>：每条日志独立成行（不聚合合并，保留审计明细），
 *       队列容量上限（默认 10000），满则丢弃新日志并限频 warn（含累计丢弃数），
 *       绝不 OOM、绝不反压业务线程。</li>
 *   <li><b>批量异步推送</b>：单守护线程按固定周期（默认 5s）把队列整批取出，按
 *       {@code max-batch-size}（默认 500）分片，一次 SSO 消息携带一批——网络往返次数 =
 *       ⌈条数/批大小⌉，与调用次数无关。</li>
 *   <li><b>服务端兜底</b>：单批条数上限 1000 + 异步落库。</li>
 * </ol>
 *
 * <p><b>失败降级</b>：推送失败原地重试 1 次，仍失败则丢弃该批并 warn（日志上报允许丢失，
 * 不重试风暴）；停机时 shutdown hook 强制 flush 一次。</p>
 *
 * <p><b>落库</b>：服务端 {@code batchOperLog} 消息处理器将上报数据转换为
 * {@code RemoteOperLogBo}，复用已有 {@code RemoteLogService.saveLog} 链路写入
 * {@code sys_oper_log} 表。</p>
 *
 * <p><b>配置</b>（{@code application.yml}，均有默认值）：</p>
 * <pre>{@code
 * sa-token:
 *   oper-log:
 *     enabled: true            # 总开关，false 时 record 直接空操作
 *     flush-interval-ms: 5000  # 刷盘周期（毫秒），最小 1000
 *     queue-capacity: 10000    # 待推送队列容量（超出丢弃新日志）
 *     max-batch-size: 500      # 单次推送条数上限
 * }</pre>
 *
 * @author sgchen
 * @since 1.2.0
 */
@Slf4j
public final class CloudOperLogRecorder {

    /**
     * 单例（懒加载，首次 record 时启动调度线程）
     */
    private static volatile CloudOperLogRecorder instance;

    /**
     * 消息类型：批量操作日志（与服务端 SsoServerController 的 handler 对应）
     */
    public static final String MSG_TYPE = "batchOperLog";

    // ================== 配置（构造时一次性读取） ==================

    private final boolean enabled;
    private final int flushIntervalMs;
    private final int queueCapacity;
    private final int maxBatchSize;

    // ================== 运行时状态 ==================

    /**
     * 待推送队列（有界 FIFO，满则丢弃新日志，绝不反压业务线程）
     */
    private final BlockingQueue<CloudOperLogVo> queue;

    /**
     * 刷盘调度线程（单线程守护）
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 累计丢弃条数（队列超限时递增，周期性 warn 汇报）
     */
    private final AtomicLong droppedCount = new AtomicLong();

    /**
     * 上次打印丢弃告警的时间戳（限制告警频率，避免日志本身刷屏）
     */
    private volatile long lastDropWarnAt = 0L;

    private CloudOperLogRecorder() {
        this.enabled = CloudPlatformHelper.getOperLogEnabled();
        this.flushIntervalMs = Math.max(1000,
            CloudPlatformHelper.getOperLogIntValue("sa-token.oper-log.flush-interval-ms", 5000));
        this.queueCapacity = Math.max(100,
            CloudPlatformHelper.getOperLogIntValue("sa-token.oper-log.queue-capacity", 10000));
        this.maxBatchSize = Math.max(50,
            CloudPlatformHelper.getOperLogIntValue("sa-token.oper-log.max-batch-size", 500));
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        if (enabled) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cloud-oper-log-flusher");
                t.setDaemon(true);
                return t;
            });
            this.scheduler.scheduleWithFixedDelay(this::flush,
                flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
            // 停机兜底：强制刷一次盘（尽力而为，守护线程可能被强杀，日志允许丢失）
            Runtime.getRuntime().addShutdownHook(new Thread(this::flushOnce, "cloud-oper-log-shutdown"));
            log.info("[cloud-oper-log] 操作日志上报已启动: flushInterval={}ms, queueCapacity={}, maxBatchSize={}",
                flushIntervalMs, queueCapacity, maxBatchSize);
        } else {
            this.scheduler = null;
        }
    }

    /**
     * 获取单例（双重检查锁）
     */
    static CloudOperLogRecorder getInstance() {
        CloudOperLogRecorder local = instance;
        if (local == null) {
            synchronized (CloudOperLogRecorder.class) {
                local = instance;
                if (local == null) {
                    instance = new CloudOperLogRecorder();
                    local = instance;
                }
            }
        }
        return local;
    }

    // ================== 对外入口 ==================

    /**
     * 记录一条操作日志（业务线程调用，O(1) 非阻塞，任何异常静默吞掉）
     *
     * @param vo 日志数据
     */
    public static void record(CloudOperLogVo vo) {
        try {
            if (vo == null) {
                return;
            }
            CloudOperLogRecorder recorder = getInstance();
            if (!recorder.enabled) {
                return;
            }
            recorder.doRecord(vo);
        } catch (Throwable t) {
            // 日志上报绝不影响业务链路
            log.debug("[cloud-oper-log] record 异常（已忽略）", t);
        }
    }

    /**
     * 立即刷盘（手动触发，一般不需要业务方调用）
     */
    public static void flushNow() {
        try {
            CloudOperLogRecorder recorder = getInstance();
            if (recorder.enabled) {
                recorder.flush();
            }
        } catch (Throwable t) {
            log.debug("[cloud-oper-log] flushNow 异常（已忽略）", t);
        }
    }

    // ================== 内部实现 ==================

    private void doRecord(CloudOperLogVo vo) {
        // 队列满直接丢弃（不反压业务线程）
        if (!queue.offer(vo)) {
            onDropped();
        }
    }

    /**
     * 丢弃计数 + 限频告警（1 分钟最多 1 条，避免告警自身刷屏）
     */
    private void onDropped() {
        long total = droppedCount.incrementAndGet();
        long now = System.currentTimeMillis();
        if (now - lastDropWarnAt > 60_000L) {
            lastDropWarnAt = now;
            log.warn("[cloud-oper-log] 操作日志队列已满(queueCapacity={})，累计丢弃 {} 条日志；"
                + "如属正常业务高峰可上调 sa-token.oper-log.queue-capacity", queueCapacity, total);
        }
    }

    /**
     * 刷盘：把队列整批移出并分批推送。
     * <p>{@code poll} 逐条取出的对象一定"成功移除才上报"，不丢数据不重复。</p>
     */
    private void flush() {
        if (queue.isEmpty()) {
            return;
        }
        List<CloudOperLogVo> batch = new ArrayList<>(Math.min(queue.size(), maxBatchSize));
        CloudOperLogVo vo;
        while ((vo = queue.poll()) != null) {
            batch.add(vo);
            if (batch.size() >= maxBatchSize) {
                pushBatch(new ArrayList<>(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            pushBatch(batch);
        }
    }

    /**
     * 供 shutdown hook 使用的包装（吞异常）
     */
    private void flushOnce() {
        try {
            flush();
        } catch (Throwable t) {
            log.debug("[cloud-oper-log] 停机刷盘异常（已忽略）", t);
        }
    }

    /**
     * 推送一批日志到授权中心（失败重试 1 次，仍失败丢弃该批）。
     * <p>所有异常吞掉：日志上报允许丢失、允许延迟，不允许影响任何调用方。</p>
     */
    private void pushBatch(List<CloudOperLogVo> batch) {
        try {
            String logsJson = JsonUtils.toJsonString(batch);
            SaSsoMessage message = new SaSsoMessage();
            message.setType(MSG_TYPE);
            message.set("logs", logsJson);
            SaResult result = CloudPlatformHelper.REMOTE_SSO_CLIENT_TEMPLATE
                .pushMessageAsSaResult(message, null);
            if (result == null || result.getCode() != SaResult.CODE_SUCCESS) {
                log.warn("[cloud-oper-log] 推送被服务端拒绝: {}", result == null ? "null" : result.getMsg());
            }
        } catch (Throwable t) {
            // 失败重试 1 次（同步间隔 1 秒，仅单刷盘线程阻塞，业务无感知）
            try {
                Thread.sleep(1000L);
                String logsJson = JsonUtils.toJsonString(batch);
                SaSsoMessage message = new SaSsoMessage();
                message.setType(MSG_TYPE);
                message.set("logs", logsJson);
                CloudPlatformHelper.REMOTE_SSO_CLIENT_TEMPLATE.pushMessageAsSaResult(message, null);
            } catch (Throwable retry) {
                log.warn("[cloud-oper-log] 批量推送失败（已丢弃 {} 条）: {}", batch.size(),
                    retry.getMessage());
            }
        }
    }
}
