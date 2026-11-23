package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送短信验证码请求（重置密码场景，字段与认证中心 {@code SendSmsBySetBody} 对齐）。
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudSendSmsByResetBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名 */
    private String username;

    /** 旧密码 */
    private String oldPassword;

    /** 新密码（发送前会校验强度，太简单会拒绝） */
    private String newPassword;

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

}
