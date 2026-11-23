package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部门扩展信息（来自 ln_pro sys_sec_department 独有字段）
 *
 * <p>数据迁移专用表，与 sys_dept 1:1 关联（主键 dept_id 对齐 sys_dept.dept_id），
 * 存储 sys_dept 主表不承载的扩展字段：联系方式、地址、URL、区域描述、扩展描述等。</p>
 *
 * @author Lion Li
 */
@Data
@TableName("sys_dept_ext")
public class SysDeptExt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门ID（关联 sys_dept.dept_id，主键）
     */
    @TableId(value = "dept_id", type = IdType.INPUT)
    private Long deptId;

    // ==================== 联系方式 ====================

    /**
     * 部门负责人姓名（LEADER）
     */
    private String deptManager;

    /**
     * 部门负责人电话（DEPTMANAGERTEL）
     */
    private String deptManagerTel;

    /**
     * 传真（FAX）
     */
    private String fax;

    /**
     * 邮编（POSTCODE）
     */
    private String postcode;

    /**
     * 地址（ADDRESS）
     */
    private String address;

    /**
     * 值班电话（ONDUTYTEL）
     */
    private String onDutyTel;

    /**
     * 网址（URL）
     */
    private String url;

    // ==================== 名称 / 描述 ====================

    /**
     * 简称（SHORTNAME）
     */
    private String shortName;

    /**
     * 打印名（PRINTNAME）
     */
    private String printName;

    /**
     * 描述（DESCRIPTION）
     */
    private String description;

    // ==================== 区域 / 类型 ====================

    /**
     * 部门类型（TYPE）
     */
    private String deptType;

    /**
     * 已部署（DEPLOYED）
     */
    private Long deployed;

    /**
     * 事发地（HAPPENAREA）
     */
    private Long happenArea;

    /**
     * 区域描述（AREA）
     */
    private String area;

    /**
     * 是否启用（USED，10=启用）
     */
    private Long used;

}