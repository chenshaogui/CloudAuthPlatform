package io.gitee.chenshaogui.cloudauth.casm.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.chenshaogui.cloudauth.casm.domain.SysKeyInfo;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.KeyInfoReqBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.SysKeyInfoBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyAlthmEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyTypeEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoRspVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysKeyInfoVo;
import io.gitee.chenshaogui.cloudauth.casm.mapper.SysKeyInfoMapper;
import io.gitee.chenshaogui.cloudauth.casm.service.IHSMService;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysClientService;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysKeyInfoService;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheNames;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.QueryGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.StringJoiner;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * 密钥信息管理Service业务层处理
 *
 * @author JLL
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysKeyInfoServiceImpl implements ISysKeyInfoService {

    private final SysKeyInfoMapper baseMapper;

    private final IHSMService hsmService;

    private final ISysClientService sysClientService;

    private  final static  ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);

    /**
     * 查询密钥信息管理
     */
    @Override
    public SysKeyInfoVo queryById(Long id) {
        SysKeyInfoVo vo = baseMapper.selectVoById(id);
        return vo;
    }

    /**
     * 查询密钥信息管理
     */
    @Cacheable(cacheNames = CacheNames.SYS_KEY_INFO, key = "#keyId")
    @Override
    public SysKeyInfoVo queryByKeyId(String keyId) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<SysKeyInfo>().eq(SysKeyInfo::getKeyId, keyId));
    }

    /**
     * 新增密钥信息管理
     */
    @Override
    public Boolean insertByBo(SysKeyInfoBo bo) {
        SysKeyInfo add = MapstructUtils.convert(bo, SysKeyInfo.class);
        // 生成clientId
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }


    @Override
    public KeyInfoRspVo applyKey(@Validated(AddGroup.class) KeyInfoReqBo reqBo) {
        KeyInfoRspVo keyInfoRspVo = null;
        if (StringUtils.isEmpty(reqBo.getClientId())) {
            throw new ServiceException("应用非法！");
        }
        SysClientVo sysClientVo = sysClientService.queryByClientId(reqBo.getClientId());
        if (sysClientVo == null) {
            throw new ServiceException("应用不存在！");
        }
        if (!"0".equals(sysClientVo.getStatus())) {
            throw new ServiceException("该应用不可用，请检查！");
        }
        KeyAlthmEnum keyAlthmEnum = KeyAlthmEnum.getByType(reqBo.getKeyAlthm());
        KeyTypeEnum keyTypeEnum = KeyTypeEnum.getByType(reqBo.getKeyType());
        if (keyTypeEnum.getIsSystem()) {
            throw new ServiceException("系统应用密钥不允许申请！");
        }
        KeyInfoVo keyInfoVo = hsmService.applyKey(keyTypeEnum, keyAlthmEnum, sysClientVo.getClientId(), sysClientVo.getKeyPoolSize());
        SysKeyInfoBo bo = MapstructUtils.convert(keyInfoVo, SysKeyInfoBo.class);
        bo.setClientId(sysClientVo.getClientId());
        StringJoiner joiner = new StringJoiner("_");
        joiner.add(keyTypeEnum.getType()).add(keyAlthmEnum.getType()).add(keyInfoVo.getId().toString());
        bo.setKeyName(joiner.toString());
        bo.setClientKeyId(keyInfoVo.getId());
        // 传输类密钥不入库
        if (this.queryById(keyInfoVo.getId()) == null
            && !KeyTypeEnum.getByType(reqBo.getKeyType()).equals(KeyTypeEnum.TEK)) {
            // 后续放队列里面，延迟入库
            executor.execute(() -> this.insertByBo(bo));
        }
        keyInfoRspVo = hsmService.transKeyInfo(keyInfoVo, String.valueOf(sysClientVo.getAppKeyId()));
        return keyInfoRspVo;
    }

    @Override
    public KeyInfoRspVo useKey(@Validated(QueryGroup.class) KeyInfoReqBo reqBo) {
        KeyInfoRspVo keyInfoRspVo = null;
        try {
            if (StringUtils.isEmpty(reqBo.getClientId())) {
                throw new ServiceException("应用非法！");
            }
            SysClientVo sysClientVo = sysClientService.queryByClientId(reqBo.getClientId());
            if (sysClientVo == null) {
                throw new ServiceException("应用不存在！");
            }
            if (!"0".equals(sysClientVo.getStatus())) {
                throw new ServiceException("该应用不可用，请检查！", 10050);
            }
            KeyInfoVo keyInfoVo = hsmService.useKey(KeyTypeEnum.getByType(reqBo.getKeyType()), reqBo.getKeyId(), reqBo.getClientId());
            keyInfoRspVo = hsmService.transKeyInfo(keyInfoVo, String.valueOf(sysClientVo.getAppKeyId()));
            // 记录密钥使用日志，视情况而定是否记录
        } catch (Exception e) {
            throw new ServiceException("使用密钥失败，" + e.getMessage());
        }
        return keyInfoRspVo;
    }

    @Override
    public KeyInfoRspVo rotationKey(KeyInfoReqBo reqBo) throws Exception {
        KeyInfoRspVo keyInfoRspVo = null;
        if (StringUtils.isEmpty(reqBo.getClientId())) {
            throw new ServiceException("应用非法！");
        }
        SysClientVo sysClientVo = sysClientService.queryByClientId(reqBo.getClientId());
        if (sysClientVo == null) {
            throw new ServiceException("应用不存在！");
        }
        if (!"0".equals(sysClientVo.getStatus())) {
            throw new ServiceException("该应用不可用，请检查！");
        }
        try {
            KeyInfoVo keyInfoVo = hsmService.rotationKey(KeyTypeEnum.getByType(reqBo.getKeyType()), reqBo.getKeyId(), reqBo.getClientId());
            SysKeyInfoBo bo = MapstructUtils.convert(keyInfoVo, SysKeyInfoBo.class);
            bo.setClientId(sysClientVo.getClientId());
            bo.setClientKeyId(sysClientVo.getId());
            // 传输类密钥不入库
            this.insertByBo(bo);
            keyInfoRspVo = hsmService.transKeyInfo(keyInfoVo, String.valueOf(sysClientVo.getAppKeyId()));
        } catch (Exception e) {
            log.error("密钥轮换失败！{}", JSON.toJSONString(reqBo), e);
            throw new ServiceException("密钥轮换失败！" + e.getMessage());
        }
        return keyInfoRspVo;
    }

    @Override
    public Boolean distroyKey(KeyInfoReqBo reqBo) throws Exception {
        if (StringUtils.isEmpty(reqBo.getClientId())) {
            throw new ServiceException("应用非法！");
        }
        SysClientVo sysClientVo = sysClientService.queryByClientId(reqBo.getClientId());
        if (sysClientVo == null) {
            throw new ServiceException("应用不存在！");
        }
        if (!"0".equals(sysClientVo.getStatus())) {
            throw new ServiceException("该应用不可用，请检查！");
        }
        return hsmService.distroyKey(KeyTypeEnum.getByType(reqBo.getKeyType()), reqBo.getKeyId(), reqBo.getClientId());
    }
}
