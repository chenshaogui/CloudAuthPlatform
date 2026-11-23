package io.gitee.chenshaogui.cloudauth.system.utils;

import cn.hutool.core.collection.CollUtil;
import io.gitee.chenshaogui.cloudauth.common.core.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysRoleVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysRoleService;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 系统工具类相关处理
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class SystemUtil {

    /**
     * 是否为租户管理员
     *
     * @return 结果
     */
    public static boolean isTenantAdmin(Long userId) {
        ISysRoleService sysRoleService = SpringUtils.getBean(ISysRoleService.class);
        List<SysRoleVo> rolePermission = sysRoleService.selectRolesByUserId(userId);
        if (CollUtil.isEmpty(rolePermission)) {
            return false;
        }
        return rolePermission.stream()
            .anyMatch(role -> role != null && TenantConstants.TENANT_ADMIN_ROLE_KEY.equals(role.getRoleKey()));
    }

}
