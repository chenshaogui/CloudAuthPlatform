package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 忘记密码 / 重置密码请求（提交手机短信验证码完成改密，字段与认证中心 {@code ResetPasswordBody} 对齐）。
 *
 * <p>典型流程：先调 {@code /sso/sendSmsCodeByPhone} 用手机号收取短信码，
 * 再调 {@code /sso/resetPassword} 提交本对象完成密码重置。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudResetPasswordBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号（收码的号码，用于定位用户） */
    private String phonenumber;

    /** 短信验证码（{@code sendSmsCodeByPhone} 下发到该手机号的码） */
    private String smsCode;

    /** 新密码 */
    private String newPassword;

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

}
