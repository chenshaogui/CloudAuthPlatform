package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 按手机号发送短信验证码对象（免密码）
 *
 * <p>用于"手机号 + 验证码"登录场景：用户仅输入手机号即可获取验证码，
 * 不要求输入账号密码。</p>
 *
 * @author CodeBuddy
 */
@Data
public class SendSmsByPhoneBody {

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
