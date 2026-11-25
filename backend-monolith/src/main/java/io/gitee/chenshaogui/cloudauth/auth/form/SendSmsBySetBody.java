package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码登录对象
 *
 * @author Lion Li
 */
@Data
public class SendSmsBySetBody {

    @NotBlank(message = "{user.username.not.blank}")
    private String username;

    @NotBlank(message = "{user.oldPassword.not.blank}")
    private String oldPassword;

    @NotBlank(message = "{user.newPassword.not.blank}")
    private String newPassword;

    @NotBlank(message = "{user.tenantId.not.blank}")
    private String tenantId;

    /**
     * 客户端ID（用于校验该客户端是否开放重置密码）
     * <p>鉴权中心前端从 URL 的 ?client=xxx 带入；非必填，未传时跳过按 client 的细粒度校验</p>
     */
    private String clientId;

}
