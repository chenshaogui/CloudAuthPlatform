package io.gitee.chenshaogui.cloudauth.system.dubbo;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteAreaService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteAreaVo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysArea;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysAreaVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysAreaMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysAreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 行政地区编码服务 Dubbo 实现
 *
 * <p>将内部 {@link ISysAreaService}（sys_area）按需转换为 {@link RemoteAreaVo} 暴露给授权中心，
 * 由授权中心通过 SSO push message 通道转发到客户端 SDK。</p>
 *
 * @author sgchen
 * @since 1.3.0 区域编码（sys_area）模块暴露
 */
@Slf4j
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteAreaServiceImpl implements RemoteAreaService {

    private final ISysAreaService areaService;
    private final SysAreaMapper areaMapper;

    /**
     * 查询行政地区编码列表
     */
    @Override
    public List<RemoteAreaVo> listArea(Long id, String areaCode, String areaName, Integer areaLevel,
                                       String parentCode, String compartment) {
        LambdaQueryWrapper<SysArea> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ObjectUtil.isNotNull(id), SysArea::getId, id)
                .eq(StringUtils.isNotBlank(areaCode), SysArea::getAreaCode, areaCode)
                .like(StringUtils.isNotBlank(areaName), SysArea::getAreaName, areaName)
                .eq(ObjectUtil.isNotNull(areaLevel), SysArea::getAreaLevel, areaLevel)
                .eq(StringUtils.isNotBlank(parentCode), SysArea::getParentCode, parentCode)
                .eq(StringUtils.isNotBlank(compartment), SysArea::getCompartment, compartment)
                .orderByAsc(SysArea::getId);
        List<SysAreaVo> list = areaMapper.selectVoList(wrapper);
        return MapstructUtils.convert(list, RemoteAreaVo.class);
    }

    /**
     * 根据区域编码查询单个区域
     */
    @Override
    public RemoteAreaVo getByAreaCode(String areaCode) {
        if (StringUtils.isBlank(areaCode)) {
            return null;
        }
        SysAreaVo vo = areaService.getSysAreaByCode(areaCode);
        return MapstructUtils.convert(vo, RemoteAreaVo.class);
    }

}
