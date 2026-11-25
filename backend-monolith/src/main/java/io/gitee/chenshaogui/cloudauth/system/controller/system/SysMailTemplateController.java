package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.convert.Convert;
import io.gitee.chenshaogui.cloudauth.common.core.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.log.annotation.Log;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMailTemplate;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailTemplateBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailTemplateVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 邮箱模板控制器
 *
 * @author sgchen
 * @since 2024-01-02
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mailTemplate")
public class SysMailTemplateController {

    private final ISysMailTemplateService sysMailTemplateService;

    /**
     * 查询邮箱模板列表
     */
    @Operation(summary = "系统管理-邮箱管理-邮箱模板-列表", tags = "系统管理")
    @SaCheckPermission("system:mailTemplate:list")
    @SaCheckRole(value = {
        TenantConstants.SUPER_ADMIN_ROLE_KEY,
        TenantConstants.TENANT_ADMIN_ROLE_KEY
    }, mode = SaMode.OR)
    @PostMapping("/list")
    public TableDataInfo<SysMailTemplateVo> list(@RequestBody SysMailTemplateBo param, PageQuery pageQuery) {
        return sysMailTemplateService.getPageByParam(param, pageQuery);
    }

    /**
     * 新增邮箱模板
     */
    @Operation(summary = "系统管理-邮箱管理-邮箱模板-新增", tags = "系统管理")
    @SaCheckPermission("system:mailTemplate:add")
    @SaCheckRole(value = {
        TenantConstants.SUPER_ADMIN_ROLE_KEY,
        TenantConstants.TENANT_ADMIN_ROLE_KEY
    }, mode = SaMode.OR)
    @Log(title = "邮箱模板-新增", businessType = BusinessType.INSERT)
    @PostMapping("add")
    public R<Void> add(@Validated @RequestBody SysMailTemplateBo param) {
        boolean result = sysMailTemplateService.add(Convert.convert(SysMailTemplate.class, param));
        return result ? R.ok() : R.fail();
    }

    /**
     * 修改邮箱模板
     */
    @Operation(summary = "系统管理-邮箱管理-邮箱模板-修改", tags = "系统管理")
    @SaCheckPermission("system:mailTemplate:edit")
    @SaCheckRole(value = {
        TenantConstants.SUPER_ADMIN_ROLE_KEY,
        TenantConstants.TENANT_ADMIN_ROLE_KEY
    }, mode = SaMode.OR)
    @Log(title = "邮箱模板-修改", businessType = BusinessType.UPDATE)
    @PostMapping("edit")
    public R<Void> edit(@Validated @RequestBody SysMailTemplateBo param) {
        boolean result = sysMailTemplateService.edit(Convert.convert(SysMailTemplate.class, param));
        return result ? R.ok() : R.fail();
    }

}
