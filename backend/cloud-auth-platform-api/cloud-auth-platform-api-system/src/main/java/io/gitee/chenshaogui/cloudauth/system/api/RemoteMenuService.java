package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteRouterVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteSysMenuVo;

import java.util.List;

/**
 * 菜单服务
 *
 * @author Lion Li
 */
public interface RemoteMenuService {

    /**
     * 根据菜单id查询菜单信息
     *
     * @param menuId 菜单id
     * @return 菜单列表
     */
    RemoteSysMenuVo getMenuInfoById(Long menuId);

    /**
     * 根据用户ID以及客户端id查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<RemoteRouterVo> selectMenuRoutersByClient(Long userId, String clientId);

}
