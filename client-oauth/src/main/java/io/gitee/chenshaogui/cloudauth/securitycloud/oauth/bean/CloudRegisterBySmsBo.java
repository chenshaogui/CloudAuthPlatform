package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户注册请求 —— <b>手机号 + 短信验证码</b>场景（字段与认证中心 {@code RegisterBody} 对齐）。
 *
 * <p>认证中心 {@code register} 为单端点、按字段数据驱动：本 BO 填 {@code phonenumber} 且不含
 * {@code uuid}，服务端即以 {@code CAPTCHA_CODE_KEY + phonenumber} 校验 {@link #code}（短信码）。
 * <b>注意</b>：短信码放在 {@code code} 字段（与服务端 {@code RegisterBody} 一致），不是 {@code smsCode}。</p>
 *
 * <p>需先调 {@code /sso/sendSmsCodeForRegister}（面向未注册号码）用 {@code phonenumber} 收码，
 * 再把 6 位码回填 {@code code} 提交。若走图形验证码注册，请改用 {@link CloudRegisterByCaptchaBo}。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudRegisterBySmsBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权类型（注册一般为 {@code "password"}） */
    private String grantType = "password";

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

    /** 用户名 */
    private String username;

    /** 用户密码 */
    private String password;

    /** 用户类型 */
    private String userType;

    /** 手机号（注册验证码 key = CAPTCHA_CODE_KEY + 手机号；成功后写入 sys_user.phonenumber） */
    private String phonenumber;

    /** 短信验证码（{@code /sso/sendSmsCodeForRegister} 下发的 6 位码；服务端以 code 字段承载校验） */
    private String code;

    /**
     * 注册后自动绑定的角色标识列表（{@code sys_role.role_key}，可多个）。
     *
     * <p>可不填：为空时 SDK 自动注入客户端配置
     * {@code sa-token.register-role-keys}（见
     * {@code CloudProperties#getRegisterRoleKeys()}）；两者均未配置则不绑定、行为不变。
     * 服务端仅在该用户尚未绑定任何角色时才会执行绑定。</p>
     *
     * <p>与 {@link CloudRegisterBo#getRoleKeys()} 语义一致，仅本 BO 场景专用（短信码场景填手机号）。</p>
     *
     * @since 1.4.0
     */
    private List<String> roleKeys;

}
