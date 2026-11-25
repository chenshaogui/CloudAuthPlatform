package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户密码修改bo
 */
@Data
public class SysUserPasswordNewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 旧密码
     * <p>免旧密码的"手机号 + 短信验证码"找回流程（辽运通风格重置密码页）可不传，
     * 此时必须传 {@link #phonenumber}，验证码按手机号 key 校验。</p>
     */
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    /**
     * 手机号（免旧密码重置时必传：按手机号定位用户，验证码按手机号 key 校验）
     */
    private String phonenumber;

    @NotBlank(message = "租户id不能为空")
    private String tenantId;

    /**
     * 客户端ID（用于校验该客户端是否开放重置密码）
     * <p>鉴权中心前端从 URL 的 ?client=xxx 带入；非必填，未传时跳过按 client 的细粒度校验</p>
     */
    private String clientId;

}
