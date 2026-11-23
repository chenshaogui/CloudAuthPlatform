package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 手机号 + 短信验证码登录请求（字段与认证中心 {@code PhoneLoginBody} 对齐）。
 *
 * <p>认证中心 {@code loginByPhone} 内部会复用 {@code sms} 授权策略，因此 {@link #grantType} 传 {@code "sms"}。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudPhoneLoginBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权类型，手机号登录固定为 {@code "sms"} */
    private String grantType = "sms";

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

    /** 手机号 */
    private String phonenumber;

    /** 短信验证码（由 sendSmsCodeByPhone 下发） */
    private String smsCode;

}
