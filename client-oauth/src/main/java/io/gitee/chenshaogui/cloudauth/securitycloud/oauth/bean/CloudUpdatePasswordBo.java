package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码请求（<b>登录态</b>下凭旧密码改新密码，字段与认证中心 {@code UpdatePasswordBody} 对齐）。
 *
 * <p>与 {@link CloudResetPasswordBo}（忘记密码，走短信码）不同：本请求要求当前已登录，
 * 由 SDK 自动注入当前用户 {@code loginId}，前端只需传旧密码与新密码。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudUpdatePasswordBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 旧密码 */
    private String oldPassword;

    /** 新密码（服务端会校验强度） */
    private String newPassword;

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

}
