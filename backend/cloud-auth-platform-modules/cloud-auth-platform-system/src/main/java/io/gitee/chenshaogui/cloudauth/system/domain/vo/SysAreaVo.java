package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 行政地区编码
 * sys_area vo
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SysClient.class)
public class SysAreaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

}
