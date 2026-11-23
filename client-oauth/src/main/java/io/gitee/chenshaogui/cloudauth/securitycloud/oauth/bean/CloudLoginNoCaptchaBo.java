package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 账号密码登录请求——免图形验证码（SDK 侧，字段与认证中心 {@code PasswordLoginBody} 对齐）。
 *
 * <p>与 {@link CloudLoginBo} 的区别：<b>刻意不含 {@code code}、{@code uuid}</b> 两个图形验证码字段，
 * 因为本入参对应认证中心 {@code loginByPasswordNoCaptcha} handler，服务端已明确不校验图形验证码。</p>
 *
 * <p>走 pushS 总线时由 {@code CloudPlatformHelper} 序列化为单个 {@code body} 参数，
 * 认证中心 handler 会把 {@code ignoreCaptcha} 强制置为 {@code true}（客户端无法通过 body 篡改）后
 * 转交 {@code tokenController.login} 校验。</p>
 *
 * @author sgchen
 * @since 1.3.4
 */
@Data
public class CloudLoginNoCaptchaBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权类型，账号密码登录固定为 {@code "password"} */
    private String grantType = "password";

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

    /** 用户名 */
    private String username;

    /** 用户密码 */
    private String password;

}
