package io.gitee.chenshaogui.cloudauth.system.utils;

import cn.hutool.core.collection.CollUtil;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMenuImportVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 菜单相关处理
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class MenuUtil {

    /**
     * 合法的菜单类型：A应用、M目录、C菜单、F按钮
     */
    private static final List<String> MENU_TYPES = List.of("A", "M", "C", "F");

    /**
     * 菜单最大层级，防御 Excel 中父子关系成环导致的无限递归
     */
    private static final int MAX_DEEP_LEVEL = 20;

    /**
     * 数据校验与预处理：必填项、格式、字典兜底
     *
     * @param importDTOList  解析出的导入数据
     * @param rootParentName 导入目标父级名称（仅用于提示，可为空）
     */
    public static void validateAndPreprocess(List<SysMenuImportVo> importDTOList, String rootParentName) {
        if (CollUtil.isEmpty(importDTOList)) {
            throw new ServiceException("Excel中无有效数据");
        }
        // 文件内的菜单ID只用作父子引用键，一旦重复就无法判定某行的父级是谁（映射会被后写的行覆盖）
        Map<Long, String> menuIdOwner = new HashMap<>();
        for (SysMenuImportVo dto : importDTOList) {
            if (StringUtils.isBlank(dto.getMenuName())) {
                throw new ServiceException("Excel中存在菜单名称为空的行，请检查后重新导入");
            }
            if (dto.getMenuId() != null) {
                String owner = menuIdOwner.putIfAbsent(dto.getMenuId(), dto.getMenuName());
                if (owner != null) {
                    throw new ServiceException("导入文件中菜单ID【" + dto.getMenuId() + "】重复（菜单【" + owner
                        + "】与【" + dto.getMenuName() + "】），父子层级无法判定，请修正或清空「菜单ID」列后重新导入");
                }
            }
            if (!MENU_TYPES.contains(dto.getMenuType())) {
                throw new ServiceException("菜单【" + dto.getMenuName() + "】的菜单类型必须为A（应用）、M（目录）、C（菜单）、F（按钮）");
            }
            // 手写/精简模板可能不填显示顺序，原实现直接比较会因拆箱抛 NPE
            if (dto.getOrderNum() == null) {
                dto.setOrderNum(0);
            } else if (dto.getOrderNum() < 0) {
                throw new ServiceException("菜单【" + dto.getMenuName() + "】的显示顺序不能为负数");
            }
            // 字典翻译失败（字典缺失/单元格被改坏）时 ExcelDictConvert 返回空串，
            // 直接入库会出现"菜单不显示""状态异常"，这里统一兜底成默认值
            if (!"0".equals(dto.getVisible()) && !"1".equals(dto.getVisible())) {
                dto.setVisible("0");
            }
            if (!"0".equals(dto.getStatus()) && !"1".equals(dto.getStatus())) {
                dto.setStatus("0");
            }
            if (!"0".equals(dto.getIsFrame()) && !"1".equals(dto.getIsFrame())) {
                dto.setIsFrame("1");
            }
            if (!"0".equals(dto.getIsCache()) && !"1".equals(dto.getIsCache())) {
                dto.setIsCache("0");
            }
        }
    }

    /**
     * 对当前导入的菜单按照目录层级排序（父在前、子在后）
     *
     * <p>注意：不再校验收到的"顶层行必须是导入目标本身"。
     * 导出文件是把某个应用的整棵树导出来（根行 + 各级子孙），导入时目标父级可能换成另一个应用/目录，
     * 这种情况下文件里的顶层行必须允许挂到导入目标下，否则"导出 → 导入到别处"永远报"非法导入"。</p>
     *
     * @param allMenus     所有导入的菜单列表
     * @param parentMenuId 导入目标父级菜单id
     */
    public static List<SysMenuImportVo> buildMenuOrderByLevel(List<SysMenuImportVo> allMenus, Long parentMenuId) {
        for (SysMenuImportVo menuVo : allMenus) {
            menuVo.setDeepLevel(getHierarchyId(menuVo, allMenus));
        }
        return allMenus.stream()
            .sorted(Comparator.comparing(SysMenuImportVo::getDeepLevel))
            .toList();
    }

    /**
     * 根据当前菜单获取所有父级层级（含自身），并生成层级ID（如层级5对应"012345"）
     *
     * @param currentMenu 当前菜单
     * @param allMenus    所有导入的菜单列表
     * @return 层级ID字符串（从根到当前菜单的层级序号拼接）
     */
    public static Long getHierarchyId(SysMenuImportVo currentMenu, List<SysMenuImportVo> allMenus) {
        List<SysMenuImportVo> hierarchy = new ArrayList<>();
        collectHierarchy(currentMenu, allMenus, hierarchy,
            Collections.newSetFromMap(new IdentityHashMap<>()));

        // 反转层级列表（根菜单→子菜单→当前菜单）
        CollUtil.reverse(hierarchy);

        // 生成层级ID（如层级5对应"012345"，根菜单为0级）
        StringBuilder hierarchyId = new StringBuilder();
        for (int i = 1; i <= hierarchy.size(); i++) {
            hierarchyId.append(i);
        }
        return Long.parseLong(hierarchyId.toString());
    }

    /**
     * 递归收集从当前菜单到根菜单的层级
     *
     * @param currentMenu 当前菜单
     * @param allMenus    所有菜单
     * @param hierarchy   用于存储层级的列表
     * @param visited     已访问节点，用于识别 Excel 中父子关系成环的脏数据
     */
    private static void collectHierarchy(SysMenuImportVo currentMenu, List<SysMenuImportVo> allMenus,
                                         List<SysMenuImportVo> hierarchy, Set<SysMenuImportVo> visited) {
        if (!visited.add(currentMenu)) {
            throw new ServiceException("菜单【" + currentMenu.getMenuName()
                + "】的父级关系存在循环引用，请检查Excel中的“父菜单ID/父菜单名称”");
        }
        if (visited.size() > MAX_DEEP_LEVEL) {
            throw new ServiceException("菜单【" + currentMenu.getMenuName()
                + "】层级超过" + MAX_DEEP_LEVEL + "层，请检查Excel中的“父菜单ID/父菜单名称”");
        }
        hierarchy.add(currentMenu);
        SysMenuImportVo parentMenu = findParentMenu(currentMenu, allMenus);
        if (parentMenu != null) {
            collectHierarchy(parentMenu, allMenus, hierarchy, visited);
        }
    }

    /**
     * 在导入数据中查找当前菜单的父级：优先父菜单ID，其次父菜单名称；都找不到视为顶层（导入到指定父级下）
     *
     * <p>注意：父级不在导入文件内是<b>正常情况</b>。导出/模板文件通常不含应用根或更上层的目录，
     * 导入目标由接口参数 {@code parentMenuId} 指定，这类行应由 {@link #resolveParentMenuId} 统一挂到
     * 导入目标下。因此这里找不到父级时返回 null，不能抛"父菜单在导入文件中不存在"，
     * 否则带「父菜单名称」列的模板（含单行模板）根本无法导入。</p>
     */
    private static SysMenuImportVo findParentMenu(SysMenuImportVo currentMenu, List<SysMenuImportVo> allMenus) {
        Long parentId = currentMenu.getParentId();
        if (parentId != null) {
            Optional<SysMenuImportVo> parentByParentId = allMenus.stream()
                .filter(menu -> parentId.equals(menu.getMenuId()) && menu != currentMenu)
                .findFirst();
            if (parentByParentId.isPresent()) {
                return parentByParentId.get();
            }
        }
        String parentName = currentMenu.getParentName();
        if (StringUtils.isBlank(parentName)) {
            return null;
        }
        return allMenus.stream()
            .filter(menu -> parentName.trim().equals(menu.getMenuName()) && menu != currentMenu)
            .findFirst()
            .orElse(null);
    }

    /**
     * 计算导入行实际要挂载的父菜单ID
     *
     * <p>Excel 里的父菜单ID 是"源数据"的 id，不能直接落库，必须按导入过程中建立的
     * {@code 源菜单ID -> 实际菜单ID} 映射转换，否则子菜单会挂到不存在的父级（孤儿菜单），
     * 底层菜单在页面上就"消失"了。</p>
     *
     * @param currentMenu    当前导入行
     * @param idMapping      源菜单ID -> 实际写入的菜单ID
     * @param nameMapping    菜单名称 -> 实际写入的菜单ID（Excel 未填菜单ID时兜底）
     * @param targetParentId 导入目标父级
     * @return 实际父菜单ID
     */
    public static Long resolveParentMenuId(SysMenuImportVo currentMenu, Map<Long, Long> idMapping,
                                           Map<String, Long> nameMapping, Long targetParentId) {
        if (currentMenu.getParentId() != null) {
            Long mappedParentId = idMapping.get(currentMenu.getParentId());
            if (mappedParentId != null) {
                return mappedParentId;
            }
        }
        String parentName = currentMenu.getParentName();
        if (StringUtils.isNotBlank(parentName)) {
            Long mappedParentId = nameMapping.get(parentName.trim());
            if (mappedParentId != null) {
                return mappedParentId;
            }
        }
        // 父级不在导入文件内（跨应用导出、应用根行、手写模板未写父级）：挂到导入目标下，避免孤儿菜单
        return targetParentId;
    }

}
