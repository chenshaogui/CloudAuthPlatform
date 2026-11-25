package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import io.gitee.chenshaogui.cloudauth.common.excel.annotation.ExcelDictFormat;
import io.gitee.chenshaogui.cloudauth.common.excel.annotation.ExcelNotation;
import io.gitee.chenshaogui.cloudauth.common.excel.convert.ExcelDictConvert;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


/**
 * 菜单对象导入VO
 *
 * @author sgchen
 */

@Data
@NoArgsConstructor
public class SysMenuImportVo implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    @ExcelNotation(value = "仅作为本文件内父子关系的引用键，可留空。导入时会重新生成新的菜单ID，不会覆盖库中已有菜单。请保持文本格式，19位数字不要转成数值。")
    @ExcelProperty(value = "菜单ID")
    private Long menuId;

    /**
     * 菜单名称
     */
    @ExcelNotation(value = "必填，同一父级下不要重名。")
    @ExcelProperty(value = "菜单名称")
    private String menuName;

    /**
     * 父菜单ID
     */
    @ExcelNotation(value = "填本文件中某一行的「菜单ID」即可表达父子层级；留空或指向文件外的ID时，该行会挂到本次导入所选的菜单下。请保持文本格式。")
    @ExcelProperty(value = "父菜单ID")
    private Long parentId;

    @ExcelNotation(value = "仅作对照，可留空；导入优先按「父菜单ID」还原层级。父级不在本文件中不会报错。")
    @ExcelProperty(value = "父菜单名称")
    private String parentName;

    /**
     * 显示顺序
     */
    @ExcelNotation(value = "整数，留空按 0 处理。")
    @ExcelProperty(value = "显示顺序")
    private Integer orderNum;

    /**
     * 路由地址
     */
    @ExcelProperty(value = "路由地址")
    private String path;

    /**
     * 组件路径
     */
    @ExcelProperty(value = "组件路径")
    private String component;

    /**
     * 路由参数
     */
    @ExcelProperty(value = "路由参数")
    private String queryParam;

    /**
     * 是否为外链（0是 1否）
     */
    @ExcelProperty(value = "是否为外链", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=是,1=否")
    private String isFrame;

    /**
     * 是否缓存（0缓存 1不缓存）
     */
    @ExcelProperty(value = "是否缓存", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=缓存,1=不缓存")
    private String isCache;

    /**
     * 菜单类型（A应用 M目录 C菜单 F按钮）
     */
    @ExcelNotation(value = "A应用 / M目录 / C菜单 / F按钮。导入到某个应用或目录下时请填 M、C 或 F，不要填 A。")
    @ExcelProperty(value = "菜单类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "A=应用,M=目录,C=菜单,F=按钮")
    private String menuType;

    /**
     * 显示状态（0显示 1隐藏）
     */
    @ExcelProperty(value = "显示状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_show_hide")
    private String visible;

    /**
     * 权限标识
     */
    @ExcelProperty(value = "权限标识")
    private String perms;

    /**
     * 菜单图标
     */
    @ExcelProperty(value = "菜单图标")
    private String icon;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 菜单状态（0正常 1停用）
     */
    @ExcelProperty(value = "菜单状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private String status;

    /**
     * 当前菜单所在的深度层级（导入时内部使用，不参与 Excel 列，
     * 否则模板会多出名为 deepLevel 的英文列）
     */
    @ExcelIgnore
    private Long deepLevel;

}
