package io.gitee.chenshaogui.cloudauth.system.domain.vo;


import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


/**
 * 邮箱模板视图对象
 *
 * @author sgchen
 * @since 2025-12-02
 */
@Data
@ColumnWidth(value = 20)
@ExcelIgnoreUnannotated
public class SysMailTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 编号
     */
    @Schema(description = "编号")
    @ExcelProperty(value = "编号")
    private Long id;

    /**
     * 模板类型 字典类型：sys_mail_template_type
     */
    @Schema(description = " 字典类型：sys_mail_template_type")
    @ExcelProperty(value = "模板类型")
    private Integer type;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称")
    @ExcelProperty(value = "模板名称")
    private String name;

    /**
     * 模板编码
     */
    @Schema(description = "模板编码")
    @ExcelProperty(value = "模板编码")
    private String code;

    /**
     * 发送的邮箱账号编码
     */
    @Schema(description = "发送的邮箱账号编码")
    @ExcelProperty(value = "发送的邮箱账号编码")
    private String accountCode;

    /**
     * 模板标题
     */
    @Schema(description = "模板标题")
    @ExcelProperty(value = "模板标题")
    private String title;

    /**
     * 模板内容
     */
    @Schema(description = "模板内容")
    @ExcelProperty(value = "模板内容")
    private String content;

    /**
     * 参数数组
     */
    @Schema(description = "下拉树结构")
    @ExcelProperty(value = "参数数组")
    private String typeParams;

    /**
     * 备注
     */
    @Schema(description = "备注")
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 状态 0=停用,1=启用
     */
    @Schema(description = "状态 0=停用,1=启用")
    @ExcelProperty(value = "状态 0=停用,1=启用")
    private Integer status;


}
