package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HtmlUtil;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.exception.ExcelDataConvertException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.*;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.*;
import io.gitee.chenshaogui.cloudauth.system.helper.SysClientMenuHelper;
import io.gitee.chenshaogui.cloudauth.system.mapper.*;
import io.gitee.chenshaogui.cloudauth.system.utils.MenuUtil;
import io.gitee.chenshaogui.cloudauth.system.utils.SystemUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.common.core.constant.Constants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenuExt;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRoleMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.SysTenantPackage;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMenuBo;
import io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeEvent;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMenuExtService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMenuService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单 业务层处理
 *
 * @author Lion Li
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements ISysMenuService {

    private final SysMenuMapper baseMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysClientMapper clientMapper;
    private final SysClientRoleMapper sysClientRoleMapper;
    private final SysTenantPackageMapper tenantPackageMapper;
    private final ISysMenuExtService menuExtService;

    /**
     * 客户端只读查询助手：抽离到 helper，避免 SysMenuServiceImpl ↔ SysClientServiceImpl 循环依赖
     */
    private final SysClientMenuHelper sysClientMenuHelper;

    /**
     * Spring 事件发布器（P0 权限同步机制使用）
     * <p>菜单变更影响范围大（多角色×多用户），无法精确指定用户，
     * 故发"空受影响用户"事件，由 {@link io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeNotifier}
     * 转为全网广播标记 {@code perm_changed:ALL}</p>
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 菜单导入模板列头（与 {@link SysMenuImportVo} 字段声明顺序一致，Excel 按列序号映射字段），
     * 用于把单元格转换异常定位到具体中文列，避免只抛一句英文 "to class java.lang.Integer error"
     */
    private static final String[] MENU_IMPORT_HEADERS = {
        "菜单ID", "菜单名称", "父菜单ID", "父菜单名称", "显示顺序", "路由地址", "组件路径", "路由参数",
        "是否为外链", "是否缓存", "菜单类型", "显示状态", "权限标识", "菜单图标", "备注", "菜单状态"
    };

    /**
     * 根据用户查询系统菜单列表
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenuVo> selectMenuList(Long userId) {
        return selectMenuList(new SysMenuBo(), userId);
    }

    /**
     * 查询系统菜单列表
     *
     * <p>默认仅返回系统级菜单（{@code app_id IS NULL}，即不在任何应用子树下的菜单）；
     * 应用级菜单（{@code menuType='A'}）及其子菜单通过 {@code app_id} 归属应用菜单管理界面（{@code /getApplications}、
     * {@code /listByMenuId} 等）单独维护，避免父节点被过滤后子菜单变成孤儿。</p>
     *
     * <p>调用方显式传入 {@code menuType='A'} 时保留原行为（兼容应用级菜单查询）。</p>
     *
     * @param menu   菜单信息
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenuVo> selectMenuList(SysMenuBo menu, Long userId) {
        List<SysMenuVo> menuList;
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        // 管理员显示所有菜单信息 不是管理员 按用户id过滤菜单
        if (!(LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId))) {
            // 通过用户id获取角色id 通过角色id获取菜单id 然后in菜单
            wrapper.inSql(SysMenu::getMenuId, baseMapper.buildMenuByUserSql(userId));
        }
        // 默认仅返回系统级菜单（app_id 为 NULL，即不属于任何应用子树）；
        // 调用方显式指定 menuType='A' 时放行，应用菜单管理页应通过 /getApplications 等专用接口获取。
        if (!SystemConstants.TYPE_APP.equals(menu.getMenuType())) {
            wrapper.isNull(SysMenu::getAppId);
        }
        menuList = baseMapper.selectVoList(
            wrapper.like(StringUtils.isNotBlank(menu.getMenuName()), SysMenu::getMenuName, menu.getMenuName())
                .eq(StringUtils.isNotBlank(menu.getVisible()), SysMenu::getVisible, menu.getVisible())
                .eq(StringUtils.isNotBlank(menu.getStatus()), SysMenu::getStatus, menu.getStatus())
                .eq(StringUtils.isNotBlank(menu.getMenuType()), SysMenu::getMenuType, menu.getMenuType())
                .eq(ObjectUtil.isNotNull(menu.getParentId()), SysMenu::getParentId, menu.getParentId())
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum));
        return menuList;
    }

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        return baseMapper.selectMenuPermsByUserId(userId);
    }

    /**
     * 根据角色ID查询权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectMenuPermsByRoleId(Long roleId) {
        return baseMapper.selectMenuPermsByRoleId(roleId);
    }

    /**
     * 根据用户ID查询菜单
     *
     * @param userId 用户名称
     * @return 菜单列表
     */
    @Override
    public List<SysMenu> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus;
        if (LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId)) {
            menus = baseMapper.selectMenuTreeAll();
        } else {
            LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
            menus = baseMapper.selectList(
                wrapper.in(SysMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
                    .eq(SysMenu::getStatus, SystemConstants.NORMAL)
                    .inSql(SysMenu::getMenuId, baseMapper.buildMenuByUserSql(userId))
                    .orderByAsc(SysMenu::getParentId)
                    .orderByAsc(SysMenu::getOrderNum));
        }
        return getChildPerms(menus, Constants.TOP_PARENT_ID);
    }

    /**
     * 根据用户和客户端查询路由菜单
     *
     * @param userId 用户名称
     * @return 菜单列表
     */
    @Override
    public List<RouterVo> selectMenuRoutersByClient(Long userId, String clientId) {
        // 1.根据客户端id查询到应用菜单
        SysClientVo sysClientVo = sysClientMenuHelper.queryByClientId(clientId);
        SysMenu sysMenusTmp = baseMapper.selectById(sysClientVo.getMenuId());
        if(sysMenusTmp==null){
            return Collections.emptyList();
        }

        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
            // 直接按 app_id 过滤 = 该应用菜单下所有子菜单（M/C/F），依赖冗余字段 app_id（已通过 SQL 脚本回填）
            // 这里不再使用 SQL 函数 get_menu_child_ids 递归，性能更优（O(1) 索引查询）
            wrapper.in(SysMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
                .eq(SysMenu::getStatus, SystemConstants.NORMAL)
                .eq(SysMenu::getAppId, sysMenusTmp.getMenuId())
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum);
        if (!(LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId))) {
            wrapper.inSql(SysMenu::getMenuId, baseMapper.buildMenuByUserSql(userId));
        }
        List<SysMenu> menus = baseMapper.selectList(wrapper);
        // buildMenus 依赖 SysMenu.children 字段填充（递归父子关系）
        List<SysMenu> childPerms = getChildPerms(menus, sysMenusTmp.getMenuId());
        return buildMenus(childPerms);
    }

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId 角色ID
     * @return 选中菜单列表
     */
    @Override
    public List<Long> selectMenuListByRoleId(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        return baseMapper.selectMenuListByRoleId(roleId, role.getMenuCheckStrictly());
    }

    /**
     * 根据租户套餐ID查询菜单树信息
     *
     * @param packageId 租户套餐ID
     * @return 选中菜单列表
     */
    @Override
    public List<Long> selectMenuListByPackageId(Long packageId) {
        SysTenantPackage tenantPackage = tenantPackageMapper.selectById(packageId);
        List<Long> menuIds = StringUtils.splitTo(tenantPackage.getMenuIds(), Convert::toLong);
        if (CollUtil.isEmpty(menuIds)) {
            return List.of();
        }
        List<Long> parentIds = null;
        if (tenantPackage.getMenuCheckStrictly()) {
            parentIds = baseMapper.selectObjs(new LambdaQueryWrapper<SysMenu>()
                .select(SysMenu::getParentId)
                .in(SysMenu::getMenuId, menuIds), x -> {
                return Convert.toLong(x);
            });
        }
        return baseMapper.selectObjs(new LambdaQueryWrapper<SysMenu>()
            .select(SysMenu::getMenuId)
            .in(SysMenu::getMenuId, menuIds)
            .notIn(CollUtil.isNotEmpty(parentIds), SysMenu::getMenuId, parentIds), x -> {
            return Convert.toLong(x);
        });
    }

    /**
     * 构建前端路由所需要的菜单
     * 路由name命名规则 path首字母转大写 + id
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    @Override
    public List<RouterVo> buildMenus(List<SysMenu> menus) {
        List<RouterVo> routers = new LinkedList<>();
        for (SysMenu menu : menus) {
            String name = menu.getRouteName() + menu.getMenuId();
            RouterVo router = new RouterVo();
            router.setHidden("1".equals(menu.getVisible()));
            router.setName(name);
            router.setPath(menu.getRouterPath());
            router.setComponent(menu.getComponentInfo());
            router.setQuery(menu.getQueryParam());
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals("1", menu.getIsCache()), menu.getPath(), menu.getRemark()));
            List<SysMenu> cMenus = menu.getChildren();
            if (CollUtil.isNotEmpty(cMenus) && SystemConstants.TYPE_DIR.equals(menu.getMenuType())) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenus(cMenus));
            } else if (menu.isMenuFrame()) {
                String frameName = StringUtils.capitalize(menu.getPath()) + menu.getMenuId();
                router.setMeta(null);
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                children.setPath(menu.getPath());
                children.setComponent(menu.getComponent());
                children.setName(frameName);
                children.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals("1", menu.getIsCache()), menu.getPath(), menu.getRemark()));
                children.setQuery(menu.getQueryParam());
                childrenList.add(children);
                router.setChildren(childrenList);
            } else if (menu.getParentId().equals(Constants.TOP_PARENT_ID) && menu.isInnerLink()) {
                router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon()));
                router.setPath("/");
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                String routerPath = SysMenu.innerLinkReplaceEach(menu.getPath());
                String innerLinkName = StringUtils.capitalize(routerPath) + menu.getMenuId();
                children.setPath(routerPath);
                children.setComponent(SystemConstants.INNER_LINK);
                children.setName(innerLinkName);
                children.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), menu.getPath()));
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            routers.add(router);
        }
        return routers;
    }

    /**
     * 构建前端所需要下拉树结构
     *
     * @param menus 菜单列表
     * @return 下拉树结构列表
     */
    @Override
    public List<Tree<Long>> buildMenuTreeSelect(List<SysMenuVo> menus) {
        if (CollUtil.isEmpty(menus)) {
            return CollUtil.newArrayList();
        }
        return TreeBuildUtils.build(menus, (menu, tree) -> {
            Tree<Long> menuTree = tree.setId(menu.getMenuId())
                .setParentId(menu.getParentId())
                .setName(menu.getMenuName())
                .setWeight(menu.getOrderNum());
            menuTree.put("menuType", menu.getMenuType());
            menuTree.put("icon", menu.getIcon());
            menuTree.put("visible", menu.getVisible());
            menuTree.put("status", menu.getStatus());
        });
    }

    /**
     * 根据菜单ID查询信息
     *
     * @param menuId 菜单ID
     * @return 菜单信息
     */
    @Override
    public SysMenuVo selectMenuById(Long menuId) {
        SysMenuVo menu = baseMapper.selectVoById(menuId);
        if (menu != null) {
            // 回填 ln_pro 独有扩展字段（按需调用，单条读不影响列表性能）
            menu.setExt(menuExtService.queryByMenuId(menuId));
        }
        return menu;
    }

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    @Override
    public boolean hasChildByMenuId(Long menuId) {
        return baseMapper.exists(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId));
    }

    /**
     * 是否存在菜单子节点
     *
     * @param menuIds 菜单ID串
     * @return 结果
     */
    @Override
    public boolean hasChildByMenuId(List<Long> menuIds) {
        return baseMapper.exists(new LambdaQueryWrapper<SysMenu>().in(SysMenu::getParentId, menuIds).notIn(SysMenu::getMenuId, menuIds));
    }

    @Override
    public List<SysMenuVo> selectBlockingChildMenus(List<Long> parentIds, List<Long> excludedIds) {
        if (CollUtil.isEmpty(parentIds)) {
            return List.of();
        }
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
            .in(SysMenu::getParentId, parentIds);
        if (CollUtil.isNotEmpty(excludedIds)) {
            wrapper.notIn(SysMenu::getMenuId, excludedIds);
        }
        wrapper.orderByAsc(SysMenu::getParentId).orderByAsc(SysMenu::getOrderNum);
        return baseMapper.selectVoList(wrapper);
    }

    @Override
    public List<SysMenuVo> selectMenuSubtreeByMenuId(Long appMenuId, Long userId) {
        if (appMenuId == null || !checkAppMenuAccessible(appMenuId, userId)) {
            return List.of();
        }
        List<SysMenuVo> result = new ArrayList<>(baseMapper.selectVoList(
            new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getMenuId, appMenuId)));
        if (result.isEmpty()) {
            return List.of();
        }
        Set<Long> visited = new HashSet<>();
        visited.add(appMenuId);
        List<Long> frontier = List.of(appMenuId);
        // 按 parent_id 逐层向下收集，最多 20 层，防止脏数据成环
        for (int depth = 0; depth < 20 && !frontier.isEmpty(); depth++) {
            List<SysMenuVo> children = baseMapper.selectVoList(
                new LambdaQueryWrapper<SysMenu>().in(SysMenu::getParentId, frontier)
                    .orderByAsc(SysMenu::getParentId).orderByAsc(SysMenu::getOrderNum));
            if (CollUtil.isEmpty(children)) {
                break;
            }
            List<Long> next = new ArrayList<>();
            for (SysMenuVo child : children) {
                if (child.getMenuId() == null || !visited.add(child.getMenuId())) {
                    continue;
                }
                result.add(child);
                next.add(child.getMenuId());
            }
            if (next.isEmpty()) {
                break;
            }
            frontier = next;
        }
        return result;
    }

    /**
     * 校验当前用户是否有权管理该应用菜单（与 listByMenuId 保持同一口径）
     */
    private boolean checkAppMenuAccessible(Long appMenuId, Long userId) {
        if (LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId)) {
            return true;
        }
        List<SysClientVo> boundClients = sysClientMenuHelper.queryByMenuIds(List.of(appMenuId));
        if (CollUtil.isEmpty(boundClients)) {
            return false;
        }
        Set<String> accessibleClientIds = sysClientMenuHelper.queryListByUserId(userId).stream()
            .map(io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo::getClientId)
            .collect(Collectors.toSet());
        return boundClients.stream()
            .anyMatch(c -> accessibleClientIds.contains(c.getClientId()));
    }

    /**
     * 查询菜单使用数量
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    @Override
    public boolean checkMenuExistRole(Long menuId) {
        return roleMenuMapper.exists(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, menuId));
    }

    /**
     * 判断菜单是否属于应用子树（app_id 不为空）
     */
    @Override
    public boolean isAppSubtreeMenu(Long menuId) {
        if (menuId == null) {
            return false;
        }
        return baseMapper.exists(new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getMenuId, menuId)
            .isNotNull(SysMenu::getAppId));
    }

    /**
     * 新增保存菜单信息
     *
     * <p>系统级菜单与应用菜单的边界保护（M6）：
     * <ul>
     *     <li>根级应用菜单（type='A'）禁止在菜单管理中直接新增，必须通过【客户端管理】创建</li>
     *     <li>父级为应用菜单（或父级祖先链中存在应用菜单）时，仅允许该应用所属客户端的管理员（或超管/租管）新增</li>
     * </ul>
     *
     * @param bo 菜单信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertMenu(SysMenuBo bo) {
        // 1. 根级应用菜单（type='A'）只能由【客户端管理】流程创建
        if (SystemConstants.TYPE_APP.equals(bo.getMenuType())) {
            throw new ServiceException("应用菜单请通过【客户端管理】创建");
        }
        // 2. 应用子树归属校验：父级或其祖先为应用菜单时，需校验当前用户是否为该应用所属客户端的管理员
        if (bo.getParentId() != null && bo.getParentId() > 0) {
            SysMenu parent = baseMapper.selectById(bo.getParentId());
            if (parent != null) {
                Long parentAppMenuId = findAppMenuAncestor(parent.getMenuId());
                if (parentAppMenuId != null) {
                    Long currentUserId = LoginHelper.getUserId();
                    boolean isAdmin = LoginHelper.isSuperAdmin(currentUserId) || SystemUtil.isTenantAdmin(currentUserId);
                    if (!isAdmin) {
                        List<SysClientVo> boundClients = sysClientMenuHelper.queryByMenuIds(List.of(parentAppMenuId));
                        if (CollUtil.isEmpty(boundClients)) {
                            throw new ServiceException("父菜单未绑定客户端，无法在应用下新增菜单");
                        }
                        String clientId = boundClients.get(0).getClientId();
                        List<String> adminClientIds = sysClientRoleMapper.selectAdminClientIdsByUserId(currentUserId);
                        if (!adminClientIds.contains(clientId)) {
                            throw new ServiceException("仅该应用所属客户端的管理员可在应用下新增菜单");
                        }
                    }
                }
            }
        }
        // 3. 自动计算 app_id（写入冗余字段，便于按应用过滤）：
        //    - 顶级菜单（parent_id = 0）：app_id = NULL（系统级）
        //    - 父级为应用菜单：app_id = 父级 menu_id
        //    - 父级为应用子树内普通菜单：app_id = 父级 app_id（继承）
        //    - 父级为系统级菜单：app_id = NULL（系统级）
        bo.setAppId(resolveAppIdForInsert(bo));
        SysMenu menu = MapstructUtils.convert(bo, SysMenu.class);
        int rows = baseMapper.insert(menu);
        // 新增菜单扩展（ln_pro 独有字段，可选）
        SysMenuExt ext = bo.getExt();
        if (ext != null) {
            ext.setMenuId(menu.getMenuId());
            menuExtService.saveOrUpdate(ext);
        }
        if (rows > 0) {
            // 新增应用菜单后自动授权给该应用的客户端管理员角色（{clientId}-admin），
            // 免去管理员新增菜单后还要手工到"角色管理"再授权一次才能访问的麻烦
            autoGrantMenuToAppAdminRole(menu);
            // P0 权限同步：菜单新增广播（避免漏通知）
            publishMenuChange(PermissionChangeEvent.ChangeType.INSERT);
        }
        return rows;
    }

    /**
     * 新增应用菜单后自动授予该应用的客户端管理员角色（{clientId}-admin）
     *
     * <p>授权范围：新增菜单自身 + 应用子树内的祖先链（过滤 0 与空值），
     * 保证角色菜单树能正常渲染（父级缺失时子菜单会被菜单树构建逻辑丢弃）。</p>
     *
     * <p>失败只记录日志，不影响菜单新增主流程。</p>
     *
     * @param menu 已落库的菜单（含 menuId / appId）
     */
    private void autoGrantMenuToAppAdminRole(SysMenu menu) {
        try {
            // 系统级菜单（app_id 为空）不处理
            if (menu == null || menu.getMenuId() == null || menu.getAppId() == null) {
                return;
            }
            List<SysClientVo> boundClients = sysClientMenuHelper.queryByMenuIds(List.of(menu.getAppId()));
            if (CollUtil.isEmpty(boundClients)) {
                return;
            }
            Set<Long> grantMenuIds = new LinkedHashSet<>();
            grantMenuIds.add(menu.getMenuId());
            for (Long ancestorId : baseMapper.getAncestorMenuIds(menu.getMenuId())) {
                if (ancestorId != null && ancestorId > 0L) {
                    grantMenuIds.add(ancestorId);
                }
            }
            List<SysRoleMenu> inserts = new ArrayList<>();
            for (SysClientVo client : boundClients) {
                for (Long roleId : sysClientRoleMapper.selectAdminRoleIdsByClientId(client.getClientId())) {
                    for (Long menuId : grantMenuIds) {
                        boolean exists = roleMenuMapper.exists(new LambdaQueryWrapper<SysRoleMenu>()
                            .eq(SysRoleMenu::getRoleId, roleId)
                            .eq(SysRoleMenu::getMenuId, menuId));
                        if (!exists) {
                            SysRoleMenu roleMenu = new SysRoleMenu();
                            roleMenu.setRoleId(roleId);
                            roleMenu.setMenuId(menuId);
                            inserts.add(roleMenu);
                        }
                    }
                }
            }
            if (CollUtil.isNotEmpty(inserts)) {
                roleMenuMapper.insertBatch(inserts);
            }
        } catch (Exception e) {
            log.warn("新增菜单自动授权应用管理员角色失败 menuId={}, err={}", menu.getMenuId(), e.getMessage());
        }
    }

    /**
     * 修改保存菜单信息
     *
     * <p>系统级菜单与应用菜单的边界保护：
     * <ul>
     *     <li>已绑定客户端的应用菜单（A）禁止变更 {@code menu_type} 与 {@code parentId}</li>
     *     <li>系统菜单（M/C/F）禁止升级为应用类型（A）</li>
     * </ul>
     *
     * @param bo 菜单信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMenu(SysMenuBo bo) {
        // 范围保护：客户端管理员只能维护自己管理应用子树内的菜单，且迁移目标父级也必须在管理范围内；
        // 平台侧非超管角色（非客户端管理员）不受应用范围限制，避免误伤平台运维角色
        Long currentUserId = LoginHelper.getUserId();
        boolean isAdmin = LoginHelper.isSuperAdmin(currentUserId) || SystemUtil.isTenantAdmin(currentUserId);
        boolean isClientAdmin = CollUtil.isNotEmpty(sysClientRoleMapper.selectAdminClientIdsByUserId(currentUserId));
        if (!isAdmin && isClientAdmin) {
            if (!isMenuInUserAdminScope(currentUserId, bo.getMenuId())) {
                throw new ServiceException("菜单不在您的客户端管理范围内，禁止修改");
            }
            if (bo.getParentId() != null && bo.getParentId() > 0
                && !isMenuInUserAdminScope(currentUserId, bo.getParentId())) {
                throw new ServiceException("目标上级菜单不在您的客户端管理范围内，禁止迁移");
            }
        }
        SysMenu original = baseMapper.selectById(bo.getMenuId());
        if (original != null) {
            String oldType = original.getMenuType();
            String newType = bo.getMenuType();
            boolean oldIsApp = SystemConstants.TYPE_APP.equals(oldType);
            boolean newIsApp = SystemConstants.TYPE_APP.equals(newType);
            if (oldIsApp && !newIsApp) {
                throw new ServiceException("已绑定客户端的应用菜单不允许变更菜单类型");
            }
            if (!oldIsApp && newIsApp) {
                throw new ServiceException("不允许将系统菜单升级为应用菜单");
            }
            if (oldIsApp && !Objects.equals(bo.getParentId(), original.getParentId())) {
                throw new ServiceException("已绑定客户端的应用菜单不允许变更父级");
            }
            // parentId 变化 或 app_id 缺失时重新计算 app_id：
            //   - parentId 变化：含跨级迁移到应用子树 / 迁出应用子树
            //   - app_id 缺失：存量 / 迁移数据未经 insertMenu 写入；让"编辑保存"这一界面操作即可自动补全，
            //                   配合下方的 cascadeUpdateAppId 还能修好整棵后代，免去 DBA 跑一次性回填脚本
            // 用 effectiveParentId 兜底（bo.parentId 可能为 null 用 original 的；bo.getAppId() 在前端编辑时也可能为 null）
            Long effectiveParentId = bo.getParentId() != null ? bo.getParentId() : original.getParentId();
            if (!Objects.equals(bo.getParentId(), original.getParentId()) || original.getAppId() == null) {
                bo.setAppId(resolveAppIdByParentId(effectiveParentId));
            }
        }
        SysMenu menu = MapstructUtils.convert(bo, SysMenu.class);
        int rows = baseMapper.updateById(menu);
        // 新增/更新菜单扩展（ln_pro 独有字段，可选）
        SysMenuExt ext = bo.getExt();
        if (ext != null) {
            ext.setMenuId(bo.getMenuId());
            menuExtService.saveOrUpdate(ext);
        }
        if (rows > 0) {
            // parentId 变化导致 app_id 变化时，级联更新所有后代的 app_id（用 SQL 函数 get_menu_child_ids 递归）
            if (original != null
                && !Objects.equals(bo.getAppId(), original.getAppId())) {
                cascadeUpdateAppId(bo.getMenuId(), bo.getAppId());
            }
            // P0 权限同步：菜单修改广播
            publishMenuChange(PermissionChangeEvent.ChangeType.UPDATE);
        }
        return rows;
    }

    /**
     * 删除菜单管理信息
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMenuById(Long menuId) {
        int rows = baseMapper.deleteById(menuId);
        // 同步删除菜单扩展（sys_menu_ext 1:1 关联，无 FK 需手动删）
        menuExtService.deleteByMenuId(menuId);
        // 同步解绑角色-菜单关联：应用菜单新增时会自动授权给应用管理员角色，
        // 删除时必须一并清理，否则残留脏关联（角色菜单树出现空节点、统计异常）
        roleMenuMapper.deleteByMenuIds(List.of(menuId));
        if (rows > 0) {
            // P0 权限同步：菜单删除广播
            publishMenuChange(PermissionChangeEvent.ChangeType.DELETE);
        }
        return rows;
    }

    /**
     * 批量删除菜单管理信息
     *
     * @param menuIds 菜单ID串
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenuById(List<Long> menuIds) {
        int rows = baseMapper.deleteByIds(menuIds);
        // 同步批量删除菜单扩展（sys_menu_ext 1:1 关联，无 FK 需手动删）
        for (Long menuId : menuIds) {
            menuExtService.deleteByMenuId(menuId);
        }
        // 同步解绑角色-菜单关联，避免残留脏关联
        roleMenuMapper.deleteByMenuIds(menuIds);
        // 广播条件按"菜单实际删除行数"判断：原实现用角色关联清理行数判断，
        // 被删菜单若无任何角色关联（如刚新增尚未授权的菜单）则不会广播，导致客户端路由缓存不刷新
        if (rows > 0) {
            // P0 权限同步：菜单批量删除广播
            publishMenuChange(PermissionChangeEvent.ChangeType.DELETE);
        }
    }

    /**
     * 发布菜单变更事件（统一走全网广播路径）
     */
    private void publishMenuChange(PermissionChangeEvent.ChangeType changeType) {
        try {
            eventPublisher.publishEvent(new PermissionChangeEvent(this, null, changeType));
        } catch (Exception e) {
            log.warn("菜单变更事件发布失败: {}", e.getMessage());
        }
    }

    /**
     * 校验菜单名称是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    @Override
    public boolean checkMenuNameUnique(SysMenuBo menu) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getMenuName, menu.getMenuName())
            .eq(SysMenu::getParentId, menu.getParentId())
            .ne(ObjectUtil.isNotNull(menu.getMenuId()), SysMenu::getMenuId, menu.getMenuId()));
        return !exist;
    }

    /**
     * 校验菜单是否存在
     *
     * @param menu 菜单信息
     * @return 结果
     */
    @Override
    public boolean checkMenuNameExists(SysMenuBo menu) {
        return baseMapper.exists(new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getMenuName, menu.getMenuName())
            .eq(SysMenu::getParentId, menu.getParentId())
            .eq(ObjectUtil.isNotNull(menu.getMenuId()), SysMenu::getMenuId, menu.getMenuId()));
    }

    /**
     * 校验菜单是否存在
     *
     * @param menu 菜单信息
     * @return 结果
     */
    @Override
    public SysMenu getExistsMenu(SysMenu menu) {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();

        // 1. 若menuId存在，优先通过menuId判断
        if (ObjectUtil.isNotNull(menu.getMenuId())) {
            queryWrapper.eq(SysMenu::getMenuId, menu.getMenuId());
            return baseMapper.selectOne(queryWrapper);
        }

        // 2. menuId不存在时，通过menuName和parentId组合判断
        // 校验必要参数（避免空条件查询）
        if (StringUtils.isBlank(menu.getMenuName()) || ObjectUtil.isNull(menu.getParentId())) {
            return null; // 关键参数缺失，无法判断，返回不存在
        }

        queryWrapper.eq(SysMenu::getMenuName, menu.getMenuName())
            .eq(SysMenu::getParentId, menu.getParentId());
        return baseMapper.selectOne(queryWrapper);
    }


    /**
     * 获取所有顶级应用菜单
     *
     * <p>仅返回 {@code menuType='A'} 且 {@code parentId=0} 的菜单，
     * 不包含目录/菜单/按钮等子节点；按当前登录用户权限过滤
     * （超管/租管返回全量，普通用户需命中自身角色/客户端关联），但不限定是否已绑定客户端。
     * 返回结果会回填 {@code clientId} 字段（已绑→clientId，未绑→null），便于应用菜单管理页前端展示绑定关系。</p>
     *
     * @param userId 当前登录用户ID
     * @return 应用菜单列表
     */
    @Override
    public List<SysMenuVo> selectAllAppMenus(Long userId) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        // 管理员显示所有菜单信息 不是管理员 按用户id过滤菜单
        if (!(LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId))) {
            // 通过用户id获取角色id 通过角色id获取菜单id 然后in菜单
            // 或者有客户端id权限的也能看
            wrapper.or(w -> w
                .inSql(SysMenu::getMenuId, baseMapper.buildMenuByUserSql(userId))
                .or()
                .inSql(SysMenu::getMenuId, clientMapper.buildMenuIdByUserSql(userId))
            );
        }
        List<SysMenuVo> menuList = baseMapper.selectVoList(
            wrapper.eq(SysMenu::getMenuType, SystemConstants.TYPE_APP)
                .eq(SysMenu::getParentId, 0L)
                .eq(SysMenu::getVisible, SystemConstants.NORMAL)
                .eq(SysMenu::getStatus, SystemConstants.NORMAL)
                .orderByAsc(SysMenu::getOrderNum));
        // 回填 clientId：已绑定则填，未绑定则保持 null，用于前端识别绑定关系
        List<Long> menuIdList = menuList.stream().map(SysMenuVo::getMenuId).toList();
        if (CollUtil.isNotEmpty(menuIdList)) {
            List<SysClientVo> sysClientVos = sysClientMenuHelper.queryByMenuIds(menuIdList);
            Map<Long, String> clientMapCache = sysClientVos.stream()
                .filter(vo -> vo.getMenuId() != null && vo.getClientId() != null)
                .collect(Collectors.toMap(
                    SysClientVo::getMenuId,
                    SysClientVo::getClientId,
                    (oldValue, newValue) -> newValue
                ));
            for (SysMenuVo sysMenuVo : menuList) {
                sysMenuVo.setClientId(clientMapCache.get(sysMenuVo.getMenuId()));
            }
        }
        return menuList;
    }

    /**
     * 获取外部入口应用
     *
     * @param userId 用户id
     * @return list
     */
    @Override
    public List<SysMenuVo> selectApplicationsByUserId(Long userId) {
        List<SysMenuVo> menuList;
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        // 管理员显示所有菜单信息 不是管理员 按用户id过滤菜单
        if (!(LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId))) {
            // 通过用户id获取角色id 通过角色id获取菜单id 然后in菜单
            // 或者有客户端id权限的也能看
            wrapper.or(w -> w
                .inSql(SysMenu::getMenuId, baseMapper.buildMenuByUserSql(userId))
                .or()
                .inSql(SysMenu::getMenuId, clientMapper.buildMenuIdByUserSql(userId))
            );
        }
        menuList = baseMapper.selectVoList(
            wrapper.eq(SysMenu::getVisible, SystemConstants.NORMAL)
                .eq(SysMenu::getStatus, SystemConstants.NORMAL)
                .eq(SysMenu::getMenuType, SystemConstants.TYPE_APP)
                .eq(SysMenu::getIsFrame, SystemConstants.YES_FRAME)
                .inSql(SysMenu::getMenuId, clientMapper.getMenuIdListSql())
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum));
        List<Long> menuIdList = menuList.stream().map(SysMenuVo::getMenuId).toList();
        if(!menuIdList.isEmpty()){
            List<SysClientVo> sysClientVos = sysClientMenuHelper.queryByMenuIds(menuIdList);
            Map<Long, String> clientMapCache = sysClientVos.stream()
                .filter(vo -> vo.getMenuId() != null && vo.getClientId() != null)
                .collect(Collectors.toMap(
                    SysClientVo::getMenuId,
                    SysClientVo::getClientId,
                    (oldValue, newValue) -> newValue
                ));
            for (SysMenuVo sysMenuVo : menuList) {
                sysMenuVo.setClientId(clientMapCache.get(sysMenuVo.getMenuId()));
            }
        }
        return menuList;
    }

    /**
     * 导出菜单列表
     *
     * @param menu 菜单信息
     * @return 菜单信息集合信息
     */
    @Override
    public List<SysMenuExportVo> selectMenuExportList(SysMenuBo menu, Long userId) {
        List<SysMenuVo> menuList = this.selectMenuList(menu, userId);
        List<SysMenuExportVo> exportList = MapstructUtils.convert(menuList, SysMenuExportVo.class);
        fillExportParentName(exportList, menuList);
        return exportList;
    }

    /**
     * 导出菜单列表（导出指定菜单的整棵子树）
     *
     * <p>原实现走 {@code selectMenusById}，依赖数据库函数 {@code get_menu_child_ids} 递归取后代：
     * 该函数在目标库缺失/版本不一致、或 GROUP_CONCAT 被 {@code group_concat_max_len} 截断时，
     * 只会返回第一层子菜单 —— 表现为"导出的 Excel 里底层的菜单丢了"，且服务端无任何异常可排查。</p>
     *
     * <p>这里改为一次性载入菜单表，在内存中按 {@code parentId} 深度优先递归展开，
     * 保证任意深度的子孙菜单都被完整导出，且顺序为先根后子（同级按 orderNum 升序），
     * 与导入时 {@code MenuUtil.buildMenuOrderByLevel} 的层级还原逻辑一致。</p>
     *
     * <p>说明：不做 {@code status} 过滤。原 SQL 只导出启用菜单，一旦某个目录被停用而子菜单仍启用，
     * 导出文件里就会出现"父节点缺失"的孤儿行，导入时直接判为非法数据。</p>
     *
     * @param menuId 菜单id（应用根菜单 id）
     * @return 菜单信息集合信息（含根菜单自身及其全部子孙）
     */
    @Override
    public List<SysMenuExportVo> selectMenuExportListById(Long menuId) {
        if (menuId == null) {
            return new ArrayList<>();
        }
        List<SysMenuVo> allMenus = baseMapper.selectVoList(new LambdaQueryWrapper<SysMenu>()
            .orderByAsc(SysMenu::getParentId)
            .orderByAsc(SysMenu::getOrderNum));
        if (CollUtil.isEmpty(allMenus)) {
            return new ArrayList<>();
        }
        SysMenuVo rootMenu = allMenus.stream()
            .filter(menu -> menuId.equals(menu.getMenuId()))
            .findFirst()
            .orElseThrow(() -> new ServiceException("导出失败：菜单不存在（menuId=" + menuId + "）"));
        // 父 id -> 子菜单列表（保持 orderNum 顺序）；自成环（parentId == menuId）的脏数据不参与挂载
        Map<Long, List<SysMenuVo>> childrenMap = allMenus.stream()
            .filter(menu -> menu.getParentId() != null && !Objects.equals(menu.getMenuId(), menu.getParentId()))
            .collect(Collectors.groupingBy(SysMenuVo::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<SysMenuVo> subtree = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        collectMenuSubtree(rootMenu, childrenMap, subtree, visited);
        // 兜底：列表页按 app_id 归集菜单，若存量数据存在父链断开（parentId 指向已删除菜单）的行，
        // 上面的递归会跳过它们，这里按 app_id 归属补收一次，保证"列表能看到多少，导出就有多少"
        for (SysMenuVo orphan : allMenus) {
            if (menuId.equals(orphan.getAppId()) && !visited.contains(orphan.getMenuId())) {
                collectMenuSubtree(orphan, childrenMap, subtree, visited);
            }
        }
        List<SysMenuExportVo> exportList = MapstructUtils.convert(subtree, SysMenuExportVo.class);
        // 用全量菜单补全父菜单名称，父级不在导出子树内（如应用根菜单的父级）也能带上
        fillExportParentName(exportList, allMenus);
        return exportList;
    }

    /**
     * 填充导出文件中的"父菜单名称"列
     *
     * <p>该列必须与导入模板保持一致：Excel 按列序号映射字段，若导出的文件少了这一列，
     * 把导出数据粘进模板时后续列会整体错位，"显示顺序"(Integer) 会读到路由地址而报转换错误。</p>
     */
    private void fillExportParentName(List<SysMenuExportVo> exportList, List<SysMenuVo> sourceMenus) {
        if (CollUtil.isEmpty(exportList)) {
            return;
        }
        Map<Long, String> menuNameMap = new HashMap<>();
        for (SysMenuVo menu : sourceMenus) {
            if (menu.getMenuId() != null && menu.getMenuName() != null) {
                menuNameMap.put(menu.getMenuId(), menu.getMenuName());
            }
        }
        for (SysMenuExportVo exportVo : exportList) {
            exportVo.setParentName(menuNameMap.get(exportVo.getParentId()));
        }
    }

    /**
     * 深度优先收集菜单子树（先父后子）
     *
     * @param menu        当前节点
     * @param childrenMap 父 id -> 子菜单列表
     * @param collector   结果集
     * @param visited     已访问菜单 id，用于防御脏数据造成的父子环
     */
    private void collectMenuSubtree(SysMenuVo menu, Map<Long, List<SysMenuVo>> childrenMap,
                                    List<SysMenuVo> collector, Set<Long> visited) {
        if (menu == null || menu.getMenuId() == null || !visited.add(menu.getMenuId())) {
            return;
        }
        collector.add(menu);
        for (SysMenuVo child : childrenMap.getOrDefault(menu.getMenuId(), List.of())) {
            collectMenuSubtree(child, childrenMap, collector, visited);
        }
    }

    /**
     * 根据父级菜单id导入数据（导入到指定父级下）
     *
     * <p>层级还原规则（修复"导出再导入后底层菜单丢失/挂错父级"）：</p>
     * <ol>
     *     <li>Excel 中的"菜单ID"是源数据的 id，只作为父子关系的引用键，<b>绝不能被当作新记录主键</b>：
     *         旧实现直接拿它 insert/查库，会主键冲突，或把源应用（甚至别家应用）的菜单"搬"到目标下；</li>
     *     <li>导入语义是"复制到指定父级"：即使该 id 与库中已有菜单重复（同库跨目录导出的常见情况），
     *         也一律生成新 id 新建记录，不复用/覆盖源菜单；仅当菜单本来就位于导入目标下时才按 id 更新</li>
     *     <li>逐行导入时维护 {@code 源菜单ID -> 实际菜单ID} 映射，子菜单按映射后的父级挂载；</li>
     *     <li>父级不在文件内的行（跨应用导出、应用根行、手填模板）统一挂到导入目标父级下；</li>
     *     <li>菜单类型为 A（应用）的行只作为"树根占位"，直接映射为导入目标，避免生成"应用套应用"。</li>
     * </ol>
     *
     * @param file            导入文件
     * @param isUpdateSupport 是否更新已存在数据
     * @param parentMenuId    导入目标父级菜单id（0 表示导入为系统级菜单）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String importMenuDataByParent(MultipartFile file, boolean isUpdateSupport, Long parentMenuId) {
        // 1.校验父菜单是否存在（parentMenuId=0 表示系统级菜单，无需父节点）
        long targetParentId = parentMenuId == null ? 0L : parentMenuId;
        SysMenuVo parentMenu = null;
        if (targetParentId > 0L) {
            parentMenu = baseMapper.selectVoById(targetParentId);
            if (parentMenu == null) {
                throw new ServiceException("指定的父级菜单不存在（menuId=" + targetParentId + "）");
            }
        }
        // 1.1 说明：导入不做"管理应用范围"限制——管理员（含客户端管理员）允许把菜单导入到任意
        //     目标父菜单下（含其他应用），与超管一致；越权风险由接口权限（system:menu:importData）
        //     与操作日志覆盖，避免误伤"把标准菜单模板导入到别家应用"这类正常业务操作

        // 2. 解析Excel数据
        List<SysMenuImportVo> importDTOList;
        try {
            importDTOList = EasyExcel.read(file.getInputStream())
                .head(SysMenuImportVo.class)
                .sheet()
                .doReadSync(); // 同步读取Excel
        } catch (IOException e) {
            log.error("Excel文件解析失败", e);
            throw new ServiceException("Excel文件解析失败：" + e.getMessage());
        } catch (RuntimeException e) {
            ExcelDataConvertException convertException = findExcelConvertException(e);
            if (convertException != null) {
                int row = convertException.getRowIndex() + 1;
                int column = convertException.getColumnIndex() + 1;
                String header = getMenuImportHeader(convertException.getColumnIndex());
                log.error("菜单导入Excel单元格解析失败：第{}行第{}列【{}】", row, column, header, e);
                throw new ServiceException("导入失败：Excel 第" + row + "行第" + column + "列【" + header
                    + "】的内容格式不正确，请核对各列是否与模板一一对应。"
                    + "特别注意「父菜单名称」列必须保留（内容可留空），不要把少一列的导出数据直接粘贴到模板里。");
            }
            log.error("Excel文件解析失败", e);
            throw new ServiceException("Excel文件解析失败：" + e.getMessage());
        }
        // 3. 数据校验与预处理（必填项、格式校验、字典兜底）
        MenuUtil.validateAndPreprocess(importDTOList, parentMenu == null ? null : parentMenu.getMenuName());

        // 4. 对导入的菜单按照层级排序（父在前、子在后，保证父级先落库）
        List<SysMenuImportVo> nextImportDTOList = MenuUtil.buildMenuOrderByLevel(importDTOList, targetParentId);

        // 5.将数据保存到数据库
        int successNum = 0;
        int failureNum = 0;
        int appRootNum = 0;
        final StringBuilder successMsg = new StringBuilder();
        final StringBuilder failureMsg = new StringBuilder();
        LoginUser loginUser = LoginHelper.getLoginUser();
        if(loginUser == null){
            throw new ServiceException("用户未登录");
        }
        // 导入过程中维护 源菜单ID/菜单名称 -> 实际菜单ID 的映射，用于还原父子层级
        Map<Long, Long> idMapping = new HashMap<>();
        Map<String, Long> nameMapping = new HashMap<>();
        for (SysMenuImportVo menuImportVo : nextImportDTOList) {
            // 应用根行（A）只作为"树根占位"：直接映射为导入目标，不落库，避免生成"应用套应用"
            if (SystemConstants.TYPE_APP.equals(menuImportVo.getMenuType())) {
                registerImportMapping(idMapping, nameMapping, menuImportVo, targetParentId);
                appRootNum++;
                continue;
            }
            SysMenu sysMenu = BeanUtil.toBean(menuImportVo, SysMenu.class);
            // 关键：Excel 里的菜单ID 是源数据的 id，必须清空，让数据库生成新主键
            sysMenu.setMenuId(null);
            // 父级按导入过程中建立的映射换算成实际菜单id（不会引用 Excel 里的旧 id）
            Long resolvedParentId = MenuUtil.resolveParentMenuId(menuImportVo, idMapping, nameMapping, targetParentId);
            // 判重：先按「菜单名称 + 换算后的父级」，未命中再按源菜单id兜底
            // （仅限本来就位于导入目标下的菜单，绝不能复用其它目录里的源菜单，否则等于把源菜单搬走）
            SysMenu existsMenu = findImportExistsMenu(sysMenu.getMenuName(), resolvedParentId);
            if (existsMenu == null) {
                existsMenu = findImportExistsMenuById(menuImportVo.getMenuId(), resolvedParentId);
            }
            sysMenu.setParentId(resolvedParentId);
            Date nowDate = DateUtils.getNowDate();
            try {
                // 验证是否存在这个菜单
                if (existsMenu==null) {
                    // 新增：按父菜单计算 app_id（与 insertMenu 保持一致）
                    sysMenu.setAppId(resolveAppIdByParentId(sysMenu.getParentId()));
                    sysMenu.setCreateDept(loginUser.getDeptId());
                    sysMenu.setCreateBy(loginUser.getUserId());
                    sysMenu.setCreateTime(nowDate);
                    sysMenu.setUpdateBy(loginUser.getUserId());
                    sysMenu.setUpdateTime(nowDate);
                    baseMapper.insert(sysMenu);
                    registerImportMapping(idMapping, nameMapping, menuImportVo, sysMenu.getMenuId());
                    // 与 insertMenu 保持一致：应用子树内的菜单自动授权给该应用的管理员角色，
                    // 免去导入后还要手工到"角色管理"再授权一次
                    autoGrantMenuToAppAdminRole(sysMenu);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、菜单 ").append(sysMenu.getMenuName()).append(" 导入成功");
                } else if (isUpdateSupport) {
                    sysMenu.setMenuId(existsMenu.getMenuId());
                    sysMenu.setUpdateBy(loginUser.getUserId());
                    sysMenu.setUpdateTime(nowDate);
                    // 更新：parentId 变化时重新计算 app_id；并级联更新所有后代 app_id
                    boolean parentChanged = !Objects.equals(sysMenu.getParentId(), existsMenu.getParentId());
                    if (parentChanged) {
                        sysMenu.setAppId(resolveAppIdByParentId(sysMenu.getParentId()));
                    }
                    baseMapper.updateById(sysMenu);
                    if (parentChanged) {
                        // 级联更新所有后代的 app_id
                        cascadeUpdateAppId(sysMenu.getMenuId(), sysMenu.getAppId());
                    }
                    registerImportMapping(idMapping, nameMapping, menuImportVo, existsMenu.getMenuId());
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、菜单 ").append(sysMenu.getMenuName()).append(" 更新成功");
                } else {
                    // 已存在且不更新时也要登记映射，否则它的子菜单会被平铺到导入目标下，层级再次丢失
                    registerImportMapping(idMapping, nameMapping, menuImportVo, existsMenu.getMenuId());
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("、菜单 ").append(sysMenu.getMenuName()).append(" 已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、菜单 " + HtmlUtil.cleanHtmlTag(sysMenu.getMenuName()) + " 导入失败：";
                String message = e.getMessage();
                if (e instanceof ConstraintViolationException cvException) {
                    message = StreamUtils.join(cvException.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
                }
                failureMsg.append(msg).append(message);
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        } else {
            if (appRootNum > 0) {
                successMsg.append("<br/><br/>说明：文件中的").append(appRootNum)
                    .append("条应用（A）根节点已自动映射为导入目标，未重复创建应用菜单");
            }
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        // P0 权限同步：导入菜单后通知客户端
        publishMenuChange(PermissionChangeEvent.ChangeType.INSERT);
        return successMsg.toString();

    }

    /**
     * 从异常链中找出 Excel 单元格转换异常（doReadSync 会把它包在 ExcelAnalysisException 里）
     */
    private ExcelDataConvertException findExcelConvertException(Throwable e) {
        while (e != null) {
            if (e instanceof ExcelDataConvertException convertException) {
                return convertException;
            }
            e = e.getCause();
        }
        return null;
    }

    /**
     * 取导入模板列头中文名
     */
    private String getMenuImportHeader(int columnIndex) {
        return columnIndex >= 0 && columnIndex < MENU_IMPORT_HEADERS.length
            ? MENU_IMPORT_HEADERS[columnIndex] : "未知列";
    }

    /**
     * 登记导入过程中的映射：源菜单ID/菜单名称 -> 实际写入的菜单ID
     *
     * @param idMapping     源菜单ID -> 实际菜单ID
     * @param nameMapping   菜单名称 -> 实际菜单ID（Excel 未填菜单ID时按名称兜底）
     * @param sourceMenu    导入文件中的行
     * @param actualMenuId  实际写入数据库的菜单ID
     */
    private void registerImportMapping(Map<Long, Long> idMapping, Map<String, Long> nameMapping,
                                       SysMenuImportVo sourceMenu, Long actualMenuId) {
        if (sourceMenu.getMenuId() != null) {
            idMapping.put(sourceMenu.getMenuId(), actualMenuId);
        }
        if (StringUtils.isNotBlank(sourceMenu.getMenuName())) {
            nameMapping.putIfAbsent(sourceMenu.getMenuName().trim(), actualMenuId);
        }
    }

    /**
     * 按「菜单名称 + 父级」查询导入行是否已存在
     *
     * @param menuName 菜单名称（Excel 中的行）
     * @param parentId 换算后的实际父级id
     * @return 已存在的菜单，不存在返回 null
     */
    private SysMenu findImportExistsMenu(String menuName, Long parentId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getMenuName, menuName)
            .eq(SysMenu::getParentId, parentId)
            .last("LIMIT 1"));
    }

    /**
     * 按 Excel 中的原菜单id 兜底查询已存在菜单（仅用于"导出后改名再导回原位置"）
     *
     * <p>同库跨目录/跨应用导入时，Excel 里的 menuId 就是源菜单在库中的真实 id，
     * 直接复用会走 update 并把它的 parentId 改成导入目标，等于把源目录的菜单"搬"过来
     * （源菜单消失），而不是复制一份。因此只有该菜单<b>本来就在导入目标下</b>时才允许复用。
     * 其余情况一律新建，由 {@code setMenuId(null)} 让数据库生成新的雪花id。</p>
     *
     * @param sourceMenuId    Excel 中的原菜单id
     * @param targetParentId  本行换算后的实际父级id
     * @return 可复用的已存在菜单，否则 null
     */
    private SysMenu findImportExistsMenuById(Long sourceMenuId, Long targetParentId) {
        if (sourceMenuId == null) {
            return null;
        }
        SysMenu sameIdMenu = baseMapper.selectById(sourceMenuId);
        if (sameIdMenu == null || SystemConstants.TYPE_APP.equals(sameIdMenu.getMenuType())) {
            return null;
        }
        if (!Objects.equals(sameIdMenu.getParentId(), targetParentId)) {
            return null;
        }
        // 双保险：确认与导入目标同属一个应用（或同为系统级菜单）
        Long sourceOwnerAppId = resolveAppIdByParentId(sameIdMenu.getParentId());
        Long targetOwnerAppId = resolveAppIdByParentId(targetParentId);
        return Objects.equals(sourceOwnerAppId, targetOwnerAppId) ? sameIdMenu : null;
    }

    /**
     * 通过菜单id查询所有子菜单列表
     *
     * <p>仅用于「应用菜单的子菜单管理」：必须传入已绑定客户端的应用菜单ID，
     * 非管理员仅能查询自己有访问权的客户端所绑定的应用菜单子树，避免横向越权</p>
     *
     * @param menu   菜单（menuId 必填）
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenuVo> selectMenuListByMenuId(SysMenuBo menu, Long userId) {
        Long appMenuId = menu.getMenuId();
        if (appMenuId == null) {
            return List.of();
        }
        boolean isAdmin = LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId);
        if (!checkAppMenuAccessible(appMenuId, userId)) {
            return List.of();
        }
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(menu.getMenuName()), SysMenu::getMenuName, menu.getMenuName())
            .eq(StringUtils.isNotBlank(menu.getVisible()), SysMenu::getVisible, menu.getVisible())
            .eq(StringUtils.isNotBlank(menu.getStatus()), SysMenu::getStatus, menu.getStatus())
            .eq(StringUtils.isNotBlank(menu.getMenuType()), SysMenu::getMenuType, menu.getMenuType())
            .eq(ObjectUtil.isNotNull(menu.getParentId()), SysMenu::getParentId, menu.getParentId())
            // 直接按 app_id 过滤 = 该应用菜单下所有子菜单（M/C/F），依赖冗余字段 app_id（已通过 SQL 脚本回填）
            // 这里不再使用 SQL 函数 get_menu_child_ids 递归，性能更优（O(1) 索引查询）
            .eq(SysMenu::getAppId, appMenuId)
            .ne(SysMenu::getMenuId, appMenuId)
            .orderByAsc(SysMenu::getParentId)
            .orderByAsc(SysMenu::getOrderNum);
        // 客户端管理员（应用维护者）：不再叠加"角色菜单"过滤——入口处已校验该应用菜单绑定的客户端
        // 在用户可见范围内，管理视图按客户端维度整树可见；否则新增菜单尚未分配给当前用户角色时
        // 会立即"消失"，也无法再将其分配给角色。
        // 普通业务用户（有应用访问权但不是该应用管理员）仍按"角色菜单"过滤，遵循信息最小化
        if (!isAdmin) {
            boolean isClientAdmin = CollUtil.isNotEmpty(sysClientRoleMapper.selectAdminClientIdsByUserId(userId));
            if (!isClientAdmin) {
                wrapper.inSql(SysMenu::getMenuId, baseMapper.buildMenuByUserSql(userId));
            }
        }
        return baseMapper.selectVoList(wrapper);
    }

    /**
     * 获取未被客户端绑定的应用菜单列表（供新增客户端时选择入口菜单）
     *
     * @param userId 用户id
     * @return list
     */
    @Override
    public List<SysMenuVo> getNoBindAppMenus(Long userId) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getVisible, SystemConstants.NORMAL)
            .eq(SysMenu::getStatus, SystemConstants.NORMAL)
            .eq(SysMenu::getMenuType, SystemConstants.TYPE_APP)
            .eq(SysMenu::getIsFrame, SystemConstants.YES_FRAME)
            .notInSql(SysMenu::getMenuId, clientMapper.getMenuIdListSql())
            .orderByAsc(SysMenu::getParentId)
            .orderByAsc(SysMenu::getOrderNum);
        if (!(LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId))) {
            // 非管理员只能看到自己有访问权的客户端所绑定的应用菜单之外的入口（应用广场视图一致）
            wrapper.and(w -> w
                .inSql(SysMenu::getMenuId, baseMapper.buildMenuByUserSql(userId))
                .or()
                .inSql(SysMenu::getMenuId, clientMapper.buildMenuIdByUserSql(userId))
            );
        }
        return baseMapper.selectVoList(wrapper);
    }

    /**
     * 查询分配角色菜单时可用的菜单列表（系统级菜单 vs 应用菜单的隔离）
     *
     * <p>超管/租管返回该用户可见的全部菜单；客户端管理员仅返回「其管理客户端的 APP 子树」内
     * 的菜单，从而避免给角色分配系统级菜单（用户管理/角色管理/客户端管理等）</p>
     *
     * @param userId 当前操作用户ID
     * @return 可选菜单列表
     */
    @Override
    public List<SysMenuVo> selectMenuListForRoleAssign(Long userId) {
        List<SysMenuVo> userMenus = selectMenuList(new SysMenuBo(), userId);
        if (LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId)) {
            return userMenus;
        }
        // 客户端管理员：仅其作为 admin 的客户端的 APP 菜单子树可选（区别于普通访问权限）
        List<SysClientVo> clients = sysClientMenuHelper.queryListByUserId(userId);
        Set<String> adminClientIds = new HashSet<>(sysClientRoleMapper.selectAdminClientIdsByUserId(userId));
        List<Long> appMenuIds = clients.stream()
            .filter(c -> adminClientIds.contains(c.getClientId()))
            .map(SysClientVo::getMenuId).filter(Objects::nonNull).distinct().toList();
        if (CollUtil.isEmpty(appMenuIds)) {
            return List.of();
        }
        // 查询其管理的应用子树全量菜单（含顶级A节点），不按当前管理员的角色菜单过滤：
        // 否则新增的子菜单尚未分配给管理员自身角色时会不可见，导致无法在角色分配中勾选。
        // 用 get_menu_child_ids 按树查询而非按 app_id 过滤，兼容存量未回填 app_id 的菜单
        Set<Long> allowedMenuIds = new HashSet<>(appMenuIds);
        for (Long appMenuId : appMenuIds) {
            List<Long> subtreeIds = baseMapper.selectObjs(
                new LambdaQueryWrapper<SysMenu>()
                    .select(SysMenu::getMenuId)
                    .apply("FIND_IN_SET(menu_id, get_menu_child_ids({0}))", appMenuId),
                obj -> obj == null ? null : Long.valueOf(obj.toString()));
            allowedMenuIds.addAll(subtreeIds);
        }
        return baseMapper.selectVoList(new LambdaQueryWrapper<SysMenu>()
            .in(SysMenu::getMenuId, allowedMenuIds)
            .orderByAsc(SysMenu::getParentId)
            .orderByAsc(SysMenu::getOrderNum));
    }

    /**
     * 判断菜单是否在用户的客户端管理范围内
     */
    @Override
    public boolean isMenuInUserAdminScope(Long userId, Long menuId) {
        if (menuId == null) {
            return false;
        }
        if (LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId)) {
            return true;
        }
        // 1. 取菜单祖先链（含自身）
        List<Long> ancestorIds = new ArrayList<>(baseMapper.getAncestorMenuIds(menuId));
        if (!ancestorIds.contains(menuId)) {
            ancestorIds.add(menuId);
        }
        if (CollUtil.isEmpty(ancestorIds)) {
            return false;
        }
        // 2. 祖先链中是否存在被激活客户端绑定的应用菜单
        List<SysClientVo> boundClients = sysClientMenuHelper.queryByMenuIds(ancestorIds);
        if (CollUtil.isEmpty(boundClients)) {
            return false;
        }
        Set<String> boundClientIds = boundClients.stream()
            .map(SysClientVo::getClientId).collect(Collectors.toSet());
        // 3. 用户是否其中某个应用所属客户端的管理员
        List<String> adminClientIds = sysClientRoleMapper.selectAdminClientIdsByUserId(userId);
        return adminClientIds.stream().anyMatch(boundClientIds::contains);
    }

    /**
     * 查找菜单祖先链中最近的应用菜单（menu_type='A'），无则返回 null
     */
    private Long findAppMenuAncestor(Long menuId) {
        List<Long> ancestorIds = baseMapper.getAncestorMenuIds(menuId);
        if (CollUtil.isEmpty(ancestorIds)) {
            return null;
        }
        // 用 ancestorIds 内的菜单 id 查询类型为应用菜单的祖先
        List<SysMenu> appMenus = baseMapper.selectList(new LambdaQueryWrapper<SysMenu>()
            .select(SysMenu::getMenuId)
            .in(SysMenu::getMenuId, ancestorIds)
            .eq(SysMenu::getMenuType, SystemConstants.TYPE_APP));
        if (CollUtil.isEmpty(appMenus)) {
            return null;
        }
        // 取最近祖先：parentId 最大（即离原菜单最近的应用祖先）
        return appMenus.stream().max(Comparator.comparing(SysMenu::getParentId)).orElse(null).getMenuId();
    }

    /**
     * 计算新增菜单的 app_id。
     *
     * <p>规则：
     * <ul>
     *     <li>顶级菜单（{@code parentId == null/0}）：app_id = NULL（系统级）</li>
     *     <li>父级为应用菜单（{@code type='A'}）：app_id = 父级 menu_id（继承）</li>
     *     <li>父级为应用子树内普通菜单（{@code app_id != null}）：app_id = 父级 app_id（继承）</li>
     *     <li>父级为系统级菜单：app_id = NULL（系统级）</li>
     * </ul>
     *
     * @param bo 新增菜单业务对象
     * @return app_id
     */
    private Long resolveAppIdForInsert(SysMenuBo bo) {
        return resolveAppIdByParentId(bo.getParentId());
    }

    /**
     * 根据父菜单ID直接计算 app_id（用于导入场景，绕过 SysMenuBo 包装）
     *
     * @param parentId 父菜单ID（null/0 返回 null）
     * @return app_id
     */
    private Long resolveAppIdByParentId(Long parentId) {
        if (parentId == null || parentId <= 0L) {
            return null;
        }
        SysMenu parent = baseMapper.selectById(parentId);
        if (parent == null) {
            return null;
        }
        if (SystemConstants.TYPE_APP.equals(parent.getMenuType())) {
            // 父级是应用菜单，应用 ID 即父级自身的 menu_id
            return parent.getAppId() != null ? parent.getAppId() : parent.getMenuId();
        }
        // 父级是普通菜单：继承父级的 app_id（NULL 表示父级在系统级，子菜单也在系统级）
        return parent.getAppId();
    }

    /**
     * 计算修改菜单后的 app_id（与新增同语义，但 parentId 取自 bo）
     *
     * @param bo 修改菜单业务对象
     * @return 新的 app_id
     */
    private Long resolveAppIdForUpdate(SysMenuBo bo) {
        return resolveAppIdForInsert(bo);
    }

    /**
     * 级联更新菜单及其所有后代的 app_id。
     *
     * <p>用 MySQL 函数 {@code get_menu_child_ids(menu_id)} 递归收集所有后代菜单 ID，
     * 一次性 UPDATE 它们的 app_id。包含自身（{@code set(SysMenu::getAppId, ...)} + WHERE 应用函数）。</p>
     *
     * @param menuId 当前菜单 ID
     * @param newAppId 新的 app_id（可为 null）
     */
    private void cascadeUpdateAppId(Long menuId, Long newAppId) {
        if (menuId == null) {
            return;
        }
        // LambdaUpdateWrapper.set(SysMenu::getAppId, null) 会生成 SET app_id = NULL，
        // 绕过字段策略（非 null 跳过）实现真写入 null。
        baseMapper.update(null, new LambdaUpdateWrapper<SysMenu>()
            .set(SysMenu::getAppId, newAppId)
            .apply("FIND_IN_SET(menu_id, get_menu_child_ids({0})) > 0", menuId));
    }

    /**
     * 根据父节点的ID获取所有子节点
     *
     * @param list     分类表
     * @param parentId 传入的父节点ID
     * @return String
     */
    private List<SysMenu> getChildPerms(List<SysMenu> list, Long parentId) {
        List<SysMenu> returnList = new ArrayList<>();
        for (SysMenu t : list) {
            // 一、根据传入的某个父节点ID,遍历该父节点的所有子节点
            if (t.getParentId().equals(parentId)) {
                recursionFn(list, t);
                returnList.add(t);
            }
        }
        return returnList;
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<SysMenu> list, SysMenu t) {
        // 得到子节点列表
        List<SysMenu> childList = StreamUtils.filter(list, n -> n.getParentId().equals(t.getMenuId()));
        t.setChildren(childList);
        for (SysMenu tChild : childList) {
            // 判断是否有子节点
            if (list.stream().anyMatch(n -> n.getParentId().equals(tChild.getMenuId()))) {
                recursionFn(list, tChild);
            }
        }
    }

}
