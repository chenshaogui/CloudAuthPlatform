package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;


/**
 * 用户
 *
 * @author Michelle.Chung
 */
@Data
@NoArgsConstructor
public class RemoteUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private String userName;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 用户类型（sys_user系统用户）
     */
    private String userType;

    /**
     * 用户邮箱
     */
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
     * 帐号状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createTime;

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
     * 部门名称
     */
    private String deptName;

    /**
     * 岗位名称列表（如 ["开发工程师", "架构师"]）
     */
    private List<String> postNames;


}
