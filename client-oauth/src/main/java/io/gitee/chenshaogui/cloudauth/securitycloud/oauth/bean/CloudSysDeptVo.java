package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 部门视图对象 sys_dept
 *
 * @author sgchen
 */
@Data
public class CloudSysDeptVo implements Serializable {

    /**
     * 部门id
     */
    private Long deptId;

    /**
     * 父部门id
     */
    private Long parentId;

    /**
     * 父部门名称
     */
    private String parentName;

    /**
     * 祖级列表
     */
    private String ancestors;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 部门类别编码
     */
    private String deptCategory;

    /**
     * 部门编码（业务自定义标识，区别于 dept_id 自增主键）
     */
    private String deptCode;

    /**
     * 显示顺序
     */
    private Integer orderNum;

    /**
     * 负责人ID
     */
    private Long leader;

    /**
     * 负责人
     */
    private String leaderName;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 部门状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createTime;

}
