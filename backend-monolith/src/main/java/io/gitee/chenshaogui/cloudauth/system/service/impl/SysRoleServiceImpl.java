package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.system.domain.*;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientRoleBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientRoleVo;
import io.gitee.chenshaogui.cloudauth.system.helper.MenuScopeChecker;
import io.gitee.chenshaogui.cloudauth.system.mapper.*;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheNames;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysRoleBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysRoleVo;
import io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeEvent;
import io.gitee.chenshaogui.cloudauth.system.service.ISysRoleService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色 业务层处理
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class SysRoleServiceImpl implements ISysRoleService {

    private final SysRoleMapper baseMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysClientRoleMapper sysClientRoleMapper;

    private final ISysClientRoleService sysClientRoleService;

    /**
     * 菜单管理范围校验：抽离到 helper，避免 SysRoleServiceImpl ↔ SysMenuServiceImpl 循环依赖
     */
    private final MenuScopeChecker menuScopeChecker;

    /**
     * Spring 事件发布器（P0 权限同步机制使用）
     * <p>由 Spring 自动注入，用于发布 {@link PermissionChangeEvent}</p>
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询角色列表
     *
     * @param role      查询条件
     * @param pageQuery 分页参数
     * @return 角色分页列表
     */
    @Override
    public TableDataInfo<SysRoleVo> selectPageRoleList(SysRoleBo role, PageQuery pageQuery) {
        Page<SysRoleVo> page = baseMapper.selectPageRoleList(pageQuery.build(), this.buildQueryWrapper(role));
        List<SysRoleVo> records = page.getRecords();
        if(records.isEmpty()){
            return TableDataInfo.build(page);
        }
        List<Long> roleIdList = records.stream().map(SysRoleVo::getRoleId).toList();
        SysClientRoleBo clientRoleBo = new SysClientRoleBo();
        clientRoleBo.setRoleIdList(roleIdList);
        List<SysClientRoleVo> clientRoleVoList = sysClientRoleService.queryList(clientRoleBo);
        Map<Long, String> clientRoleMapCache = clientRoleVoList.stream()
            .filter(vo -> vo.getRoleId() != null && vo.getClientId() != null)
            .collect(Collectors.toMap(
                SysClientRoleVo::getRoleId,
                SysClientRoleVo::getClientId,
                (oldValue, newValue) -> newValue
            ));
        for (SysRoleVo record : records) {
            record.setClientId(clientRoleMapCache.get(record.getRoleId()));
        }
        return TableDataInfo.build(page);
    }

    /**
     * 根据条件查询角色数据
     *
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    @Override
    public List<SysRoleVo> selectRoleList(SysRoleBo role) {
        return baseMapper.selectRoleList(this.buildQueryWrapper(role));
    }

    private Wrapper<SysRole> buildQueryWrapper(SysRoleBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysRole> wrapper = Wrappers.lambdaQuery();
        // 管理员显示所有客户端信息 不是管理员 按用户id过滤客户端
        if (!(LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin())) {
            Long userId = LoginHelper.getUserId();
            // 仅显示用户被授予客户端管理员（{clientId}-admin）的角色
            List<Long> roleIds = sysClientRoleMapper.selectAdminRoleIdsByUserId(userId);
            if (CollUtil.isEmpty(roleIds)) {
                wrapper.apply("1=0");
            } else {
                wrapper.in(SysRole::getRoleId, roleIds);
            }
        }
        wrapper.eq(ObjectUtil.isNotNull(bo.getRoleId()), SysRole::getRoleId, bo.getRoleId())
            .like(StringUtils.isNotBlank(bo.getRoleName()), SysRole::getRoleName, bo.getRoleName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SysRole::getStatus, bo.getStatus())
            .like(StringUtils.isNotBlank(bo.getRoleKey()), SysRole::getRoleKey, bo.getRoleKey())
            .between(params.get("beginTime") != null && params.get("endTime") != null,
                SysRole::getCreateTime, params.get("beginTime"), params.get("endTime"))
            .orderByAsc(SysRole::getRoleSort).orderByAsc(SysRole::getCreateTime);
        return wrapper;
    }

    /**
     * 根据用户ID查询角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRolesByUserId(Long userId) {
        return baseMapper.selectRolesByUserId(userId);
    }

    /**
     * 根据用户ID查询角色列表(包含被授权状态)
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRolesAuthByUserId(Long userId) {
        List<SysRoleVo> userRoles = baseMapper.selectRolesByUserId(userId);
        List<SysRoleVo> roles = selectRoleAll();
        // 使用HashSet提高查找效率
        Set<Long> userRoleIds = StreamUtils.toSet(userRoles, SysRoleVo::getRoleId);
        for (SysRoleVo role : roles) {
            if (userRoleIds.contains(role.getRoleId())) {
                role.setFlag(true);
            }
        }
        return roles;
    }

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        List<SysRoleVo> perms = baseMapper.selectRolesByUserId(userId);
        Set<String> permsSet = new HashSet<>();
        for (SysRoleVo perm : perms) {
            if (ObjectUtil.isNotNull(perm)) {
                permsSet.addAll(StringUtils.splitList(perm.getRoleKey().trim()));
            }
        }
        return permsSet;
    }

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRoleAll() {
        return this.selectRoleList(new SysRoleBo());
    }

    /**
     * 根据用户ID获取角色选择框列表
     *
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    @Override
    public List<Long> selectRoleListByUserId(Long userId) {
        List<SysRoleVo> list = baseMapper.selectRolesByUserId(userId);
        return StreamUtils.toList(list, SysRoleVo::getRoleId);
    }

    /**
     * 通过角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色对象信息
     */
    @Override
    public SysRoleVo selectRoleById(Long roleId) {
        SysRoleVo sysRoleVo = baseMapper.selectRoleById(roleId);

        SysClientRoleVo sysClientRoleVos = sysClientRoleService.getInfoByRoleId(roleId);
        if(sysClientRoleVos!=null){
            sysRoleVo.setClientId(sysClientRoleVos.getClientId());
        }

        return sysRoleVo;
    }

    /**
     * 获取角色最大的顺序
     * @return int
     */
    @Override
    public Integer getRoleSort(){
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<SysRole>()
            .orderByDesc(SysRole::getRoleSort)
            .last("LIMIT 1");
        SysRoleVo sysRoleVo = baseMapper.selectVoOne(queryWrapper);
        if(sysRoleVo == null){
            return 0;
        }
        return sysRoleVo.getRoleSort()==null?0:sysRoleVo.getRoleSort()+1;
    }

    /**
     * 通过角色ID串查询角色
     *
     * @param roleIds 角色ID串
     * @return 角色列表信息
     */
    @Override
    public List<SysRoleVo> selectRoleByIds(List<Long> roleIds) {
        return baseMapper.selectRoleList(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getStatus, SystemConstants.NORMAL)
            .in(CollUtil.isNotEmpty(roleIds), SysRole::getRoleId, roleIds));
    }

    /**
     * 校验角色名称是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleNameUnique(SysRoleBo role) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleName, role.getRoleName())
            .ne(ObjectUtil.isNotNull(role.getRoleId()), SysRole::getRoleId, role.getRoleId()));
        return !exist;
    }

    /**
     * 校验角色权限是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleKeyUnique(SysRoleBo role) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleKey, role.getRoleKey())
            .ne(ObjectUtil.isNotNull(role.getRoleId()), SysRole::getRoleId, role.getRoleId()));
        return !exist;
    }

    /**
     * 校验角色是否允许操作
     *
     * @param role 角色信息
     */
    @Override
    public void checkRoleAllowed(SysRoleBo role) {
        if (ObjectUtil.isNotNull(role.getRoleId()) && LoginHelper.isSuperAdmin(role.getRoleId())) {
            throw new ServiceException("不允许操作超级管理员角色");
        }
        String[] keys = new String[]{TenantConstants.SUPER_ADMIN_ROLE_KEY, TenantConstants.TENANT_ADMIN_ROLE_KEY};
        // 新增不允许使用 管理员标识符
        if (ObjectUtil.isNull(role.getRoleId())
            && StringUtils.equalsAny(role.getRoleKey(), keys)) {
            throw new ServiceException("不允许使用系统内置管理员角色标识符!");
        }
        // 修改不允许修改 管理员标识符
        if (ObjectUtil.isNotNull(role.getRoleId())) {
            SysRole sysRole = baseMapper.selectById(role.getRoleId());
            // 如果标识符不相等 判断为修改了管理员标识符
            if (!StringUtils.equals(sysRole.getRoleKey(), role.getRoleKey())) {
                if (StringUtils.equalsAny(sysRole.getRoleKey(), keys)) {
                    throw new ServiceException("不允许修改系统内置管理员角色标识符!");
                } else if (StringUtils.equalsAny(role.getRoleKey(), keys)) {
                    throw new ServiceException("不允许使用系统内置管理员角色标识符!");
                }
            }
        }
    }

    /**
     * 校验角色是否有数据权限
     *
     * @param roleId 角色id
     */
    @Override
    public void checkRoleDataScope(Long roleId) {
        if (ObjectUtil.isNull(roleId)) {
            return;
        }
        this.checkRoleDataScope(Collections.singletonList(roleId));
    }

    /**
     * 校验角色是否有数据权限
     *
     * @param roleIds 角色ID列表（支持传单个ID）
     */
    @Override
    public void checkRoleDataScope(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds) || LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return;
        }
        long count = baseMapper.selectRoleCount(roleIds);
        if (count != roleIds.size()) {
            throw new ServiceException("没有权限访问部分角色数据！");
        }
    }

    /**
     * 通过角色ID查询角色使用数量
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    public long countUserRoleByRoleId(Long roleId) {
        return userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
    }

    /**
     * 新增保存角色信息
     *
     * @param bo 角色信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertRole(SysRoleBo bo) {
        SysRole role = MapstructUtils.convert(bo, SysRole.class);
        SysClientRoleBo clientRoleBo = BeanUtil.toBean(bo, SysClientRoleBo.class);
        // 新增角色信息
        int insertRows = baseMapper.insert(role);
        bo.setRoleId(role.getRoleId());
        // 增加客户端角色关联
        clientRoleBo.setRoleId(role.getRoleId());
        sysClientRoleService.insertByBo(clientRoleBo);
        // 修复 P0：返回值不再用 insertRoleMenu 的行数，避免 menuIds=[] 时被 Controller 误判为失败
        insertRoleMenu(bo);
        // P0 权限同步：新增角色暂未分配用户，无需发布事件；后续分配用户时由 insertAuthUsers 通知
        return insertRows;
    }

    /**
     * 修改保存角色信息
     *
     * @param bo 角色信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRole(SysRoleBo bo) {
        SysRole role = MapstructUtils.convert(bo, SysRole.class);

        if (SystemConstants.DISABLE.equals(role.getStatus()) && this.countUserRoleByRoleId(role.getRoleId()) > 0) {
            throw new ServiceException("角色已分配，不能禁用!");
        }
        // 修改角色信息
        int roleUpdateRows = baseMapper.updateById(role);
        // 增加客户端角色关联，当client为空时删除角色对应clientId,不为空时更新关联数据
        sysClientRoleService.deleteByRoleId(role.getRoleId());
        if(StringUtils.isNotBlank(bo.getClientId())){
            SysClientRoleBo clientRoleBo = BeanUtil.toBean(bo, SysClientRoleBo.class);
            sysClientRoleService.insertOrUpdateByBo(clientRoleBo);
        }

        // 修复 P0：menuIds 字段语义
        //   null  → 前端没传，不动菜单关联（避免误清空）
        //   []    → 前端明确传空，清空菜单关联
        //   [X]   → 替换菜单关联
        // 同时：返回值不再用 insertRoleMenu 的行数，避免 [] 时被 Controller 误判为失败
        if (bo.getMenuIds() != null) {
            if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()
                || !menuScopeChecker.isClientAdmin(LoginHelper.getUserId())) {
                roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getRoleId()));
            } else {
                // 客户端管理员只可见/可改其管理范围内的菜单（应用子树），范围外的既有角色菜单
                // （如系统预配置的系统管理、系统配置等权限）不在其管理树中展示，
                // 更新时必须原样保留，仅替换管理范围内的关联，避免保存角色时误清空系统级权限
                List<Long> existMenuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .select(SysRoleMenu::getMenuId)
                        .eq(SysRoleMenu::getRoleId, role.getRoleId()))
                    .stream().map(SysRoleMenu::getMenuId).toList();
                List<Long> inScopeMenuIds = existMenuIds.stream()
                    .filter(menuId -> menuScopeChecker.isMenuInUserAdminScope(LoginHelper.getUserId(), menuId))
                    .toList();
                if (!inScopeMenuIds.isEmpty()) {
                    roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, role.getRoleId())
                        .in(SysRoleMenu::getMenuId, inScopeMenuIds));
                }
            }
            insertRoleMenu(bo);
        }
        // P0 权限同步：角色被修改（菜单/数据权限/状态/客户端绑定），通知所有关联用户
        publishRoleChange(role.getRoleId(), PermissionChangeEvent.ChangeType.UPDATE);
        return roleUpdateRows;
    }

    /**
     * 修改角色状态
     *
     * @param roleId 角色ID
     * @param status 角色状态
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRoleStatus(Long roleId, String status) {
        if (SystemConstants.DISABLE.equals(status) && this.countUserRoleByRoleId(roleId) > 0) {
            throw new ServiceException("角色已分配，不能禁用!");
        }
        int rows = baseMapper.update(null,
            new LambdaUpdateWrapper<SysRole>()
                .set(SysRole::getStatus, status)
                .eq(SysRole::getRoleId, roleId));
        if (rows > 0) {
            // P0 权限同步：角色状态变更（启用/禁用），通知所有关联用户
            publishRoleChange(roleId, PermissionChangeEvent.ChangeType.STATUS_CHANGE);
        }
        return rows;
    }

    /**
     * 修改数据权限信息
     *
     * @param bo 角色信息
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#bo.roleId")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int authDataScope(SysRoleBo bo) {
        SysRole role = MapstructUtils.convert(bo, SysRole.class);
        // 修改角色信息
        baseMapper.updateById(role);
        // 删除角色与部门关联
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, role.getRoleId()));
        // 新增角色和部门信息（数据权限）
        int rows = insertRoleDept(bo);
        // P0 权限同步：数据权限变更，通知所有关联用户
        publishRoleChange(role.getRoleId(), PermissionChangeEvent.ChangeType.UPDATE);
        return rows;
    }

    /**
     * 新增角色菜单信息
     *
     * @param role 角色对象
     */
    private int insertRoleMenu(SysRoleBo role) {
        if(role.getMenuIds()==null || role.getMenuIds().length==0){
            return 1; // 修复 P0：空菜单集合也视为"成功插入 0 条"，不再让 Controller 误判失败
        }
        // M4 服务端防御：客户端管理员只能给其管理的应用分配菜单，禁止分配系统级菜单（绕过 UI 绕过）；
        // 平台侧非超管角色（非客户端管理员）不受应用范围限制
        Long currentUserId = LoginHelper.getUserId();
        boolean isAdmin = LoginHelper.isSuperAdmin(currentUserId) || LoginHelper.isTenantAdmin();
        if (!isAdmin && menuScopeChecker.isClientAdmin(currentUserId)) {
            for (Long menuId : role.getMenuIds()) {
                if (!menuScopeChecker.isMenuInUserAdminScope(currentUserId, menuId)) {
                    throw new ServiceException("菜单[" + menuId + "]不在您的客户端管理范围内，禁止分配");
                }
            }
        }
        int rows = 1;
        // 新增用户与角色管理
        List<SysRoleMenu> list = new ArrayList<>();
        for (Long menuId : role.getMenuIds()) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getRoleId());
            rm.setMenuId(menuId);
            list.add(rm);
        }
        if (CollUtil.isNotEmpty(list)) {
            rows = roleMenuMapper.insertBatch(list) ? list.size() : 0;
        }
        return rows;
    }

    /**
     * 新增角色部门信息(数据权限)
     *
     * @param role 角色对象
     */
    private int insertRoleDept(SysRoleBo role) {
        int rows = 1;
        // 新增角色与部门（数据权限）管理
        List<SysRoleDept> list = new ArrayList<>();
        for (Long deptId : role.getDeptIds()) {
            SysRoleDept rd = new SysRoleDept();
            rd.setRoleId(role.getRoleId());
            rd.setDeptId(deptId);
            list.add(rd);
        }
        if (CollUtil.isNotEmpty(list)) {
            rows = roleDeptMapper.insertBatch(list) ? list.size() : 0;
        }
        return rows;
    }

    /**
     * 通过角色ID删除角色
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#roleId")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRoleById(Long roleId) {
        // 删除角色与菜单关联
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        // 删除角色与部门关联
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId));
        // 删除与客户端的关联
        sysClientRoleMapper.delete(new LambdaQueryWrapper<SysClientRole>().eq(SysClientRole::getRoleId, roleId));
        int rows = baseMapper.deleteById(roleId);
        if (rows > 0) {
            // P0 权限同步：单角色删除，理论上在 controller 层已校验"无关联用户"，这里取小量
            publishRoleChange(roleId, PermissionChangeEvent.ChangeType.DELETE);
        }
        return rows;
    }

    /**
     * 批量删除角色信息
     *
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, allEntries = true)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRoleByIds(List<Long> roleIds) {
        checkRoleDataScope(roleIds);
        List<SysRole> roles = baseMapper.selectByIds(roleIds);
        for (SysRole role : roles) {
            checkRoleAllowed(BeanUtil.toBean(role, SysRoleBo.class));
            if (countUserRoleByRoleId(role.getRoleId()) > 0) {
                throw new ServiceException(String.format("%1$s已分配，不能删除!", role.getRoleName()));
            }
        }
        // 删除角色与菜单关联
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds));
        // 删除角色与部门关联
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().in(SysRoleDept::getRoleId, roleIds));
        // 删除与客户端的关联
        sysClientRoleMapper.delete(new LambdaQueryWrapper<SysClientRole>().in(SysClientRole::getRoleId, roleIds));
        int rows = baseMapper.deleteByIds(roleIds);
        if (rows > 0 && CollUtil.isNotEmpty(roleIds)) {
            // P0 权限同步：批量删除角色，逐个查询关联用户并聚合
            Set<Long> allUserIds = new HashSet<>();
            for (Long roleId : roleIds) {
                allUserIds.addAll(selectUserIdsByRoleIdSafe(roleId));
            }
            publishUserChange(new ArrayList<>(allUserIds), PermissionChangeEvent.ChangeType.DELETE);
        }
        return rows;
    }

    /**
     * 取消授权用户角色
     *
     * @param userRole 用户和角色关联信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAuthUser(SysUserRole userRole) {
        if (LoginHelper.getUserId().equals(userRole.getUserId())) {
            throw new ServiceException("不允许修改当前用户角色!");
        }
        int rows = userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getRoleId, userRole.getRoleId())
            .eq(SysUserRole::getUserId, userRole.getUserId()));
        if (rows > 0) {
            cleanOnlineUser(List.of(userRole.getUserId()));
            // P0 权限同步：单用户取消授权
            publishUserChange(List.of(userRole.getUserId()), PermissionChangeEvent.ChangeType.STATUS_CHANGE);
        }
        return rows;
    }

    /**
     * 批量取消授权用户角色
     *
     * @param roleId  角色ID
     * @param userIds 需要取消授权的用户数据ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAuthUsers(Long roleId, Long[] userIds) {
        List<Long> ids = List.of(userIds);
        if (ids.contains(LoginHelper.getUserId())) {
            throw new ServiceException("不允许修改当前用户角色!");
        }
        int rows = userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getRoleId, roleId)
            .in(SysUserRole::getUserId, ids));
        if (rows > 0) {
            cleanOnlineUser(ids);
            // P0 权限同步：批量取消授权
            publishUserChange(ids, PermissionChangeEvent.ChangeType.STATUS_CHANGE);
        }
        return rows;
    }

    /**
     * 批量选择授权用户角色
     *
     * @param roleId  角色ID
     * @param userIds 需要授权的用户数据ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertAuthUsers(Long roleId, Long[] userIds) {
        // 新增用户与角色管理
        int rows = 1;
        List<Long> ids = List.of(userIds);
        if (ids.contains(LoginHelper.getUserId())) {
            throw new ServiceException("不允许修改当前用户角色!");
        }
        List<SysUserRole> list = StreamUtils.toList(ids, userId -> {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            return ur;
        });
        if (CollUtil.isNotEmpty(list)) {
            rows = userRoleMapper.insertBatch(list) ? list.size() : 0;
        }
        if (rows > 0) {
            cleanOnlineUser(ids);
            // P0 权限同步：批量分配角色
            publishUserChange(ids, PermissionChangeEvent.ChangeType.STATUS_CHANGE);
        }
        return rows;
    }

    /**
     * 根据角色ID清除该角色关联的所有在线用户的登录状态（踢出在线用户）
     *
     * <p>
     * 先判断角色是否绑定用户，若无绑定则直接返回
     * 然后遍历当前所有在线Token，查找拥有该角色的用户并强制登出
     * 注意：在线用户量过大时，操作可能导致 Redis 阻塞，需谨慎调用
     * </p>
     *
     * @param roleId 角色ID
     */
    @Override
    public void cleanOnlineUserByRole(Long roleId) {
        // 如果角色未绑定用户 直接返回
        Long num = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        if (num == 0) {
            return;
        }
        // 当前操作人不踢：客户端管理员/超管修改自己所属角色时不应被立即登出，
        // 其权限变更已由 permission-sync 机制（Redis 标记 + 客户端刷新）通知
        Long currentUserId = LoginHelper.getUserId();
        log.info("cleanOnlineUserByRole roleId={} 跳过当前操作人 userId={}", roleId, currentUserId);
        List<String> keys = StpUtil.searchTokenValue("", 0, -1, false);
        if (CollUtil.isEmpty(keys)) {
            return;
        }
        // 角色关联的在线用户量过大会导致redis阻塞卡顿 谨慎操作
        keys.parallelStream().forEach(key -> {
            String token = StringUtils.substringAfterLast(key, ":");
            // 如果已经过期则跳过
            if (StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) < -1) {
                return;
            }
            LoginUser loginUser = LoginHelper.getLoginUser(token);
            if (ObjectUtil.isNull(loginUser) || CollUtil.isEmpty(loginUser.getRoles())) {
                return;
            }
            // 跳过当前操作人，避免"改完自己的角色立即被登出"
            if (loginUser.getUserId().equals(currentUserId)) {
                return;
            }
            if (loginUser.getRoles().stream().anyMatch(r -> r.getRoleId().equals(roleId))) {
                try {
                    StpUtil.logoutByTokenValue(token);
                } catch (NotLoginException ignored) {
                }
            }
        });
    }

    /**
     * 根据用户ID列表清除对应在线用户的登录状态（踢出指定用户）
     *
     * <p>
     * 遍历当前所有在线Token，匹配用户ID列表中的用户，强制登出
     * 注意：在线用户量过大时，操作可能导致 Redis 阻塞，需谨慎调用
     * </p>
     *
     * @param userIds 需要清除的用户ID列表
     */
    @Override
    public void cleanOnlineUser(List<Long> userIds) {
        List<String> keys = StpUtil.searchTokenValue("", 0, -1, false);
        if (CollUtil.isEmpty(keys)) {
            return;
        }
        // 角色关联的在线用户量过大会导致redis阻塞卡顿 谨慎操作
        keys.parallelStream().forEach(key -> {
            String token = StringUtils.substringAfterLast(key, ":");
            // 如果已经过期则跳过
            if (StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) < -1) {
                return;
            }
            LoginUser loginUser = LoginHelper.getLoginUser(token);
            if (ObjectUtil.isNull(loginUser)) {
                return;
            }
            if (userIds.contains(loginUser.getUserId())) {
                try {
                    StpUtil.logoutByTokenValue(token);
                } catch (NotLoginException ignored) {
                }
            }
        });
    }

    // ================== P0 权限同步机制辅助方法 ==================

    /**
     * 查询角色关联的用户ID列表（用于 P0 权限同步事件）
     * <p>任何异常都会被吞掉，避免阻塞业务流程</p>
     */
    private List<Long> selectUserIdsByRoleIdSafe(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        try {
            List<Long> ids = userRoleMapper.selectUserIdsByRoleId(roleId);
            return ids == null ? Collections.emptyList() : ids;
        } catch (Exception e) {
            log.warn("查询角色关联用户失败 roleId={}, err={}", roleId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 发布"按角色"权限变更事件：内部会先查询角色关联的用户再发事件
     */
    private void publishRoleChange(Long roleId, PermissionChangeEvent.ChangeType changeType) {
        List<Long> userIds = selectUserIdsByRoleIdSafe(roleId);
        publishUserChange(userIds, changeType);
    }

    /**
     * 发布"按用户ID列表"权限变更事件
     * <p>该方法在事务方法内部调用，{@code @TransactionalEventListener(AFTER_COMMIT)} 会保证
     * 仅在事务成功提交后才真正写 Redis 标记，避免脏读</p>
     */
    private void publishUserChange(List<Long> userIds, PermissionChangeEvent.ChangeType changeType) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        try {
            eventPublisher.publishEvent(new PermissionChangeEvent(this, userIds, changeType));
        } catch (Exception e) {
            // 事件发布失败不应阻塞业务
            log.warn("权限变更事件发布失败 type={}, err={}", changeType, e.getMessage());
        }
    }
}
