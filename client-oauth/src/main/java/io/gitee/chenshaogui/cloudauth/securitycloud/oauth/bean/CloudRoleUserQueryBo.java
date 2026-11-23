package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色下用户分页查询条件
 *
 * <p>用于 {@code CloudPlatformHelper.getRoleUserPage(bo)} 的入参，
 * 按 roleId 或 roleKey（二选一，roleId 优先）定位角色，分页返回其关联用户列表。</p>
 *
 * @author sgchen
 * @since 1.5.6
 */
@Data
public class CloudRoleUserQueryBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID（与 roleKey 二选一，roleId 优先）
     */
    private Long roleId;

    /**
     * 角色权限标识（与 roleId 二选一；支持短标识或完整 {clientId}-{标识}）
     */
    private String roleKey;

    /**
     * 当前页（从 1 开始，默认 1）
     */
    private Integer pageNum;

    /**
     * 每页大小（默认 10）
     */
    private Integer pageSize;

    /**
     * 构建缓存 key 后缀（roleId/roleKey + 分页参数组合唯一标识一次查询）
     */
    public String toCacheKeySuffix() {
        StringBuilder sb = new StringBuilder();
        sb.append(roleId == null ? "" : roleId)
                .append('|').append(roleKey == null ? "" : roleKey)
                .append('|').append(pageNum == null ? "" : pageNum)
                .append('|').append(pageSize == null ? "" : pageSize);
        return sb.toString();
    }

}
