package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.system.domain.SysMenuExt;

/**
 * 菜单扩展信息 服务层
 *
 * <p>与 sys_menu 1:1 关联，存储来自 ln_pro sys_sec_operation 的独有字段
 * （老系统桌面/布局/类名/日志配置等）。</p>
 *
 * @author Lion Li
 */
public interface ISysMenuExtService {

    /**
     * 根据 menuId 查询扩展信息
     *
     * @param menuId 菜单ID（sys_menu.menu_id）
     * @return 扩展信息，若不存在返回 null
     */
    SysMenuExt queryByMenuId(Long menuId);

    /**
     * 新增或更新扩展信息（按 menuId 主键幂等）
     *
     * @param ext 扩展信息
     * @return true 成功 / false 失败
     */
    boolean saveOrUpdate(SysMenuExt ext);

    /**
     * 根据 menuId 删除扩展信息
     *
     * @param menuId 菜单ID
     * @return true 成功 / false 失败
     */
    boolean deleteByMenuId(Long menuId);

}