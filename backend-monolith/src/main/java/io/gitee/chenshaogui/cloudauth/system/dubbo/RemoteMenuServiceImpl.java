package io.gitee.chenshaogui.cloudauth.system.dubbo;

import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteMenuService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteRouterVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteSysMenuVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.RouterVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMenuVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 菜单服务
 *
 * @author sgchen
 */
@RequiredArgsConstructor
@Service

public class RemoteMenuServiceImpl implements RemoteMenuService {

    private final ISysMenuService sysMenuService;


    /**
     * 根据菜单id查询菜单信息
     *
     * @param menuId 菜单id
     * @return 菜单列表
     */
    @Override
    public RemoteSysMenuVo getMenuInfoById(Long menuId) {
        SysMenuVo sysMenuVo = sysMenuService.selectMenuById(menuId);
        return MapstructUtils.convert(sysMenuVo, RemoteSysMenuVo.class);
    }

    /**
     * 根据用户ID以及客户端id查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<RemoteRouterVo> selectMenuRoutersByClient(Long userId, String clientId) {
        List<RouterVo> routerVos = sysMenuService.selectMenuRoutersByClient(userId, clientId);
        return MapstructUtils.convert(routerVos, RemoteRouterVo.class);
    }
}
