package io.gitee.chenshaogui.cloudauth.casm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.chenshaogui.cloudauth.casm.domain.SysClient;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.casm.mapper.SysClientMapper;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysClientService;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheNames;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 客户端管理Service业务层处理
 *
 * @author JLL
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysClientServiceImpl implements ISysClientService {

    private final SysClientMapper baseMapper;

    /**
     * 查询客户端管理
     */
    @Cacheable(cacheNames = CacheNames.SYS_CLIENT_CASM, key = "#id")
    @Override
    public SysClientVo queryById(Long id) {
        SysClientVo vo = baseMapper.selectVoById(id);
        vo.setGrantTypeList(StringUtils.splitList(vo.getGrantType()));
        return vo;
    }

    /**
     * 查询客户端管理
     */
    @Cacheable(cacheNames = CacheNames.SYS_CLIENT_CASM, key = "#clientId")
    @Override
    public SysClientVo queryByClientId(String clientId) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<SysClient>().eq(SysClient::getClientId, clientId));
    }
}
