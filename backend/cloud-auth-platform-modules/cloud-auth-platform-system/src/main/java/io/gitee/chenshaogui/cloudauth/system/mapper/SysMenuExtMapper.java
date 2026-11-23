package io.gitee.chenshaogui.cloudauth.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.mapper.BaseMapperPlus;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenuExt;

/**
 * 菜单扩展信息 数据层
 *
 * <p>与 sys_menu 一对一映射，存储来自 ln_pro sys_sec_operation 的独有字段。</p>
 *
 * <p>加 InterceptorIgnore 注解的 tenantLine 属性跳过租户拦截器：
 * sys_menu_ext 无 tenant_id 列，通过主表 sys_menu 完成租户过滤（菜单本就不按租户切分）。</p>
 *
 * @author Lion Li
 */
@InterceptorIgnore(tenantLine = "true")
public interface SysMenuExtMapper extends BaseMapperPlus<SysMenuExt, SysMenuExt> {

}