package io.gitee.chenshaogui.cloudauth.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.gitee.chenshaogui.cloudauth.common.core.domain.model.LoginBody;
import org.hibernate.validator.constraints.Length;

import java.util.List;

/**
 * 用户注册对象
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegisterBody extends LoginBody {

    /**
     * 用户名
     */
    @NotBlank(message = "{user.username.not.blank}")
    @Length(min = 2, max = 30, message = "{user.username.length.valid}")
    private String username;

    /**
     * 用户密码
     * <p>选填：为空时由服务端使用默认密码（sys.user.initPassword）。
     * 非空时才做长度校验（见 SysLoginService.register），故此处不再强制 @NotBlank。</p>
     */
    private String password;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 用户来源类型（写入 sys_user_ext.user_source_type）
     * <p>用于客户端个性化注册时区分用户身份（如辽运通"法人/安全责任人"）。
     * 鉴权中心前端按 sys_client.layout_config.register.roles 渲染单选项后传入；
     * 未传则不写入 sys_user_ext.user_source_type。</p>
     */
    private String userSourceType;

    /**
     * 手机号（短信验证码校验用）
     * <p>注册页验证码由 /resource/sms/code 按手机号发送（Redis key = CAPTCHA_CODE_KEY + 手机号）；
     * 注册校验验证码时优先按本字段取 key，未传时回退 uuid（图片验证码场景）。</p>
     */
    private String phonenumber;

    /**
     * 注册后自动绑定的角色标识列表（sys_role.role_key，可多个）
     * <p>由客户端 SDK 按配置 sa-token.register-role-keys 随注册消息上报（见 SDK 1.4.0），
     * 为空则不执行任何绑定。服务端仅在该用户尚未绑定任何角色时才会绑定；
     * role_key 查不到对应角色时静默跳过（仅记日志）；超级管理员角色禁止经本通道绑定。</p>
     */
    private List<String> roleKeys;

}
