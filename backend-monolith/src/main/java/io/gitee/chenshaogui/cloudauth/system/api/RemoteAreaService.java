package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteAreaVo;

import java.util.List;

/**
 * 行政地区编码服务（Dubbo）
 *
 * <p>对外暴露 sys_area 的查询能力，供授权中心 SSO push message 通道转发到客户端 SDK。</p>
 *
 * @author sgchen
 * @since 1.3.0 区域编码（sys_area）模块暴露
 */
public interface RemoteAreaService {

    /**
     * 查询行政地区编码列表（按可选条件过滤，未传条件时返回全量）
     *
     * @param id          主键（可选）
     * @param areaCode    地区编码（可选，精确匹配）
     * @param areaName    区域名称（可选，模糊匹配）
     * @param areaLevel   区域级别（可选）
     * @param parentCode  父级编码（可选，精确匹配）
     * @param compartment 区划代码（可选）
     * @return 区域列表，按 id 升序
     */
    List<RemoteAreaVo> listArea(Long id, String areaCode, String areaName, Integer areaLevel,
                                  String parentCode, String compartment);

    /**
     * 根据区域编码查询单个区域
     *
     * @param areaCode 区域编码（如 "110000"）
     * @return 区域详情；未找到时返回 null
     */
    RemoteAreaVo getByAreaCode(String areaCode);

}
