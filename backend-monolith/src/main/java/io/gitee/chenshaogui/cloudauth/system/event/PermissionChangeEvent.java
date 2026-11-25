package io.gitee.chenshaogui.cloudauth.system.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.List;

/**
 * 权限变更事件（权限同步机制）
 *
 * <p>由 SysUser / SysRole / SysMenu / SysDept / SysPost / SysTenant 相关 Service
 * 在修改用户、角色、菜单、部门、岗位、租户套餐等数据后发布，
 * {@link PermissionChangeNotifier} 监听后将受影响范围（用户/租户/全网）的
 * "变更时间戳"写入 Redis，客户端 SDK 在下次检查时对比时间戳后自动重载登录用户</p>
 *
 * <p>影响范围优先级：
 * <ol>
 *   <li>{@code affectedUserIds} 非空 → 精确通知这些用户</li>
 *   <li>否则 {@code affectedTenantIds} 非空 → 通知这些租户下的全部用户</li>
 *   <li>两者都为空 → 全网广播</li>
 * </ol>
 *
 * <p>监听器使用 {@code afterCommit} + {@code fallbackExecution=true} 触发，
 * 保证事务提交后才发标记，同时兼容未开启事务的业务方法（如菜单/部门/岗位的增删改）</p>
 *
 * @author sgchen
 */
public class PermissionChangeEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 受影响的用户ID列表（精确通知范围）
     */
    @Getter
    private final List<Long> affectedUserIds;

    /**
     * 受影响的租户ID列表（租户级通知范围，如租户套餐同步、租户停用、租户信息变更）
     */
    @Getter
    private final List<String> affectedTenantIds;

    /**
     * 变更类型（用于日志/监控分类）
     */
    @Getter
    private final ChangeType changeType;

    public enum ChangeType {
        /** 新增角色/菜单 */
        INSERT,
        /** 修改角色/菜单/分配权限 */
        UPDATE,
        /** 删除角色/菜单/用户 */
        DELETE,
        /** 启用/禁用、分配/取消授权 */
        STATUS_CHANGE,
        /** 重置密码 */
        PASSWORD_RESET
    }

    /**
     * 按用户精确通知
     */
    public PermissionChangeEvent(Object source, List<Long> affectedUserIds, ChangeType changeType) {
        this(source, affectedUserIds, null, changeType);
    }

    /**
     * 完整构造：用户 + 租户
     *
     * @param affectedUserIds   受影响用户（为空表示不使用用户维度）
     * @param affectedTenantIds 受影响租户（为空表示不使用租户维度）
     * @param changeType        变更类型
     */
    public PermissionChangeEvent(Object source, List<Long> affectedUserIds,
                                 List<String> affectedTenantIds, ChangeType changeType) {
        super(source);
        this.affectedUserIds = affectedUserIds == null ? Collections.emptyList() : affectedUserIds;
        this.affectedTenantIds = affectedTenantIds == null ? Collections.emptyList() : affectedTenantIds;
        this.changeType = changeType == null ? ChangeType.UPDATE : changeType;
    }

    /**
     * 是否为全网广播（用户维度与租户维度均为空）
     */
    public boolean isBroadcast() {
        return affectedUserIds.isEmpty() && affectedTenantIds.isEmpty();
    }
}
