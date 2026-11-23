package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户注册请求（通用超集入参，字段与认证中心 {@code RegisterBody} 对齐）。
 *
 * <p>认证中心 {@code register} 为<b>单端点、按字段数据驱动</b>，图形码与短信码共用 {@link #code} 字段承载，
 * 服务端按是否存在 {@link #phonenumber} 决定校验 key：</p>
 * <ul>
 *   <li><b>图形验证码注册</b>：填 {@code username}、{@code password}、{@link #code}（图形码）+ {@link #uuid}；
 *       先调 {@code /sso/captcha} 拿 {@code uuid} 与图片。此时 {@code phonenumber} 留空。</li>
 *   <li><b>手机号短信注册</b>：填 {@code username}、{@code password}、{@link #phonenumber} + {@link #code}（短信码）；
 *       先调 {@code /sso/sendSmsCodeForRegister}（面向未注册号码）收码。<b>此时 {@link #uuid} 留空即为正确，非缺陷。</b></li>
 * </ul>
 *
 * <p>若希望编译期就避免字段混用，建议改用类型化的
 * {@code CloudPlatformHelper.registerByCaptcha(CloudRegisterByCaptchaBo)} 或
 * {@code CloudPlatformHelper.registerBySms(CloudRegisterBySmsBo)}（三者底层是同一个服务端 {@code register}）。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudRegisterBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权类型（注册一般为 {@code "password"}） */
    private String grantType = "password";

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

    /** 用户名 */
    private String username;

    /** 用户密码 */
    private String password;

    /** 手机号（注册短信验证码校验用） */
    private String phonenumber;

    /** 用户类型 */
    private String userType;

    /** 用户来源类型（写入 sys_user_ext.user_source_type，客户端个性化注册区分身份用，可空） */
    private String userSourceType;

    /** 图形验证码（认证中心开启验证码时必填） */
    private String code;

    /** 验证码唯一标识 */
    private String uuid;

    /**
     * 注册后自动绑定的角色标识列表（{@code sys_role.role_key}，可多个）。
     *
     * <p>可不填：为空时 SDK 自动注入客户端配置
     * {@code sa-token.register-role-keys}（见
     * {@code CloudProperties#getRegisterRoleKeys()}）；两者均未配置则不绑定、行为不变。
     * 服务端仅在该用户尚未绑定任何角色时才会执行绑定。</p>
     *
     * @since 1.4.0
     */
    private List<String> roleKeys;

}
