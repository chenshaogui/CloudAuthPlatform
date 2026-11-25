package io.gitee.chenshaogui.cloudauth.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.chenshaogui.cloudauth.common.core.exception.base.BaseException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserClientConfig;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserClientConfigBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserClientConfigVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserClientConfigMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserClientConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserClientConfigServiceImpl implements ISysUserClientConfigService {

    private final SysUserClientConfigMapper configMapper;

    private final ISysClientService sysClientService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(SysUserClientConfigBo bo) {
        // 1. 校验配置是否重复（排除自身ID）
        /*
          if (checkConfigUnique(bo.getUserId(), bo.getClientId(), bo.getId())) {
            return false;
        }
        */
        SysUserClientConfig config = MapstructUtils.convert(bo, SysUserClientConfig.class);
        // 2. 存在则更新，不存在则新增
        SysUserClientConfigVo existConfig = getConfigByUserIdAndClientId(config.getUserId(), config.getClientId());
        if (existConfig.getId() != null) {
            config.setId(existConfig.getId()); // 复用主键，更新数据
            return configMapper.updateById(config) > 0;
        } else {
            return configMapper.insert(config) > 0;
        }
    }

    @Override
    public SysUserClientConfigVo getConfigByUserIdAndClientId(Long userId, String clientId) {
        SysUserClientConfigVo clientConfigVo = configMapper.selectVoOne(new LambdaQueryWrapper<SysUserClientConfig>()
            .eq(SysUserClientConfig::getUserId, userId)
            .eq(SysUserClientConfig::getClientId, clientId));
        if(clientConfigVo == null){
            clientConfigVo = new SysUserClientConfigVo();
            clientConfigVo.setClientId(clientId);
            clientConfigVo.setUserId(userId);
        }
        return clientConfigVo;
    }

    @Override
    public List<SysUserClientConfigVo> listByUserId(Long userId) {
        // 此处需要组装数据，先查询用户能看到哪些客户端，然后再根据
        List<SysClientVo> sysClientVos = sysClientService.queryListByUserId(userId);
        if(sysClientVos.isEmpty()){
            return Collections.emptyList();
        }
        List<String> clientIdList = sysClientVos.stream().map(SysClientVo::getClientId).toList();
        List<SysUserClientConfigVo> clientConfigVos = configMapper.selectVoList(new LambdaQueryWrapper<SysUserClientConfig>()
            .eq(SysUserClientConfig::getUserId, userId)
            .in(SysUserClientConfig::getClientId, clientIdList));
        Map<String, SysUserClientConfigVo> clientConfigMap = clientConfigVos.stream()
            .collect(Collectors.toMap(
                SysUserClientConfigVo::getClientId,
                vo -> vo,
                (existingValue, newValue) -> existingValue,
                HashMap::new
            ));
        List<SysUserClientConfigVo> result = new ArrayList<>();
        for (SysClientVo sysClientVo : sysClientVos) {
            SysUserClientConfigVo clientConfigVo = clientConfigMap.get(sysClientVo.getClientId());
            if(clientConfigVo == null){
                clientConfigVo = new SysUserClientConfigVo();
                clientConfigVo.setClientId(sysClientVo.getClientId());
                clientConfigVo.setUserId(userId);
            }
            clientConfigVo.setClientKey(sysClientVo.getClientKey());
            result.add(clientConfigVo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveConfig(List<SysUserClientConfigBo> configList) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        // 批量校验
        List<SysUserClientConfig> batchList = new ArrayList<>();
        for (SysUserClientConfigBo config : configList) {
            if (checkConfigUnique(config.getUserId(), config.getClientId(), config.getId())) {
                throw new BaseException("用户【" + config.getUserId() + "】-客户端【" + config.getClientId() + "】配置重复");
            }
            SysUserClientConfig iconfig = MapstructUtils.convert(config, SysUserClientConfig.class);
            Long userId = iconfig.getUserId();
            String clientId = iconfig.getClientId();
            if(userId==null && loginUser!=null){
                iconfig.setUserId(loginUser.getUserId());
            }
            if(StringUtils.isEmpty(clientId)){
                iconfig.setClientId(LoginHelper.getClientId());
            }
            batchList.add(iconfig);
        }
        return configMapper.insertBatch(batchList);
    }

    @Override
    public boolean checkConfigUnique(Long userId, String clientId, Long excludeId) {
        LambdaQueryWrapper<SysUserClientConfig> queryWrapper = new LambdaQueryWrapper<SysUserClientConfig>()
            .eq(SysUserClientConfig::getUserId, userId)
            .eq(SysUserClientConfig::getClientId, clientId);
        // 排除自身ID（更新场景）
        if (excludeId != null) {
            queryWrapper.ne(SysUserClientConfig::getId, excludeId);
        }
        return configMapper.exists(queryWrapper);
    }

    /**
     * 根据id查询客户端信息
     *
     * @param id 唯一id
     * @return list
     */
    @Override
    public SysUserClientConfigVo getConfigById(Long id) {
        return configMapper.selectVoById(id);
    }

    @Override
    public int delete(SysUserClientConfigBo bo) {
        LambdaQueryWrapper<SysUserClientConfig> queryWrapper = new LambdaQueryWrapper<SysUserClientConfig>()
            .eq(SysUserClientConfig::getUserId, bo.getUserId())
            .eq(SysUserClientConfig::getClientId, bo.getClientId());
        return configMapper.delete(queryWrapper);
    }
}
