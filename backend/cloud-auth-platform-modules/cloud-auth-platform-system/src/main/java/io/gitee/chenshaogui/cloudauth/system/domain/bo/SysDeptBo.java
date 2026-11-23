package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysDept;
import io.gitee.chenshaogui.cloudauth.system.domain.SysDeptExt;

/**
 * 部门业务对象 sys_dept
 *
 * @author Michelle.Chung
 */

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysDept.class, reverseConvertGenerate = false)
public class SysDeptBo extends BaseEntity {

    /**
     * 部门id
     */
    private Long deptId;

    /**
     * 父部门ID
     */
    private Long parentId;

    /**
     * 部门名称
     */
    @NotBlank(message = "部门名称不能为空")
    @Size(min = 0, max = 30, message = "部门名称长度不能超过{max}个字符")
    private String deptName;

    /**
     * 部门类别编码
     */
    @Size(min = 0, max = 100, message = "部门类别编码长度不能超过{max}个字符")
    private String deptCategory;

    /**
     * 部门编码（业务自定义标识，区别于 dept_id 自增主键）
     */
    @Size(min = 0, max = 64, message = "部门编码长度不能超过{max}个字符")
    private String deptCode;

    /**
     * 显示顺序
     */
    @NotNull(message = "显示顺序不能为空")
    private Integer orderNum;

    /**
     * 负责人
     */
    private Long leader;

    /**
     * 联系电话
     */
    @Size(min = 0, max = 11, message = "联系电话长度不能超过{max}个字符")
    private String phone;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 部门状态（0正常 1停用）
     */
    private String status;

    /**
     * 归属部门id（部门树）
     */
    private Long belongDeptId;

    /**
     * 部门扩展信息（ln_pro 独有字段，与 sys_dept_ext 1:1）
     * <p>主表 add/edit 一并提交，由 SysDeptServiceImpl 同步写 sys_dept_ext。</p>
     */
    private SysDeptExt ext;

}
