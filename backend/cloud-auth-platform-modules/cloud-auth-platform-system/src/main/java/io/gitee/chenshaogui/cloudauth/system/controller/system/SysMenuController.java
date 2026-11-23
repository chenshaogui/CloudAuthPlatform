package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.lang.tree.Tree;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.excel.utils.ExcelUtil;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.idempotent.annotation.RepeatSubmit;
import io.gitee.chenshaogui.cloudauth.common.log.annotation.Log;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMenuBo;
import io.gitee.chenshaogui.cloudauth.system.helper.MenuScopeChecker;
import io.gitee.chenshaogui.cloudauth.system.utils.SystemUtil;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMenuService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/menu")
public class SysMenuController extends BaseController {

    private final ISysMenuService menuService;

    private final ISysClientService sysClientService;

    private final MenuScopeChecker menuScopeChecker;

    /**
     * 获取路由信息
     *
     * @return 路由信息
     */
    @GetMapping("/getRouters")
    public R<List<RouterVo>> getRouters() {
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(LoginHelper.getUserId());
        return R.ok(menuService.buildMenus(menus));
    }
    /**
     * 获取路由信息
     *
     * @return 路由信息
     */
    @GetMapping("/getRoutersByClient")
    public R<List<RouterVo>> getRoutersByClient(String clientId) {
        String newClientId = clientId==null?LoginHelper.getClientId():clientId;
        List<RouterVo> menus = menuService.selectMenuRoutersByClient(LoginHelper.getUserId(), newClientId);
        return R.ok(menus);
    }


    /**
     * 获取所有应用菜单列表（不含子菜单、按钮等）
     *
     * <p>仅返回 {@code menuType='A'} 的顶级应用菜单，按当前登录用户权限过滤，
     * 不限定是否已绑定客户端，用于应用菜单管理页与客户端管理"绑定应用"下拉。</p>
     *
     * @return 应用菜单列表
     */
    @GetMapping("/getApplications")
    public R<List<SysMenuVo>> getApplications() {
        return R.ok(menuService.selectAllAppMenus(LoginHelper.getUserId()));
    }

    /**
     * 获取已绑定客户端的应用菜单列表
     *
     * <p>当前用户可访问的、{@code isFrame='0'} 且已被客户端绑定的应用菜单；
     * 用于首页工作台展示应用入口。</p>
     *
     * @return 已绑定应用菜单列表
     */
    @GetMapping("/listBoundApplications")
    public R<List<SysMenuVo>> listBoundApplications() {
        return R.ok(menuService.selectApplicationsByUserId(LoginHelper.getUserId()));
    }

    @GetMapping("/getNoBindAppMenus")
    public R<List<SysMenuVo>> getNoBindAppMenus() {
        List<SysMenuVo> menus = menuService.getNoBindAppMenus(LoginHelper.getUserId());
        return R.ok(menus);
    }

    /**
     * 获取菜单列表
     */
    @SaCheckPermission(value = {"system:menu:list", "system:application:list"}, mode = SaMode.OR)
    @GetMapping("/list")
    public R<List<SysMenuVo>> list(SysMenuBo menu) {
        List<SysMenuVo> menus = menuService.selectMenuList(menu, LoginHelper.getUserId());
        return R.ok(menus);
    }

    @SaCheckPermission(value = {"system:menu:query", "system:application:list"}, mode = SaMode.OR)
    @GetMapping("/listByMenuId")
    public R<List<SysMenuVo>> listByMenuId(SysMenuBo menu) {
        if(menu.getMenuId()==null){
            return R.fail("菜单id不能为空");
        }
        List<SysMenuVo> menus = menuService.selectMenuListByMenuId(menu, LoginHelper.getUserId());
        return R.ok(menus);
    }

    /**
     * 查询应用菜单完整子树（按 parent_id 递归，含界面上看不见的历史子菜单）
     *
     * <p>仅供「应用菜单管理」的级联删除弹窗勾选，与删除校验口径一致。</p>
     */
    @SaCheckPermission(value = {"system:menu:query", "system:application:list"}, mode = SaMode.OR)
    @GetMapping("/subtreeByMenuId")
    public R<List<SysMenuVo>> subtreeByMenuId(@RequestParam Long menuId) {
        return R.ok(menuService.selectMenuSubtreeByMenuId(menuId, LoginHelper.getUserId()));
    }

    /**
     * 根据菜单编号获取详细信息
     *
     * @param menuId 菜单ID
     */
    @SaCheckPermission(value = {"system:menu:query", "system:application:list"}, mode = SaMode.OR)
    @GetMapping(value = "/{menuId}")
    public R<SysMenuVo> getInfo(@PathVariable Long menuId) {
        return R.ok(menuService.selectMenuById(menuId));
    }

    /**
     * 获取菜单下拉树列表
     */
    @SaCheckPermission(value = {"system:menu:query", "system:application:list"}, mode = SaMode.OR)
    @GetMapping("/treeselect")
    public R<List<Tree<Long>>> treeselect(SysMenuBo menu) {
        Long userId = LoginHelper.getUserId();
        // 客户端管理员的可分配范围＝其管理的应用子树（沿用 selectMenuList 会返回"自己角色已有的系统菜单"，
        // 既看不到应用子树，勾选后保存还会被服务端范围校验拒绝）；
        // 平台侧非超管角色（非客户端管理员）保持原行为，避免误伤
        List<SysMenuVo> menus;
        if (LoginHelper.isSuperAdmin(userId) || SystemUtil.isTenantAdmin(userId)
            || !menuScopeChecker.isClientAdmin(userId)) {
            menus = menuService.selectMenuList(menu, userId);
        } else {
            menus = menuService.selectMenuListForRoleAssign(userId);
        }
        return R.ok(menuService.buildMenuTreeSelect(menus));
    }

    /**
     * 加载对应角色菜单列表树
     *
     * @param roleId 角色ID
     */
    @SaCheckPermission(value = {"system:menu:query", "system:application:list"}, mode = SaMode.OR)
    @GetMapping(value = "/roleMenuTreeselect/{roleId}")
    public R<MenuTreeSelectVo> roleMenuTreeselect(@PathVariable("roleId") Long roleId) {
        // M4：客户端管理员的可选菜单范围仅限其管理客户端的应用子树，禁止分配系统级菜单
        List<SysMenuVo> menus = menuService.selectMenuListForRoleAssign(LoginHelper.getUserId());
        MenuTreeSelectVo selectVo = new MenuTreeSelectVo(
            menuService.selectMenuListByRoleId(roleId),
            menuService.buildMenuTreeSelect(menus));
        return R.ok(selectVo);
    }

    /**
     * 加载对应租户套餐菜单列表树
     *
     * @param packageId 租户套餐ID
     */
    @SaCheckPermission(value = {"system:menu:query", "system:application:list"}, mode = SaMode.OR)
    @GetMapping(value = "/tenantPackageMenuTreeselect/{packageId}")
    public R<MenuTreeSelectVo> tenantPackageMenuTreeselect(@PathVariable("packageId") Long packageId) {
        List<SysMenuVo> menus = menuService.selectMenuList(LoginHelper.getUserId());
        List<Tree<Long>> list = menuService.buildMenuTreeSelect(menus);
        // 删除租户管理菜单
        list.removeIf(menu -> menu.getId() == 6L);
        List<Long> ids = new ArrayList<>();
        if (packageId > 0L) {
            ids = menuService.selectMenuListByPackageId(packageId);
        }
        MenuTreeSelectVo selectVo = new MenuTreeSelectVo(ids, list);
        return R.ok(selectVo);
    }

    /**
     * 新增菜单
     */
    @SaCheckPermission(value = {"system:menu:add", "system:application:add"}, mode = SaMode.OR)
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysMenuBo menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            return R.fail("新增菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        } else if (SystemConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath())) {
            return R.fail("新增菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        }
        return toAjax(menuService.insertMenu(menu));
    }

    /**
     * 修改菜单
     */
    @SaCheckPermission(value = {"system:menu:edit", "system:application:update"}, mode = SaMode.OR)
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysMenuBo menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            return R.fail("修改菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        } else if (SystemConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath())) {
            return R.fail("修改菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        } else if (menu.getMenuId().equals(menu.getParentId())) {
            return R.fail("修改菜单'" + menu.getMenuName() + "'失败，上级菜单不能选择自己");
        }
        return toAjax(menuService.updateMenu(menu));
    }

    /**
     * 删除菜单
     *
     * @param menuId 菜单ID
     */
    @SaCheckPermission(value = {"system:menu:remove", "system:application:remove"}, mode = SaMode.OR)
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{menuId}")
    public R<Void> remove(@PathVariable("menuId") Long menuId) {
        List<SysMenuVo> blockingChildren = menuService.selectBlockingChildMenus(List.of(menuId), List.of());
        if (!blockingChildren.isEmpty()) {
            return R.warn(buildChildMenuMessage(blockingChildren, false));
        }
        if (sysClientService.existsActiveClientByMenuId(menuId)) {
            return R.warn("应用菜单已被客户端绑定，请通过【客户端管理】解绑后再删除");
        }
        // 客户端管理员只能删除其管理应用范围内的菜单，避免横向越权删除其他应用/系统菜单；
        // 平台侧非超管角色（非客户端管理员）不受应用范围限制
        Long removeUserId = LoginHelper.getUserId();
        if (menuScopeChecker.isClientAdmin(removeUserId) && !menuScopeChecker.isMenuInUserAdminScope(removeUserId, menuId)) {
            return R.fail("无权删除该菜单，只能维护自己管理应用下的菜单");
        }
        // 应用子树内的菜单：删除时自动解绑角色关联（新增菜单会自动授权给应用管理员角色，
        // 若沿用"已分配不允许删除"会导致管理员连自己刚新增的菜单都删不掉）；
        // 系统级菜单仍保留"已分配不允许删除"的保护，避免误删造成大范围权限缺失
        if (!menuService.isAppSubtreeMenu(menuId) && menuService.checkMenuExistRole(menuId)) {
            return R.warn("菜单已分配,不允许删除");
        }
        return toAjax(menuService.deleteMenuById(menuId));
    }

    /**
     * 角色菜单列表树信息
     *
     * @param checkedKeys 选中菜单列表
     * @param menus       菜单下拉树结构列表
     */
    public record MenuTreeSelectVo(List<Long> checkedKeys, List<Tree<Long>> menus) {
    }

    /**
     * 批量级联删除菜单
     *
     * @param menuIds 菜单ID串
     */
    @SaCheckPermission(value = {"system:menu:remove", "system:application:remove"}, mode = SaMode.OR)
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/cascade/{menuIds}")
    public R<Void> remove(@PathVariable("menuIds") Long[] menuIds) {
        List<Long> menuIdList = List.of(menuIds);
        List<SysMenuVo> blockingChildren = menuService.selectBlockingChildMenus(menuIdList, menuIdList);
        if (!blockingChildren.isEmpty()) {
            return R.warn(buildChildMenuMessage(blockingChildren, true));
        }
        // 应用菜单被客户端绑定时禁止级联删除（与单删保持一致）
        List<Long> boundAppMenus = menuIdList.stream()
            .filter(sysClientService::existsActiveClientByMenuId)
            .toList();
        if (!boundAppMenus.isEmpty()) {
            return R.warn("应用菜单[" + boundAppMenus + "]已被客户端绑定，请通过【客户端管理】解绑后再删除");
        }
        // 客户端管理员只能删除其管理应用范围内的菜单；平台侧非超管角色（非客户端管理员）不受限制
        Long removeUserId = LoginHelper.getUserId();
        if (menuScopeChecker.isClientAdmin(removeUserId)) {
            List<Long> outOfScopeMenus = menuIdList.stream()
                .filter(menuId -> !menuScopeChecker.isMenuInUserAdminScope(removeUserId, menuId))
                .toList();
            if (!outOfScopeMenus.isEmpty()) {
                return R.fail("无权删除菜单" + outOfScopeMenus + "，只能维护自己管理应用下的菜单");
            }
        }
        menuService.deleteMenuById(menuIdList);
        return R.ok();
    }

    /**
     * 拼装"存在子菜单"的提示，带出数量与名称，避免用户看不到子菜单时无从下手
     *
     * @param children 挡住删除的子菜单
     * @param cascade  是否为级联删除场景
     */
    private String buildChildMenuMessage(List<SysMenuVo> children, boolean cascade) {
        String names = children.stream().limit(5)
            .map(child -> StringUtils.isBlank(child.getMenuName()) ? "未命名" : child.getMenuName())
            .collect(Collectors.joining("、"));
        if (children.size() > 5) {
            names += " 等";
        }
        String action = cascade
            ? "请在「级联删除」中一并勾选这些子菜单后重试"
            : "请先删除子菜单，或使用「级联删除」一次性删除整棵子树";
        return "存在" + children.size() + "个子菜单（" + names + "），不允许删除；" + action;
    }

    /**
     * 导入数据（系统级菜单）
     *
     * <p>与"按菜单id导入"统一走 {@code importMenuDataByParent}（parentMenuId=0 表示系统级），
     * 避免两套导入逻辑对父子层级的处理不一致</p>
     *
     * @param file          导入文件
     * @param updateSupport 是否更新已存在数据
     */
    @Log(title = "菜单管理", businessType = BusinessType.IMPORT)
    @SaCheckPermission(value = {"system:menu:import", "system:application:upload"}, mode = SaMode.OR)
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<String> importData(@RequestPart("file") MultipartFile file, boolean updateSupport) {
        if (file.isEmpty()) {
            throw new ServiceException("导入文件不能为空");
        }
        String msg = menuService.importMenuDataByParent(file, updateSupport, 0L);
        return R.ok("菜单导入完成", msg);
    }

    /**
     * 导入数据【通过菜单id】
     *
     * @param file          导入文件
     * @param parentMenuId 父级菜单id
     */
    @Log(title = "菜单管理", businessType = BusinessType.IMPORT)
    @SaCheckPermission(value = {"system:menu:import", "system:application:upload"}, mode = SaMode.OR)
    @PostMapping(value = "/importDataByMenuId", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<String> importDataByMenuId(@RequestPart("file") MultipartFile file, boolean updateSupport, Long parentMenuId) {
        if (file.isEmpty()) {
            throw new RuntimeException("导入文件不能为空");
        }
        if (parentMenuId == null || parentMenuId <= 0) {
            throw new RuntimeException("父级菜单ID不能为空且必须为正数");
        }
        String msg = menuService.importMenuDataByParent(file, updateSupport, parentMenuId);
        return R.ok("菜单导入完成", msg);
    }

    /**
     * 获取导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.exportExcelAsText(new ArrayList<>(), "应用菜单导入模板", SysMenuImportVo.class, response);
    }

    /**
     * 获取导入模板（按菜单id，兼容旧前端）
     *
     * <p>模板统一为「只有表头 + 表头批注」的空白表格，不再输出当前菜单的数据行：
     * 那一行曾被误当成要导入的数据，直接上传到当前菜单下会生成一条同名副本。
     * 旧前端传的 menuId 会被忽略，模板内容与具体菜单无关；填写规则见表头批注。</p>
     */
    @PostMapping("/importTemplateById")
    public void importTemplateById(HttpServletResponse response) {
        ExcelUtil.exportExcelAsText(new ArrayList<>(), "应用菜单导入模板", SysMenuImportVo.class, response);
    }

    /**
     * 导出菜单列表
     */
    @Log(title = "菜单管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission(value = {"system:menu:export", "system:application:download"}, mode = SaMode.OR)
    @PostMapping("/export")
    public void export(SysMenuBo menuBo, HttpServletResponse response) {
        List<SysMenuExportVo> list = menuService.selectMenuExportList(menuBo, LoginHelper.getUserId());
        ExcelUtil.exportExcelAsText(list, "菜单数据", SysMenuExportVo.class, response);
    }

    /**
     * 通过菜单id导出菜单列表
     */
    @Log(title = "菜单管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission(value = {"system:menu:export", "system:application:download"}, mode = SaMode.OR)
    @PostMapping("/exportByMenuId")
    public void exportByMenuId(SysMenuBo menuBo, HttpServletResponse response) {
        if(menuBo.getMenuId()==null){
            throw new ServiceException("menuId菜单id不能为空");
        }
        List<SysMenuExportVo> list = menuService.selectMenuExportListById(menuBo.getMenuId());
        ExcelUtil.exportExcelAsText(list, "菜单数据", SysMenuExportVo.class, response);
    }

}
