package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 账号密码登录请求（SDK 侧，字段与认证中心 {@code PasswordLoginBody} 对齐）。
 *
 * <p>走 pushS 总线时由 {@code CloudPlatformHelper} 序列化为单个 {@code body} 参数，
 * 认证中心 handler 原样转交 {@code tokenController.login} 校验。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudLoginBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权类型，账号密码登录固定为 {@code "password"} */
    private String grantType = "password";

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

    /** 用户名 */
    private String username;

    /** 用户密码 */
    private String password;

    /** 图形验证码（认证中心开启验证码时必填，配合 {@link CloudCaptchaVo#getUuid()}） */
    private String code;

    /** 验证码唯一标识（来自 getCaptcha()） */
    private String uuid;

}
