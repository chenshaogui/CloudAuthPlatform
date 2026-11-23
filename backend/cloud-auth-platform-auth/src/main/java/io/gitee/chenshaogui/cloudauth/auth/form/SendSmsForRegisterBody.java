package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册场景按手机号发送短信验证码对象（面向尚未注册的号码，服务端不校验号码存在性）
 *
 * @author sgchen
 */
@Data
public class SendSmsForRegisterBody {

    /**
     * 手机号
     */
    @NotBlank(message = "{user.phonenumber.not.blank}")
    private String phonenumber;

    /**
     * 租户ID
     */
    @NotBlank(message = "{user.tenantId.not.blank}")
    private String tenantId;

    /**
     * 客户端ID（非必填，用于日志与后续按客户端做细粒度限制）
     */
    private String clientId;

}
