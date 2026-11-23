package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 行政地区编码查询条件 sys_area
 *
 * <p>用于 {@code CloudPlatformHelper.getAreaList(bo)} 的可选过滤条件，
 * 所有字段均可空（为空时不过滤）。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加区域编码（sys_area）模块
 */
@Data
public class CloudAreaBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（可选）
     */
    private Long id;

    /**
     * 地区编码（可选，精确匹配）
     */
    private String areaCode;

    /**
     * 区域名称（可选，模糊匹配）
     */
    private String areaName;

    /**
     * 区域级别（可选，0:国家 1:省 2:市 3:区 4:街道）
     */
    private Integer areaLevel;

    /**
     * 父级编码（可选，精确匹配）
     */
    private String parentCode;

    /**
     * 区划代码（可选）
     */
    private String compartment;

}
