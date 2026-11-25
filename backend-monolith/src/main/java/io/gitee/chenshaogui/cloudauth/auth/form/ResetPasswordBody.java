package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘记密码 / 重置密码对象（手机号 + 短信验证码 + 新密码）
 *
 * <p>验证码由 sendSmsCodeByPhone 下发，存储 key = GlobalConstants.CAPTCHA_CODE_KEY + phonenumber，
 * 校验通过并改密成功后删除该 key，防止一码多用。</p>
 *
 * @author sgchen
 */
@Data
public class ResetPasswordBody {

    /**
     * 手机号
     */
    @NotBlank(message = "{user.phonenumber.not.blank}")
    private String phonenumber;

    /**
     * 短信验证码
     */
    @NotBlank(message = "{sms.code.not.blank}")
    private String smsCode;

    /**
     * 新密码
     */
    @NotBlank(message = "{user.newPassword.not.blank}")
    private String newPassword;

    /**
     * 租户ID
     */
    @NotBlank(message = "{user.tenantId.not.blank}")
    private String tenantId;

    /**
     * 客户端ID（用于校验该客户端是否开放重置密码）
     */
    private String clientId;

}
