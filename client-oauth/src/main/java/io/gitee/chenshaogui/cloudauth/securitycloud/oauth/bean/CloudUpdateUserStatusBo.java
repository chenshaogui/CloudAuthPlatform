package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 启用/停用其他用户入参（走总线调认证中心 {@code updateUserStatus}）。
 *
 * <p>仅承载目标用户ID与状态码；操作人、租户、客户端均由 SDK/服务端从登录态派生，不在此透传。</p>
 *
 * @since 1.5.7
 */
@Data
public class CloudUpdateUserStatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标用户ID；与 {@link #userName} 二选一，优先 {@code userId}。
     */
    private Long userId;

    /**
     * 目标用户账号（userName）；与 {@link #userId} 二选一。
     * 服务端在<b>操作人会话所属租户</b>内解析账号对应的 userId，跨租户 / 不存在的账号解析不到，返回“用户不存在或无权限”。
     */
    private String userName;

    /**
     * 账号状态（0正常 / 1停用）
     */
    private String status;

}
