package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.xss.Xss;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUser;

import java.util.List;

/**
 * 用户信息业务对象 sys_user
 *
 * @author Michelle.Chung
 */

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysUser.class, reverseConvertGenerate = false)
public class SysUserBo extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

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
    @NotBlank(message = "用户昵称不能为空")
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
     * 密码
     */
    private String password;

    /**
     * 帐号状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 角色组
     */
    @Size(min = 1, message = "用户角色不能为空")
    private Long[] roleIds;

    /**
     * 岗位组
     */
    private Long[] postIds;

    /**
     * 数据权限 当前角色ID
     */
    private Long roleId;

    /**
     * 用户ID
     */
    private String userIds;

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
     * 排除不查询的用户(工作流用)
     */
    private String excludeUserIds;

    /**
     * 客户端id
     */
    private String clientId;

    /**
     * 注册后自动绑定的角色标识列表（sys_role.role_key，可多个）
     * <p>由 RemoteUserBo.roleKeys 透传，注册链路 registerUser 中按非空判断自动绑定；
     * 与 {@link #roleIds}（管理员指定角色ID）互不影响、语义不同。</p>
     */
    private List<String> roleKeys;

    public SysUserBo(Long userId) {
        this.userId = userId;
    }

    public boolean isSuperAdmin() {
        return SystemConstants.SUPER_ADMIN_ID.equals(this.userId);
    }

}
