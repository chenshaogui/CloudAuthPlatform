package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 行政地区编码
 * sys_area
 */
@Data
@NoArgsConstructor
@TableName("sys_area")
public class SysArea {
    /**
     * 主键
     */
    private Long id;

    /**
     * 地区编码
     */
    private String areaCode;

    /**
     * 区域名称
     */
    private String areaName;

    /**
     * 区域级别 0:国家---1:省---2:市---3:区---4:街道
     */
    private Integer areaLevel;

    /**
     * 父级编码
     */
    private String parentCode;

    /**
     * 区划代码
     */
    private String compartment;

    /**
     * 更新标记
     */
    private Byte syncFlag;

    /**
     * 创建者
     */
    @Schema(description = "创建者", hidden = true)
    @TableField(fill = FieldFill.INSERT)
    private Long createUserId;

    /**
     * 创建者
     */
    @Schema(description = "创建者姓名", hidden = true)
    @TableField(fill = FieldFill.INSERT)
    private String createUserName;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", hidden = true)
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新者
     */
    @Schema(description = "更新者", hidden = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUserId;

    /**
     * 更新者姓名
     */
    @Schema(description = "更新者姓名", hidden = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateUserName;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", hidden = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @Schema(description = "状态 0 停用 1启用 9 删除", hidden = true)
    private Integer status;

}
