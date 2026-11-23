package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.gitee.chenshaogui.cloudauth.common.core.domain.model.LoginBody;

/**
 * 手机号 + 短信验证码登录对象（免密码）
 *
 * @author CodeBuddy
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PhoneLoginBody extends LoginBody {

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

}
