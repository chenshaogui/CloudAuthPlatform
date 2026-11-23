package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.system.domain.SysArea;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysAreaBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysAreaVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysAreaMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysAreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SysAreaServiceImpl
 *
 * @author sgchen
 * @date 2025/8/29 16:22
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SysAreaServiceImpl implements ISysAreaService {

    private final SysAreaMapper sysAreaMapper;


    @Override
    public List<SysAreaVo> getAreaList(SysAreaBo sysAreaBo) {
        LambdaQueryWrapper<SysArea> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ObjectUtil.isNotNull(sysAreaBo.getAreaCode()), SysArea::getAreaCode, sysAreaBo.getAreaCode())
            .eq(StringUtils.isNotBlank(sysAreaBo.getParentCode()), SysArea::getParentCode, sysAreaBo.getParentCode())
            .orderByAsc(SysArea::getId);
        return sysAreaMapper.selectVoList(wrapper);
    }

    @Override
    public SysAreaVo getSysAreaByCode(String areaCode) {
        return sysAreaMapper.selectVoOne(new LambdaQueryWrapper<SysArea>()
            .eq(SysArea::getAreaCode, areaCode));
    }
}
