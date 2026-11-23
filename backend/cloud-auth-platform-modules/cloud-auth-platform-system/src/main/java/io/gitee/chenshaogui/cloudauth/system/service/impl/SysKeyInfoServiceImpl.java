package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteKeyInfoService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteKeyInfoVo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;
import io.gitee.chenshaogui.cloudauth.system.domain.SysKeyInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUser;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysKeyInfoBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyInfoVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysKeyInfoMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysKeyInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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


    private final SysClientMapper clientMapper;


    @DubboReference
    private RemoteKeyInfoService remoteSysKeyInfoService;


    /**
     * 查询密钥信息管理列表
     */
    @Override
    public TableDataInfo<SysKeyInfoVo> queryPageList(SysKeyInfoBo bo, PageQuery pageQuery) {
        QueryWrapper<SysKeyInfo> wrapper = Wrappers.query();
        wrapper.eq("u.del_flag", SystemConstants.NORMAL)
            .eq(StringUtils.isNotBlank(bo.getClientId()), "u.client_id", bo.getClientId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), "u.status", bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getKeyId()), "u.key_id", bo.getKeyId())
            .eq(StringUtils.isNotBlank(bo.getKeyType()), "u.key_type", bo.getKeyType())
            .eq(StringUtils.isNotBlank(bo.getKeyName()), "u.key_name", bo.getKeyName())
            .eq(StringUtils.isNotBlank(bo.getKeyAlthm()), "u.key_althm", bo.getKeyAlthm())
            .orderByDesc("u.id");
        Page<SysKeyInfoVo> result = baseMapper.selectJoinVoList(pageQuery.build(), wrapper);
        return TableDataInfo.build(result);
    }

    @Override
    public SysKeyInfoVo viewById(Long id) {
        SysKeyInfoVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            throw new ServiceException("密钥信息不存在");
        }
        RemoteKeyInfoVo remoteKeyInfoVo = remoteSysKeyInfoService.viewById(String.valueOf(id), vo.getKeyType(), vo.getKeyAlthm(), vo.getClientId());
        return MapstructUtils.convert(remoteKeyInfoVo, SysKeyInfoVo.class);
    }

    @Override
    public SysKeyInfoVo applyKey(SysKeyInfoBo bo) {
        LambdaQueryWrapper<SysClient>  lqw = Wrappers.lambdaQuery();
        lqw.eq(SysClient::getClientId, bo.getClientId());
        lqw.eq(SysClient::getStatus, "0");
        SysClientVo clientVo = clientMapper.selectVoOne(lqw);
        if (clientVo == null) {
            throw new ServiceException("客户端不存在");
        }
        RemoteKeyInfoVo remoteKeyInfoVo = remoteSysKeyInfoService.applyKey(null, bo.getKeyType(), bo.getKeyAlthm(), bo.getClientId());
        return MapstructUtils.convert(remoteKeyInfoVo, SysKeyInfoVo.class);
    }

    @Override
    public List<SysKeyInfoVo> queryList(SysKeyInfoBo bo) {
        LambdaQueryWrapper<SysKeyInfo> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public SysKeyInfoVo rotationKey(Long id) {
        SysKeyInfoVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            throw new ServiceException("密钥信息不存在");
        }
        if (Arrays.asList("PEK", "AEK", "DEK", "KEK").contains(vo.getKeyType())) {
            throw new ServiceException("密钥类型为非TEK时不能轮换");
        }
        RemoteKeyInfoVo remoteKeyInfoVo = remoteSysKeyInfoService.rotationKey(String.valueOf(id), vo.getKeyType(), vo.getKeyAlthm(), vo.getClientId());
        return MapstructUtils.convert(remoteKeyInfoVo, SysKeyInfoVo.class);
    }

    @Override
    public Boolean distroyKey(Long id) {
        SysKeyInfoVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            throw new ServiceException("密钥信息不存在");
        }
        if (Arrays.asList("PEK", "AEK", "DEK", "KEK").contains(vo.getKeyType())) {
            throw new ServiceException("密钥类型为非TEK时不能销毁");
        }
        remoteSysKeyInfoService.distroyKey(String.valueOf(id), vo.getKeyType(), vo.getKeyAlthm(), vo.getClientId());
        return true;
    }

    private LambdaQueryWrapper<SysKeyInfo> buildQueryWrapper(SysKeyInfoBo bo) {
        LambdaQueryWrapper<SysKeyInfo> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getClientId()), SysKeyInfo::getClientId, bo.getClientId());
        lqw.eq(StringUtils.isNotBlank(bo.getKeyId()), SysKeyInfo::getKeyId, bo.getKeyId());
        lqw.eq(StringUtils.isNotBlank(bo.getKeyType()), SysKeyInfo::getKeyType, bo.getKeyType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SysKeyInfo::getStatus, bo.getStatus());
        lqw.orderByAsc(SysKeyInfo::getId);
        return lqw;
    }
}
