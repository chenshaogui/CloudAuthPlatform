package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysTenant;
import io.gitee.chenshaogui.cloudauth.system.domain.SysTenantPackage;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysTenantPackageBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysTenantPackageVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysTenantMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysTenantPackageMapper;
import io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeEvent;
import io.gitee.chenshaogui.cloudauth.system.service.ISysTenantPackageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 租户套餐Service业务层处理
 *
 * @author Michelle.Chung
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysTenantPackageServiceImpl implements ISysTenantPackageService {

    private final SysTenantPackageMapper baseMapper;
    private final SysTenantMapper tenantMapper;

    /**
     * Spring 事件发布器（权限同步机制使用）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询租户套餐
     */
    @Override
    public SysTenantPackageVo queryById(Long packageId){
        return baseMapper.selectVoById(packageId);
    }

    /**
     * 查询租户套餐列表
     */
    @Override
    public TableDataInfo<SysTenantPackageVo> queryPageList(SysTenantPackageBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysTenantPackage> lqw = buildQueryWrapper(bo);
        Page<SysTenantPackageVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<SysTenantPackageVo> selectList() {
        return baseMapper.selectVoList(new LambdaQueryWrapper<SysTenantPackage>()
                .eq(SysTenantPackage::getStatus, SystemConstants.NORMAL));
    }

    /**
     * 查询租户套餐列表
     */
    @Override
    public List<SysTenantPackageVo> queryList(SysTenantPackageBo bo) {
        LambdaQueryWrapper<SysTenantPackage> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<SysTenantPackage> buildQueryWrapper(SysTenantPackageBo bo) {
        LambdaQueryWrapper<SysTenantPackage> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getPackageName()), SysTenantPackage::getPackageName, bo.getPackageName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SysTenantPackage::getStatus, bo.getStatus());
        lqw.orderByAsc(SysTenantPackage::getPackageId);
        return lqw;
    }

    /**
     * 新增租户套餐
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(SysTenantPackageBo bo) {
        SysTenantPackage add = MapstructUtils.convert(bo, SysTenantPackage.class);
        // 保存菜单id
        List<Long> menuIds = Arrays.asList(bo.getMenuIds());
        add.setMenuIds(CollUtil.isNotEmpty(menuIds) ? StringUtils.joinComma(menuIds) : "");
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setPackageId(add.getPackageId());
        }
        return flag;
    }

    /**
     * 修改租户套餐
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(SysTenantPackageBo bo) {
        SysTenantPackage update = MapstructUtils.convert(bo, SysTenantPackage.class);
        // 保存菜单id
        List<Long> menuIds = Arrays.asList(bo.getMenuIds());
        update.setMenuIds(CollUtil.isNotEmpty(menuIds) ? StringUtils.joinComma(menuIds) : "");
        boolean flag = baseMapper.updateById(update) > 0;
        if (flag) {
            publishPackageChange(bo.getPackageId());
        }
        return flag;
    }

    /**
     * 校验套餐名称是否唯一
     */
    @Override
    public boolean checkPackageNameUnique(SysTenantPackageBo bo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysTenantPackage>()
            .eq(SysTenantPackage::getPackageName, bo.getPackageName())
            .ne(ObjectUtil.isNotNull(bo.getPackageId()), SysTenantPackage::getPackageId, bo.getPackageId()));
        return !exist;
    }

    /**
     * 修改套餐状态
     *
     * @param bo 套餐信息
     * @return 结果
     */
    @Override
    public int updatePackageStatus(SysTenantPackageBo bo) {
        SysTenantPackage tenantPackage = MapstructUtils.convert(bo, SysTenantPackage.class);
        int rows = baseMapper.updateById(tenantPackage);
        if (rows > 0) {
            publishPackageChange(bo.getPackageId());
        }
        return rows;
    }

    /**
     * 批量删除租户套餐
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            boolean exists = tenantMapper.exists(new LambdaQueryWrapper<SysTenant>().in(SysTenant::getPackageId, ids));
            if (exists) {
                throw new ServiceException("租户套餐已被使用");
            }
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 发布租户套餐变更事件
     *
     * <p>套餐的菜单集合变化会改变引用该套餐的全部租户的可见菜单，
     * 因此先反查使用该套餐的租户列表，再按租户维度通知（避免逐个枚举用户）</p>
     *
     * @param packageId 套餐ID
     */
    private void publishPackageChange(Long packageId) {
        if (packageId == null) {
            return;
        }
        try {
            List<String> tenantIds = tenantMapper.selectList(
                    new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getPackageId, packageId))
                .stream().map(SysTenant::getTenantId).filter(StringUtils::isNotBlank).toList();
            if (CollUtil.isEmpty(tenantIds)) {
                return;
            }
            eventPublisher.publishEvent(new PermissionChangeEvent(this, null, tenantIds,
                PermissionChangeEvent.ChangeType.UPDATE));
        } catch (Exception e) {
            log.warn("租户套餐变更事件发布失败 packageId={}, err={}", packageId, e.getMessage());
        }
    }
}
