package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码登录对象
 *
 * @author Lion Li
 */
@Data
public class SendSmsBody {

    @NotBlank(message = "{user.username.not.blank}")
    private String username;

    @NotBlank(message = "{user.password.not.blank}")
    private String password;

    @NotBlank(message = "{user.tenantId.not.blank}")
    private String tenantId;

}
