package io.gitee.chenshaogui.cloudauth.system.helper;

import cn.hutool.core.collection.CollUtil;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysMenuMapper;
import io.gitee.chenshaogui.cloudauth.system.utils.SystemUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单权限范围校验器（Component）
 *
 * <p>承担原 {@code ISysMenuService.isMenuInUserAdminScope(...)} 的语义，
 * 不依赖 {@code ISysMenuService}，从根源切断
 * {@code SysRoleServiceImpl → ISysMenuService} 的循环依赖。</p>
 *
 * <p>判定规则：超管/租管永远 true；客户端管理员需满足"菜单祖先链中
 * 存在其管理的某个客户端的 APP 菜单"。</p>
 */
@Component
@RequiredArgsConstructor
public class MenuScopeChecker {

    private final SysMenuMapper sysMenuMapper;
    private final SysClientRoleMapper sysClientRoleMapper;
    private final SysClientMenuHelper clientMenuHelper;

    /**
     * 当前用户是否是"客户端管理员"（负责某个客户端的管理员角色 {clientId}-admin）
     *
     * <p>用于区分两类非超管用户，避免"管理范围"限制误伤平台侧角色：
     * 客户端管理员 → 只能在自身管理应用范围内操作；
     * 平台侧非超管角色（如仅被授予菜单管理权限的运维角色）→ 不受应用范围限制</p>
     *
     * @param userId 用户ID
     * @return true=客户端管理员
     */
    public boolean isClientAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return CollUtil.isNotEmpty(sysClientRoleMapper.selectAdminClientIdsByUserId(userId));
    }

    /**
     * 判断菜单是否在用户的客户端管理范围内（用于角色分配/菜单新增等服务端防御）
     *
     * @param userId 当前操作用户ID
     * @param menuId 目标菜单ID
     * @return true=有管理权（允许）
     */
    public boolean isMenuInUserAdminScope(Long userId, Long menuId) {
        if (menuId == null) {
            return false;
        }
        if (LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId)) {
            return true;
        }
        // 1. 取菜单祖先链（含自身）
        List<Long> ancestorIds = new ArrayList<>(sysMenuMapper.getAncestorMenuIds(menuId));
        if (!ancestorIds.contains(menuId)) {
            ancestorIds.add(menuId);
        }
        if (CollUtil.isEmpty(ancestorIds)) {
            return false;
        }
        // 2. 祖先链中是否存在被激活客户端绑定的应用菜单
        List<SysClientVo> boundClients = clientMenuHelper.queryByMenuIds(ancestorIds);
        if (CollUtil.isEmpty(boundClients)) {
            return false;
        }
        Set<String> boundClientIds = boundClients.stream()
            .map(SysClientVo::getClientId)
            .collect(Collectors.toSet());
        // 3. 当前用户是否是上述某个应用所属客户端的管理员
        List<String> adminClientIds = sysClientRoleMapper.selectAdminClientIdsByUserId(userId);
        return adminClientIds.stream().anyMatch(boundClientIds::contains);
    }
}
