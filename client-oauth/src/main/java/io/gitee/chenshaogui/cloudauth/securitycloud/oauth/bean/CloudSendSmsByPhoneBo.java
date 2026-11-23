package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送短信验证码请求（仅手机号，用于手机号登录，字段与认证中心 {@code SendSmsByPhoneBody} 对齐）。
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudSendSmsByPhoneBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号 */
    private String phonenumber;

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

}
