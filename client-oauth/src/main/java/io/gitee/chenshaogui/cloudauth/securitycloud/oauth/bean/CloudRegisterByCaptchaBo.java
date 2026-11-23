package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户注册请求 —— <b>图形验证码</b>场景（字段与认证中心 {@code RegisterBody} 对齐）。
 *
 * <p>认证中心 {@code register} 为单端点、按字段数据驱动：本 BO 只填 {@code uuid}（不填
 * {@code phonenumber}），服务端即以 {@code CAPTCHA_CODE_KEY + uuid} 校验 {@link #code}（图形码）。
 * 需先调 {@code /sso/captcha} 拿到 {@code uuid} 与图片，用户输入 {@code code} 后一起提交。</p>
 *
 * <p>若走手机号短信注册，请改用 {@link CloudRegisterBySmsBo}。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudRegisterByCaptchaBo implements Serializable {

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

    /** 用户来源类型（写入 sys_user_ext.user_source_type，客户端个性化注册区分身份用，可空） */
    private String userSourceType;

    /** 图形验证码（认证中心开启验证码时必填，用户根据 {@code /sso/captcha} 图片输入） */
    private String code;

    /** 图形验证码唯一标识（来自 {@code /sso/captcha}；服务端按此定位 Redis 里的验证码） */
    private String uuid;

    /**
     * 注册后自动绑定的角色标识列表（{@code sys_role.role_key}，可多个）。
     *
     * <p>可不填：为空时 SDK 自动注入客户端配置
     * {@code sa-token.register-role-keys}（见
     * {@code CloudProperties#getRegisterRoleKeys()}）；两者均未配置则不绑定、行为不变。
     * 服务端仅在该用户尚未绑定任何角色时才会执行绑定。</p>
     *
     * <p>与 {@link CloudRegisterBo#getRoleKeys()} 语义一致，仅本 BO 场景专用（图形码不填手机号）。</p>
     *
     * @since 1.4.0
     */
    private List<String> roleKeys;

}
