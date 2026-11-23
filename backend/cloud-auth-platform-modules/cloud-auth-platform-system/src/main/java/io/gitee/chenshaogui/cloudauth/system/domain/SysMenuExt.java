package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 菜单扩展信息（来自 ln_pro sys_sec_operation 独有字段）
 *
 * <p>数据迁移专用表，与 sys_menu 1:1 关联（主键 menu_id 对齐 sys_menu.menu_id），
 * 存储 sys_menu 主表不承载的扩展字段：来源标识、路由、跳转、日志配置、桌面布局等。</p>
 *
 * @author Lion Li
 */
@Data
@TableName("sys_menu_ext")
public class SysMenuExt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID（关联 sys_menu.menu_id，主键）
     */
    @TableId(value = "menu_id", type = IdType.INPUT)
    private Long menuId;

    // ==================== 路由 / 跳转 ====================

    /**
     * 是否路由菜单（IS_ROUTE，0:不是 1:是）
     */
    private Integer isRoute;

    /**
     * 是否缓存路由（KEEP_ALIVE，0:否 1:是）
     */
    private Integer keepAlive;

    /**
     * 默认跳转地址（REDIRECT）
     */
    private String redirectUrl;

    /**
     * 打开方式（INTERNAL_OR_EXTERNAL，0:内部 1:外部）
     */
    private Integer internalExternal;

    /**
     * 类名（CLASSNAME）
     */
    private String className;

    // ==================== 日志 / 桌面 ====================

    /**
     * 是否记录日志（REMEMBERLOG）
     */
    private Long rememberLog;

    /**
     * 日志字符串（LOGSTRING）
     */
    private String logString;

    /**
     * 配置 XML（CONFIG，老系统桌面布局）
     */
    private String config;

}