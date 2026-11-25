package io.gitee.chenshaogui.cloudauth.system.helper;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClientRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRoleMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserRole;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientRoleBo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysRoleBo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleMenuMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端管理员角色绑定器（Component）
 *
 * <p>承担原 {@code SysClientServiceImpl.bindAdminRoleInfo(...)} 的全部语义，
 * 直接基于 Mapper 完成 admin role 的创建（角色插入 + 客户端角色关联 + 角色菜单关联），
 * 不再依赖 {@code ISysRoleService}，从根源切断
 * {@code SysClientServiceImpl → ISysRoleService} 的循环依赖。</p>
 *
 * <p>复用原事务边界：{@code SysClientServiceImpl.insertByBo} 仍是外层
 * {@code @Transactional}，本类的写入自动参与同一事务。</p>
 *
 * <p>行为对比原实现：
 * <ul>
 *     <li>不调用 {@code ISysRoleService.insertRole} —— 不再触发 {@code SysRoleServiceImpl.insertRoleMenu} 中对 {@code LoginHelper.getUserId()} 的客户端菜单范围校验。
 *         这里创建的是系统级 admin role（角色权限字符为 {@code {clientId}-admin}），其菜单是预配置的
 *         {@code project.client-admin-menuIds} + 当前 client 的入口菜单，不应受到当前登录用户管理范围的限制。</li>
 *     <li>仅做"角色名称/权限字符是否重复"的前置校验，与原行为一致。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysClientAdminRoleBinder {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysClientRoleMapper sysClientRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    /**
     * 客户端管理员角色的固定菜单集合（系统预先配置的菜单ID，逗号分隔）
     */
    @Value("${project.client-admin-menuIds:}")
    private String clientAdminMenuIds;

    /**
     * 为新创建的客户端绑定管理员角色。
     *
     * <p>角色权限字符串规则：{@code {clientId}-admin}。若名称/权限已存在则跳过（幂等）。</p>
     *
     * <p>创建成功后会把<b>当前操作人</b>绑定到该管理员角色上：客户端列表、应用菜单等均按
     * "用户 → 角色 → 客户端"链路过滤，不绑定会导致操作人新建完客户端后看不到自己刚建的数据。</p>
     *
     * @param add 已完成主键回填的客户端实体
     * @return 新建的客户端管理员角色ID，未创建（已存在）时返回 {@code null}
     */
    public Long bindAdminRole(SysClient add) {
        SysRoleBo role = BeanUtil.toBean(add, SysRoleBo.class);
        role.setRoleName(add.getClientKey());
        // 角色权限字符串统一格式：{clientId}-{roleKey}，客户端管理员角色固定后缀 -admin
        role.setRoleKey(role.getClientId() + "-admin");

        // 组装菜单列表：固定菜单集合 + 本客户端入口菜单
        if (StringUtils.isNotBlank(clientAdminMenuIds)) {
            List<Long> menuIdList = StringUtils.splitTo(clientAdminMenuIds, ",", Convert::toLong);
            menuIdList.add(add.getMenuId());
            role.setMenuIds(menuIdList.toArray(new Long[0]));
        } else {
            role.setMenuIds(new Long[]{add.getMenuId()});
        }

        // 前置唯一性校验（与原行为一致：已存在则跳过，不抛异常）
        if (!checkRoleNameUnique(role)) {
            log.info("当前角色名称【{}】已存在", role.getRoleName());
            return null;
        }
        if (!checkRoleKeyUnique(role)) {
            log.info("当前角色权限字符【{}】已存在", role.getRoleKey());
            return null;
        }
        role.setRoleSort(getRoleSort());

        // 直接走 Mapper，避免触发 SysRoleServiceImpl 的菜单范围校验
        // （admin role 的菜单是系统行为，不应受当前登录用户管理范围限制）
        SysRole sysRole = MapstructUtils.convert(role, SysRole.class);
        sysRoleMapper.insert(sysRole);
        role.setRoleId(sysRole.getRoleId());

        // 客户端-角色关联
        SysClientRoleBo clientRoleBo = BeanUtil.toBean(role, SysClientRoleBo.class);
        clientRoleBo.setRoleId(sysRole.getRoleId());
        sysClientRoleMapper.insert(MapstructUtils.convert(clientRoleBo, SysClientRole.class));

        // 角色-菜单关联
        insertRoleMenu(role);

        // 将创建该客户端的当前操作人绑定为客户端管理员，保证其能看到新建的客户端数据
        bindCreatorAsClientAdmin(sysRole.getRoleId());

        return sysRole.getRoleId();
    }

    /**
     * 将当前操作人绑定到客户端管理员角色（{clientId}-admin）
     *
     * <p>超管 / 租户管理员本身可查看全部客户端，跳过绑定，避免额外污染其角色列表。</p>
     *
     * @param roleId 客户端管理员角色ID
     */
    private void bindCreatorAsClientAdmin(Long roleId) {
        if (roleId == null) {
            return;
        }
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            return;
        }
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return;
        }
        boolean exists = sysUserRoleMapper.exists(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getUserId, userId)
            .eq(SysUserRole::getRoleId, roleId));
        if (exists) {
            return;
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        sysUserRoleMapper.insert(userRole);
        log.info("已将当前操作人 userId={} 绑定为客户端管理员角色 roleId={}", userId, roleId);
    }

    /**
     * 校验角色名称是否唯一
     */
    private boolean checkRoleNameUnique(SysRoleBo role) {
        return !sysRoleMapper.exists(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleName, role.getRoleName())
            .ne(role.getRoleId() != null, SysRole::getRoleId, role.getRoleId()));
    }

    /**
     * 校验角色权限字符是否唯一
     */
    private boolean checkRoleKeyUnique(SysRoleBo role) {
        return !sysRoleMapper.exists(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleKey, role.getRoleKey())
            .ne(role.getRoleId() != null, SysRole::getRoleId, role.getRoleId()));
    }

    /**
     * 获取下一个可用的角色排序号
     */
    private Integer getRoleSort() {
        SysRole sysRoleVo = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .orderByDesc(SysRole::getRoleSort)
            .last("LIMIT 1"));
        if (sysRoleVo == null) {
            return 0;
        }
        return sysRoleVo.getRoleSort() == null ? 0 : sysRoleVo.getRoleSort() + 1;
    }

    /**
     * 写入角色-菜单关联（不参与外层事务范围校验，admin role 的菜单由系统配置）
     */
    private void insertRoleMenu(SysRoleBo role) {
        Long[] menuIds = role.getMenuIds();
        if (menuIds == null || menuIds.length == 0) {
            return;
        }
        List<SysRoleMenu> list = new ArrayList<>();
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getRoleId());
            rm.setMenuId(menuId);
            list.add(rm);
        }
        if (CollUtil.isNotEmpty(list)) {
            sysRoleMenuMapper.insertBatch(list);
        }
    }
}
