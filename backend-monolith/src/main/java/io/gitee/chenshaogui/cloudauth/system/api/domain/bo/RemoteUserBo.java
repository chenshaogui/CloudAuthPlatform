package io.gitee.chenshaogui.cloudauth.system.api.domain.bo;


import lombok.Data;
import lombok.NoArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.xss.Xss;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 用户信息业务对象 sys_user
 *
 * @author Michelle.Chung
 */
@Data
@NoArgsConstructor
public class RemoteUserBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 用户账号
     */
    @Xss(message = "用户账号不能包含脚本字符")
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 0, max = 30, message = "用户账号长度不能超过{max}个字符")
    private String userName;

    /**
     * 用户昵称
     */
    @Xss(message = "用户昵称不能包含脚本字符")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过{max}个字符")
    private String nickName;

    /**
     * 用户类型（sys_user系统用户）
     */
    private String userType;

    /**
     * 用户邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 手机号码
     */
    private String phonenumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    private String sex;

    /**
     * 头像地址
     */
    private Long avatar;

    /**
     * 密码
     */
    private String password;

    /**
     * 帐号状态（0正常 1停用）
     */
    private String status;

    /**
     * 最后登录IP
     */
    private String loginIp;

    /**
     * 最后登录时间
     */
    private Date loginDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 数据权限 当前角色ID
     */
    private Long roleId;

    /**
     * 数据是否脱敏 0=否 1=是
     */
    private Integer desensitizeFlag;

    /**
     * 归属地编码
     */
    private String belongCode;

    /**
     * 管辖归属地名称
     */
    private String belongName;

    /**
     * 注册后自动绑定的角色标识列表（sys_role.role_key，可多个）
     * <p>注册链路：RegisterBody → RemoteUserBo → SysUserBo →
     * SysUserServiceImpl#registerUser 按 role_key 查角色并绑定 sys_user_role。
     * 仅用户尚未绑定角色时生效；为空不处理。</p>
     */
    private List<String> roleKeys;

    public RemoteUserBo(Long userId) {
        this.userId = userId;
    }

    public boolean isSuperAdmin() {
        return SystemConstants.SUPER_ADMIN_ID.equals(this.userId);
    }

}
