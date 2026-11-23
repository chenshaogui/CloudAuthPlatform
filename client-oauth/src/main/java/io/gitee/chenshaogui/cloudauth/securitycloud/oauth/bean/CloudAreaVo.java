package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 行政地区编码视图对象 sys_area
 *
 * <p>对应授权中心 {@code sys_area} 表的客户端 VO，
 * 业务方通过 {@code CloudPlatformHelper.getAreaList / getAreaByCode} 远程拉取。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加区域编码（sys_area）模块
 */
@Data
public class CloudAreaVo implements Serializable {

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
