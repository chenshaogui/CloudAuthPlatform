package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheNames;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.*;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.mybatis.helper.DataBaseHelper;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.CacheUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.domain.SysDept;
import io.gitee.chenshaogui.cloudauth.system.domain.SysDeptExt;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUser;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysDeptBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysDeptVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysDeptMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserMapper;
import io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeEvent;
import io.gitee.chenshaogui.cloudauth.system.service.ISysDeptExtService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysDeptService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 部门管理 服务实现
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysDeptServiceImpl implements ISysDeptService {

    private final SysDeptMapper baseMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final ISysDeptExtService deptExtService;

    /**
     * Spring 事件发布器（权限同步机制使用）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询部门管理数据
     *
     * @param dept      部门信息
     * @param pageQuery 分页对象
     * @return 部门信息集合
     */
    @Override
    public TableDataInfo<SysDeptVo> selectPageDeptList(SysDeptBo dept, PageQuery pageQuery) {
        Page<SysDeptVo> page = baseMapper.selectPageDeptList(pageQuery.build(), buildQueryWrapper(dept));
        return TableDataInfo.build(page);
    }

    /**
     * 查询部门管理数据
     *
     * @param dept 部门信息
     * @return 部门信息集合
     */
    @Override
    public List<SysDeptVo> selectDeptList(SysDeptBo dept) {
        LambdaQueryWrapper<SysDept> lqw = buildQueryWrapper(dept);
        return baseMapper.selectDeptList(lqw);
    }

    /**
     * 查询部门树结构信息
     *
     * @param bo 部门信息
     * @return 部门树信息集合
     */
    @Override
    public List<Tree<Long>> selectDeptTreeList(SysDeptBo bo) {
        LambdaQueryWrapper<SysDept> lqw = buildQueryWrapper(bo);
        List<SysDeptVo> depts = baseMapper.selectDeptList(lqw);
        return buildDeptTreeSelect(depts);
    }

    private LambdaQueryWrapper<SysDept> buildQueryWrapper(SysDeptBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysDept> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysDept::getDelFlag, SystemConstants.NORMAL);
        lqw.eq(ObjectUtil.isNotNull(bo.getDeptId()), SysDept::getDeptId, bo.getDeptId());
        lqw.eq(ObjectUtil.isNotNull(bo.getParentId()), SysDept::getParentId, bo.getParentId());
        lqw.like(StringUtils.isNotBlank(bo.getDeptName()), SysDept::getDeptName, bo.getDeptName());
        lqw.like(StringUtils.isNotBlank(bo.getDeptCode()), SysDept::getDeptCode, bo.getDeptCode());
        lqw.like(StringUtils.isNotBlank(bo.getDeptCategory()), SysDept::getDeptCategory, bo.getDeptCategory());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SysDept::getStatus, bo.getStatus());
        lqw.between(params.get("beginTime") != null && params.get("endTime") != null,
            SysDept::getCreateTime, params.get("beginTime"), params.get("endTime"));
        lqw.orderByAsc(SysDept::getAncestors);
        lqw.orderByAsc(SysDept::getParentId);
        lqw.orderByAsc(SysDept::getOrderNum);
        lqw.orderByAsc(SysDept::getDeptId);
        if (ObjectUtil.isNotNull(bo.getBelongDeptId())) {
            //部门树搜索
            lqw.and(x -> {
                List<Long> deptIds = baseMapper.selectDeptAndChildById(bo.getBelongDeptId());
                x.in(SysDept::getDeptId, deptIds);
            });
        }
        return lqw;
    }

    /**
     * 构建前端所需要下拉树结构
     *
     * @param depts 部门列表
     * @return 下拉树结构列表
     */
    @Override
    public List<Tree<Long>> buildDeptTreeSelect(List<SysDeptVo> depts) {
        if (CollUtil.isEmpty(depts)) {
            return CollUtil.newArrayList();
        }
        return TreeBuildUtils.buildMultiRoot(
            depts,
            SysDeptVo::getDeptId,
            SysDeptVo::getParentId,
            (node, treeNode) -> treeNode
                .setId(node.getDeptId())
                .setParentId(node.getParentId())
                .setName(node.getDeptName())
                .setWeight(node.getOrderNum())
                .putExtra("disabled", SystemConstants.DISABLE.equals(node.getStatus()))
        );
    }

    /**
     * 根据角色ID查询部门树信息
     *
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    @Override
    public List<Long> selectDeptListByRoleId(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        return baseMapper.selectDeptListByRoleId(roleId, role.getDeptCheckStrictly());
    }

    /**
     * 根据部门ID查询信息
     *
     * @param deptId 部门ID
     * @return 部门信息
     */
    @Cacheable(cacheNames = CacheNames.SYS_DEPT, key = "#deptId")
    @Override
    public SysDeptVo selectDeptById(Long deptId) {
        SysDeptVo dept = baseMapper.selectVoById(deptId);
        if (ObjectUtil.isNull(dept)) {
            return null;
        }
        SysDeptVo parentDept = baseMapper.selectVoOne(new LambdaQueryWrapper<SysDept>()
            .select(SysDept::getDeptName).eq(SysDept::getDeptId, dept.getParentId()));
        dept.setParentName(ObjectUtils.notNullGetter(parentDept, SysDeptVo::getDeptName));
        // 回填 ln_pro 独有扩展字段（按需调用，单条读不影响列表性能）
        dept.setExt(deptExtService.queryByDeptId(deptId));
        return dept;
    }

    /**
     * 通过部门ID串查询部门
     *
     * @param deptIds 部门id串
     * @return 部门列表信息
     */
    @Override
    public List<SysDeptVo> selectDeptByIds(List<Long> deptIds) {
        return baseMapper.selectDeptList(new LambdaQueryWrapper<SysDept>()
            .select(SysDept::getDeptId, SysDept::getDeptName, SysDept::getDeptCode, SysDept::getLeader)
            .eq(SysDept::getStatus, SystemConstants.NORMAL)
            .in(CollUtil.isNotEmpty(deptIds), SysDept::getDeptId, deptIds));
    }

    /**
     * 通过部门ID查询部门名称
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    @Override
    public String selectDeptNameByIds(String deptIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(deptIds, Convert::toLong)) {
            SysDeptVo vo = SpringUtils.getAopProxy(this).selectDeptById(id);
            if (ObjectUtil.isNotNull(vo)) {
                list.add(vo.getDeptName());
            }
        }
        return StringUtils.joinComma(list);
    }

    /**
     * 通过部门ID查询部门编码
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门编码串逗号分隔（与 deptIds 一一对应，顺序一致；id 对应部门不存在或未启用时该位为空串）
     */
    @Override
    public String selectDeptCodeByIds(String deptIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(deptIds, Convert::toLong)) {
            SysDeptVo vo = SpringUtils.getAopProxy(this).selectDeptById(id);
            // 不存在的部门编码位留空（与 selectDeptNameByIds 的"按顺序、不补 null"语义保持一致）
            list.add(ObjectUtil.isNotNull(vo) ? vo.getDeptCode() : "");
        }
        return StringUtils.joinComma(list);
    }

    /**
     * 根据ID查询所有子部门数（正常状态）
     *
     * @param deptId 部门ID
     * @return 子部门数
     */
    @Override
    public long selectNormalChildrenDeptById(Long deptId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<SysDept>()
            .eq(SysDept::getStatus, SystemConstants.NORMAL)
            .apply(DataBaseHelper.findInSet(deptId, "ancestors")));
    }

    /**
     * 是否存在子节点
     *
     * @param deptId 部门ID
     * @return 结果
     */
    @Override
    public boolean hasChildByDeptId(Long deptId) {
        return baseMapper.exists(new LambdaQueryWrapper<SysDept>()
            .eq(SysDept::getParentId, deptId));
    }

    /**
     * 查询部门是否存在用户
     *
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean checkDeptExistUser(Long deptId) {
        return userMapper.exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getDeptId, deptId));
    }

    /**
     * 校验部门名称是否唯一
     *
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public boolean checkDeptNameUnique(SysDeptBo dept) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysDept>()
            .eq(SysDept::getDeptName, dept.getDeptName())
            .eq(SysDept::getParentId, dept.getParentId())
            .ne(ObjectUtil.isNotNull(dept.getDeptId()), SysDept::getDeptId, dept.getDeptId()));
        return !exist;
    }

    /**
     * 校验部门编码（{@code dept_code}）是否唯一
     *
     * <p>同租户（{@code tenant_id}）内 {@code dept_code} 不允许重复。{@code deptCode} 留空时跳过校验。
     * 数据库唯一索引 {@code uk_sys_dept_tenant_code} 兜底，本方法仅做友好提示前置。</p>
     *
     * @param dept 部门信息
     * @return true 唯一 / false 重复
     */
    @Override
    public boolean checkDeptCodeUnique(SysDeptBo dept) {
        // deptCode 留空时跳过校验：与 uk_sys_dept_tenant_code 索引"允许多个 NULL"语义一致
        if (StringUtils.isBlank(dept.getDeptCode())) {
            return true;
        }
        // SysDeptBo 继承 BaseEntity（无 tenantId 字段），从当前登录/动态租户上下文取
        // 租户功能未启用或无登录态时返回 null/空，eq 条件会自动跳过（数据库唯一索引兜底）
        String currentTenantId = TenantHelper.getTenantId();
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysDept>()
            .eq(StringUtils.isNotBlank(currentTenantId), SysDept::getTenantId, currentTenantId)
            .eq(SysDept::getDeptCode, dept.getDeptCode())
            .ne(ObjectUtil.isNotNull(dept.getDeptId()), SysDept::getDeptId, dept.getDeptId()));
        if (exist) {
            log.warn("[checkDeptCodeUnique] 部门编码重复 tenantId={}, deptCode={}, excludeDeptId={}",
                currentTenantId, dept.getDeptCode(), dept.getDeptId());
        }
        return !exist;
    }

    /**
     * 校验部门是否有数据权限
     *
     * @param deptId 部门id
     */
    @Override
    public void checkDeptDataScope(Long deptId) {
        if (ObjectUtil.isNull(deptId)) {
            return;
        }
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return;
        }
        if (baseMapper.countDeptById(deptId) == 0) {
            throw new ServiceException("没有权限访问部门数据！");
        }
    }

    /**
     * 新增保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_DEPT_AND_CHILD, allEntries = true)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDept(SysDeptBo bo) {
        SysDept info = baseMapper.selectById(bo.getParentId());
        // 如果父节点不为正常状态,则不允许新增子节点
        if (!SystemConstants.NORMAL.equals(info.getStatus())) {
            throw new ServiceException("部门停用，不允许新增");
        }
        SysDept dept = MapstructUtils.convert(bo, SysDept.class);
        dept.setAncestors(info.getAncestors() + StringUtils.SEPARATOR + dept.getParentId());
        int rows = baseMapper.insert(dept);
        // 新增部门扩展（ln_pro 独有字段，可选）
        SysDeptExt ext = bo.getExt();
        if (ext != null) {
            ext.setDeptId(dept.getDeptId());
            deptExtService.saveOrUpdate(ext);
        }
        if (rows > 0) {
            publishDeptChange(PermissionChangeEvent.ChangeType.INSERT);
        }
        return rows;
    }

    /**
     * 修改保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT, key = "#bo.deptId"),
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT_AND_CHILD, allEntries = true)
    })
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDept(SysDeptBo bo) {
        SysDept dept = MapstructUtils.convert(bo, SysDept.class);
        SysDept oldDept = baseMapper.selectById(dept.getDeptId());
        if (!oldDept.getParentId().equals(dept.getParentId())) {
            // 如果是新父部门 则校验是否具有新父部门权限 避免越权
            this.checkDeptDataScope(dept.getParentId());
            SysDept newParentDept = baseMapper.selectById(dept.getParentId());
            if (ObjectUtil.isNotNull(newParentDept) && ObjectUtil.isNotNull(oldDept)) {
                String newAncestors = newParentDept.getAncestors() + StringUtils.SEPARATOR + newParentDept.getDeptId();
                String oldAncestors = oldDept.getAncestors();
                dept.setAncestors(newAncestors);
                updateDeptChildren(dept.getDeptId(), newAncestors, oldAncestors);
            }
        } else {
            dept.setAncestors(oldDept.getAncestors());
        }
        int result = baseMapper.updateById(dept);
        // 如果部门状态为启用，且部门祖级列表不为空，且部门祖级列表不等于根部门祖级列表（如果部门祖级列表不等于根部门祖级列表，则说明存在上级部门）
        if (SystemConstants.NORMAL.equals(dept.getStatus())
            && StringUtils.isNotEmpty(dept.getAncestors())
            && !StringUtils.equals(SystemConstants.ROOT_DEPT_ANCESTORS, dept.getAncestors())) {
            // 如果该部门是启用状态，则启用该部门的所有上级部门
            updateParentDeptStatusNormal(dept);
        }
        // 新增/更新部门扩展（ln_pro 独有字段，可选）
        SysDeptExt ext = bo.getExt();
        if (ext != null) {
            ext.setDeptId(bo.getDeptId());
            deptExtService.saveOrUpdate(ext);
        }
        if (result > 0) {
            publishDeptChange(PermissionChangeEvent.ChangeType.UPDATE);
        }
        return result;
    }

    /**
     * 修改该部门的父级部门状态
     *
     * @param dept 当前部门
     */
    private void updateParentDeptStatusNormal(SysDept dept) {
        String ancestors = dept.getAncestors();
        Long[] deptIds = Convert.toLongArray(ancestors);
        baseMapper.update(null, new LambdaUpdateWrapper<SysDept>()
            .set(SysDept::getStatus, SystemConstants.NORMAL)
            .in(SysDept::getDeptId, Arrays.asList(deptIds)));
    }

    /**
     * 修改子元素关系
     *
     * @param deptId       被修改的部门ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    private void updateDeptChildren(Long deptId, String newAncestors, String oldAncestors) {
        List<SysDept> children = baseMapper.selectList(new LambdaQueryWrapper<SysDept>()
            .apply(DataBaseHelper.findInSet(deptId, "ancestors")));
        List<SysDept> list = new ArrayList<>();
        for (SysDept child : children) {
            SysDept dept = new SysDept();
            dept.setDeptId(child.getDeptId());
            dept.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
            list.add(dept);
        }
        if (CollUtil.isNotEmpty(list)) {
            if (baseMapper.updateBatchById(list)) {
                list.forEach(dept -> CacheUtils.evict(CacheNames.SYS_DEPT, dept.getDeptId()));
            }
        }
    }

    /**
     * 删除部门管理信息
     *
     * @param deptId 部门ID
     * @return 结果
     */
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT, key = "#deptId"),
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT_AND_CHILD, key = "#deptId")
    })
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeptById(Long deptId) {
        int rows = baseMapper.deleteById(deptId);
        // 同步删除部门扩展（sys_dept_ext 1:1 关联，无 FK 需手动删）
        deptExtService.deleteByDeptId(deptId);
        if (rows > 0) {
            publishDeptChange(PermissionChangeEvent.ChangeType.DELETE);
        }
        return rows;
    }

    /**
     * 发布部门变更事件（走全网广播）
     *
     * <p><b>为什么用广播</b>：部门树决定了每个用户的数据权限范围
     * （全部 / 自定义 / 本部门 / 本部门及以下），部门移动、改名、启停、
     * 删除都会改变"哪些数据可见"，影响面难以精确枚举，因此统一广播，
     * 由客户端刷新登录用户后重新计算数据范围</p>
     */
    private void publishDeptChange(PermissionChangeEvent.ChangeType changeType) {
        try {
            eventPublisher.publishEvent(new PermissionChangeEvent(this, null, changeType));
        } catch (Exception e) {
            log.warn("部门变更事件发布失败: {}", e.getMessage());
        }
    }

}
