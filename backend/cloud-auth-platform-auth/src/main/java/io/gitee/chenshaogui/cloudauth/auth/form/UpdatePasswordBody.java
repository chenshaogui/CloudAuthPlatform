package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录态修改密码对象（旧密码 + 新密码）
 *
 * <p>身份来源为总线顶层 loginId（SDK 上报），本对象不携带 userId，服务端以 loginId 为准定位用户。</p>
 *
 * @author sgchen
 */
@Data
public class UpdatePasswordBody {

    /**
     * 旧密码
     */
    @NotBlank(message = "{user.oldPassword.not.blank}")
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "{user.newPassword.not.blank}")
    private String newPassword;

    /**
     * 租户ID（sys_user 为租户隔离表，改密需带租户维度）
     */
    @NotBlank(message = "{user.tenantId.not.blank}")
    private String tenantId;

}
