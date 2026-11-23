package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送短信验证码请求（注册场景，字段与认证中心 {@code SendSmsForRegisterBody} 对齐）。
 *
 * <p>与 {@link CloudSendSmsByPhoneBo} 的区别：本请求面向<b>尚未注册</b>的手机号，
 * 服务端不应校验手机号是否已存在，只负责下发注册验证码；配合 {@code /sso/register} 使用。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudSendSmsForRegisterBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号（待注册的号码） */
    private String phonenumber;

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

}
