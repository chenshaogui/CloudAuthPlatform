package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 行政地区编码视图对象 sys_area
 *
 * <p>Dubbo RPC 透传 VO，与系统内 {@code io.gitee.chenshaogui.cloudauth.system.domain.vo.SysAreaVo} 字段一致，
 * 独立定义避免子系统强依赖 system 内部包结构。</p>
 *
 * @author sgchen
 * @since 1.3.0 区域编码（sys_area）模块暴露
 */
@Data
public class RemoteAreaVo implements Serializable {

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
