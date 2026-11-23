package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 删除用户入参（走总线调认证中心 {@code deleteUser}）。
 *
 * <p>携带目标用户（userId / userName 二选一）与待解除的角色ID（roleId）：
 * 服务端按“角色解绑 / 删用户”规则处理——用户仅绑定该唯一角色时删除用户，否则仅解除角色绑定。
 * 操作人、租户、客户端均由 SDK/服务端从登录态派生，不在此透传。</p>
 *
 * @since 1.5.7
 */
@Data
public class CloudDeleteUserBo implements Serializable {

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
     * 待解除绑定的角色ID（必填）。决定“解绑角色还是删用户”的判定维度。
     */
    private Long roleId;

}
