package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.gitee.chenshaogui.cloudauth.common.excel.annotation.ExcelDictFormat;
import io.gitee.chenshaogui.cloudauth.common.excel.convert.ExcelDictConvert;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


/**
 * 菜单对象导出VO
 *
 * @author sgchen
 */

@Data
@NoArgsConstructor
public class SysMenuExportVo implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    @ExcelProperty(value = "菜单ID")
    private Long menuId;

    /**
     * 菜单名称
     */
    @ExcelProperty(value = "菜单名称")
    private String menuName;

    /**
     * 父菜单ID
     */
    @ExcelProperty(value = "父菜单ID")
    private Long parentId;

    /**
     * 父菜单名称
     *
     * <p>列顺序必须与导入模板 {@link SysMenuImportVo} 完全一致：
     * Excel 读写按列序号映射字段，导出文件若缺少这一列，用户把导出数据粘到模板里时
     * 后续列会整体左移一列，"显示顺序"(Integer) 会读到路由地址文本而报转换错误。</p>
     */
    @ExcelProperty(value = "父菜单名称")
    private String parentName;

    /**
     * 显示顺序
     */
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
    private Integer isFrame;

    /**
     * 是否缓存（0缓存 1不缓存）
     */
    @ExcelProperty(value = "是否缓存", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=缓存,1=不缓存")
    private Integer isCache;

    /**
     * 菜单类型（M目录 C菜单 F按钮）
     */
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

}
