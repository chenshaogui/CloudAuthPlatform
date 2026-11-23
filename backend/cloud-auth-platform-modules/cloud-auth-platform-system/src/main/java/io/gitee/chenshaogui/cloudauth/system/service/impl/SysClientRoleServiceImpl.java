package io.gitee.chenshaogui.cloudauth.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.core.exception.base.BaseException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ObjectUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClientRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRole;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientRoleBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientRoleVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 客户端角色管理Service业务层处理
 *
 * @author Michelle.Chung
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysClientRoleServiceImpl implements ISysClientRoleService {

    private final SysClientRoleMapper baseMapper;

    private final SysRoleMapper sysRoleMapper;



    /**
     * 查询客户端角色管理列表
     */
    @Override
    public TableDataInfo<SysClientRoleVo> queryPageList(SysClientRoleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysClientRole> lqw = buildQueryWrapper(bo);
        Page<SysClientRoleVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询客户端角色管理列表
     */
    @Override
    public List<SysClientRoleVo> queryList(SysClientRoleBo bo) {
        LambdaQueryWrapper<SysClientRole> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<SysClientRole> buildQueryWrapper(SysClientRoleBo bo) {
        LambdaQueryWrapper<SysClientRole> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getClientId()), SysClientRole::getClientId, bo.getClientId());
        lqw.eq(ObjectUtils.isNotEmpty(bo.getRoleId()), SysClientRole::getRoleId, bo.getRoleId());
        lqw.in(ObjectUtils.isNotEmpty(bo.getRoleIdList()), SysClientRole::getRoleId, bo.getRoleIdList());
        lqw.orderByAsc(SysClientRole::getClientId);
        return lqw;
    }

    /**
     * 新增客户端角色管理
     */
    @Override
    public Boolean insertByBo(SysClientRoleBo bo) {
        SysClientRole add = MapstructUtils.convert(bo, SysClientRole.class);
        return baseMapper.insert(add) > 0;
    }

    /**
     * 修改客户端角色管理
     */
    @Override
    public Boolean updateByBo(SysClientRoleBo bo) {
        SysClientRole update = MapstructUtils.convert(bo, SysClientRole.class);
        return baseMapper.updateById(update) > 0;
    }

    @Override
    public Boolean insertOrUpdateByBo(SysClientRoleBo bo) {
        SysClientRole update = MapstructUtils.convert(bo, SysClientRole.class);
        return baseMapper.insertOrUpdate(update);
    }

    /**
     * 批量删除客户端角色管理
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    @Override
    public Boolean deleteByRoleId(Long roleId) {
        return baseMapper.delete(new LambdaQueryWrapper<SysClientRole>().eq(SysClientRole::getRoleId, roleId)) > 0;
    }

    @Override
    public SysClientRoleVo getInfoByRoleId(Long roleId) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<SysClientRole>()
            .eq(SysClientRole::getRoleId, roleId)
            .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bindRolesToClient(String clientId, List<Long> roleIds) {
        // 1. 校验角色是否存在
        if (roleIds != null && !roleIds.isEmpty()) {
            Long existCount = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleId, roleIds));
            if (existCount != roleIds.size()) {
                throw new BaseException("部分角色不存在，绑定失败");
            }
        }

        // 2. 先删除该客户端已有的角色关联（保证幂等）
        baseMapper.delete(new LambdaQueryWrapper<SysClientRole>().eq(SysClientRole::getClientId, clientId));

        // 3. 批量新增新的关联
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysClientRole> clientRoleList = new ArrayList<>();
            for (Long roleId : roleIds) {
                SysClientRole clientRole = new SysClientRole();
                clientRole.setClientId(clientId);
                clientRole.setRoleId(roleId);
                clientRoleList.add(clientRole);
            }
            return baseMapper.insertBatch(clientRoleList);
        }
        return true;
    }

    @Override
    public List<Long> getRoleIdsByClientId(Long clientId) {
        List<SysClientRoleVo> sysClientRoleVos = baseMapper.selectVoList(new LambdaQueryWrapper<SysClientRole>().eq(SysClientRole::getClientId, clientId));
        return sysClientRoleVos.stream().map(SysClientRoleVo::getRoleId).toList();
    }

}
