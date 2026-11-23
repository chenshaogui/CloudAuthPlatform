package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送短信验证码请求（用户名 + 密码校验维度，字段与认证中心 {@code SendSmsBody} 对齐）。
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudSendSmsBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名 */
    private String username;

    /** 用户密码（发送前先校验账号密码，防止任意枚举手机号） */
    private String password;

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

}
