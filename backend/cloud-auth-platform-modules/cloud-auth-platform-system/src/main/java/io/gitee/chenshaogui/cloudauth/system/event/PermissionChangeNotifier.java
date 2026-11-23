package io.gitee.chenshaogui.cloudauth.system.event;

import cn.hutool.core.collection.CollUtil;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.PermissionChangeMarkUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 权限变更事件监听器
 *
 * <p>把 {@link PermissionChangeEvent} 的受影响范围（用户 / 租户 / 全网）
 * 转换为 Redis 中的"变更时间戳"标记，具体 key 结构与读写协议见
 * {@link PermissionChangeMarkUtils}（由 auth 服务读取，必须共用同一套协议）。</p>
 *
 * <p><b>关于 {@code fallbackExecution = true}</b>：{@code @TransactionalEventListener}
 * 默认只在事务上下文中生效，而菜单 / 部门 / 岗位等增删改方法并未标注 {@code @Transactional}，
 * 若不加此开关，这些变更的事件会被<b>静默丢弃</b>（不报错、不生效），是极易踩的坑。</p>
 *
 * @author sgchen
 */
@Slf4j
@Component
public class PermissionChangeNotifier {

    /**
     * 全网广播 key（保持对外兼容，实际定义在 {@link PermissionChangeMarkUtils}）
     */
    public static final String MARK_ALL_KEY = PermissionChangeMarkUtils.ALL_KEY;

    /**
     * 仅事务成功提交后执行；{@code fallbackExecution=true} 保证无事务方法也能触发
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPermissionChange(PermissionChangeEvent event) {
        if (event == null) {
            return;
        }

        // 路径 1：用户与租户维度均为空 → 全网广播
        if (event.isBroadcast()) {
            PermissionChangeMarkUtils.markAll();
            log.info("[permission-sync] 权限变更事件（全网广播）：type={}", event.getChangeType());
            return;
        }

        int marked = 0;

        // 路径 2：精确用户
        if (CollUtil.isNotEmpty(event.getAffectedUserIds())) {
            Set<Long> userIds = new LinkedHashSet<>(event.getAffectedUserIds());
            for (Long userId : userIds) {
                if (userId == null) {
                    continue;
                }
                PermissionChangeMarkUtils.markUser(userId);
                marked++;
            }
        }

        // 路径 3：租户维度
        if (CollUtil.isNotEmpty(event.getAffectedTenantIds())) {
            Set<String> tenantIds = new LinkedHashSet<>(event.getAffectedTenantIds());
            for (String tenantId : tenantIds) {
                if (!StringUtils.hasText(tenantId)) {
                    continue;
                }
                PermissionChangeMarkUtils.markTenant(tenantId);
                marked++;
            }
        }

        log.info("[permission-sync] 权限变更事件已通知: type={}, 用户维度={}, 租户维度={}, 写入标记={}",
                event.getChangeType(), event.getAffectedUserIds().size(),
                event.getAffectedTenantIds().size(), marked);
    }

    /**
     * 精确用户标记 key
     */
    public static String buildKey(Long userId) {
        return PermissionChangeMarkUtils.buildUserKey(userId);
    }

    /**
     * 全网广播：给所有用户打权限变更标记
     */
    public static void markAllChanged() {
        PermissionChangeMarkUtils.markAll();
    }

    /**
     * 手动为指定用户打标记（非事件场景，如定时任务扫描脏数据后补发）
     */
    public static void markChanged(Long userId) {
        PermissionChangeMarkUtils.markUser(userId);
    }

    /**
     * 手动为指定租户打标记
     */
    public static void markTenantChanged(String tenantId) {
        PermissionChangeMarkUtils.markTenant(tenantId);
    }
}
