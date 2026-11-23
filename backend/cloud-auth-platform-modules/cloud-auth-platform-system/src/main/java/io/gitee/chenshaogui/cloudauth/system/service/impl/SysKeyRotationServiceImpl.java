package io.gitee.chenshaogui.cloudauth.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysKeyRotation;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysKeyRotationBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyRotationVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysKeyRotationMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 密钥轮换记录管理Service业务层处理
 *
 * @author JLL
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysKeyRotationServiceImpl implements ISysKeyRotationService {

    private final SysKeyRotationMapper baseMapper;


    /**
     * 查询密钥轮换记录管理列表
     */
    @Override
    public TableDataInfo<SysKeyRotationVo> queryPageList(SysKeyRotationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysKeyRotation> lqw = buildQueryWrapper(bo);
        Page<SysKeyRotationVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public SysKeyRotationVo viewById(Long id) {
        return baseMapper.selectVoById(id);
    }

    private LambdaQueryWrapper<SysKeyRotation> buildQueryWrapper(SysKeyRotationBo bo) {
        LambdaQueryWrapper<SysKeyRotation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getClientId()), SysKeyRotation::getOldKeyId, bo.getOldKeyId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SysKeyRotation::getStatus, bo.getStatus());
        lqw.orderByAsc(SysKeyRotation::getId);
        return lqw;
    }
}
