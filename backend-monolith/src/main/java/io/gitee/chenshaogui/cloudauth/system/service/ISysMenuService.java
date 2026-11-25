package io.gitee.chenshaogui.cloudauth.system.service;

import cn.hutool.core.lang.tree.Tree;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMenuBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.RouterVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMenuExportVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMenuVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * 菜单 业务层
 *
 * @author Lion Li
 */
public interface ISysMenuService {

    /**
     * 根据用户查询系统菜单列表
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<SysMenuVo> selectMenuList(Long userId);

    /**
     * 根据用户查询系统菜单列表
     *
     * @param menu   菜单信息
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<SysMenuVo> selectMenuList(SysMenuBo menu, Long userId);

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    Set<String> selectMenuPermsByUserId(Long userId);

    /**
     * 根据角色ID查询权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    Set<String> selectMenuPermsByRoleId(Long roleId);

    /**
     * 根据用户ID查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<SysMenu> selectMenuTreeByUserId(Long userId);

    /**
     * 根据用户ID以及客户端id查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<RouterVo> selectMenuRoutersByClient(Long userId, String clientId);

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId 角色ID
     * @return 选中菜单列表
     */
    List<Long> selectMenuListByRoleId(Long roleId);

    /**
     * 根据租户套餐ID查询菜单树信息
     *
     * @param packageId 租户套餐ID
     * @return 选中菜单列表
     */
    List<Long> selectMenuListByPackageId(Long packageId);

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    List<RouterVo> buildMenus(List<SysMenu> menus);

    /**
     * 构建前端所需要下拉树结构
     *
     * @param menus 菜单列表
     * @return 下拉树结构列表
     */
    List<Tree<Long>> buildMenuTreeSelect(List<SysMenuVo> menus);

    /**
     * 根据菜单ID查询信息
     *
     * @param menuId 菜单ID
     * @return 菜单信息
     */
    SysMenuVo selectMenuById(Long menuId);

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    boolean hasChildByMenuId(Long menuId);

    /**
     * 是否存在菜单子节点
     *
     * @param menuIds 菜单ID串
     * @return 结果 true 存在 false 不存在
     */
    boolean hasChildByMenuId(List<Long> menuIds);

    /**
     * 查询挡住删除的子菜单（按 parent_id 关联，与删除校验口径一致）
     *
     * @param parentIds   待删除的菜单ID
     * @param excludedIds 一并删除、可忽略的菜单ID
     * @return 子菜单列表
     */
    List<SysMenuVo> selectBlockingChildMenus(List<Long> parentIds, List<Long> excludedIds);

    /**
     * 查询应用菜单的完整子树（应用根 + 全部后代）
     *
     * <p>按 parent_id 逐层递归，不依赖 app_id 冗余字段，
     * 保证级联删除能勾选到界面上看不见的历史子菜单。</p>
     *
     * @param appMenuId 应用根菜单ID
     * @param userId    当前用户ID
     * @return 应用根及其全部后代
     */
    List<SysMenuVo> selectMenuSubtreeByMenuId(Long appMenuId, Long userId);

    /**
     * 查询菜单是否存在角色
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    boolean checkMenuExistRole(Long menuId);

    /**
     * 判断菜单是否属于应用子树（app_id 不为空）
     *
     * <p>用于删除保护策略区分：应用子树内的菜单允许删除并自动解绑角色关联，
     * 系统级菜单仍保留"已分配不允许删除"的保护</p>
     *
     * @param menuId 菜单ID
     * @return true=应用子树菜单
     */
    boolean isAppSubtreeMenu(Long menuId);

    /**
     * 新增保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    int insertMenu(SysMenuBo bo);

    /**
     * 修改保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    int updateMenu(SysMenuBo bo);

    /**
     * 删除菜单管理信息
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    int deleteMenuById(Long menuId);

    /**
     * 批量删除菜单管理信息
     *
     * @param menuIds 菜单ID串
     * @return 结果
     */
    void deleteMenuById(List<Long> menuIds);

    /**
     * 校验菜单名称是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean checkMenuNameUnique(SysMenuBo menu);

    /**
     * 校验菜单是否存在
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean checkMenuNameExists(SysMenuBo menu);

    /**
     * 校验菜单是否存在
     *
     * @param menu 菜单信息
     * @return 结果
     */
    SysMenu getExistsMenu(SysMenu menu);

    /**
     * 获取外部入口应用
     * @param userId 用户id
     * @return list
     */
    List<SysMenuVo> selectApplicationsByUserId(Long userId);

    /**
     * 获取所有顶级应用菜单
     *
     * <p>仅返回 {@code menuType='A'} 且 {@code parentId=0} 的菜单，
     * 不包含目录/菜单/按钮等子节点；按当前登录用户权限过滤
     * （超管/租管返回全量，普通用户需命中自身角色/客户端关联），但不限定是否已绑定客户端。
     * 适用于应用菜单管理页与客户端管理"绑定应用"下拉。</p>
     *
     * @param userId 当前登录用户ID
     * @return 应用菜单列表
     */
    List<SysMenuVo> selectAllAppMenus(Long userId);

    /**
     * 导出菜单列表
     *
     * @param menu 菜单信息
     * @return 菜单信息集合信息
     */
    List<SysMenuExportVo> selectMenuExportList(SysMenuBo menu, Long userId);

    /**
     * 导出菜单列表
     *
     * @param menuId 菜单id
     * @return 菜单信息集合信息
     */
    List<SysMenuExportVo> selectMenuExportListById(Long menuId);

    /**
     * 根据父级菜单id导入数据
     * @param file 导入文件
     * @param parentMenuId 父级菜单id
     */
    String importMenuDataByParent(MultipartFile file, boolean isUpdateSupport, Long parentMenuId);

    /**
     * 通过菜单id查询所有子菜单列表
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<SysMenuVo> selectMenuListByMenuId(SysMenuBo menu, Long userId);

    /**
     * 获取app类型菜单
     * @param userId 用户id
     * @return list
     */
    List<SysMenuVo> getNoBindAppMenus(Long userId);

    /**
     * 查询分配角色菜单时可用的菜单列表（系统级菜单 vs 应用菜单的隔离）
     *
     * <p>超管/租管返回全量；客户端管理员返回「其管理客户端的 APP 子树」内的菜单，
     * 用于 {@code roleMenuTreeselect} 过滤系统级菜单</p>
     *
     * @param userId 当前操作用户ID
     * @return 可选菜单列表
     */
    List<SysMenuVo> selectMenuListForRoleAssign(Long userId);

    /**
     * 判断菜单是否在用户的客户端管理范围内
     *
     * <p>超管/租管返回 true；客户端管理员需满足菜单祖先链中存在其管理的客户端 APP 菜单</p>
     *
     * @param userId 用户ID
     * @param menuId 菜单ID
     * @return true=有管理权
     */
    boolean isMenuInUserAdminScope(Long userId, Long menuId);

}
