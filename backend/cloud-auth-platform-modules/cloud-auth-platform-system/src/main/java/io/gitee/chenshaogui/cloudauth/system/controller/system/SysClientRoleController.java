package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.EditGroup;
import io.gitee.chenshaogui.cloudauth.common.idempotent.annotation.RepeatSubmit;
import io.gitee.chenshaogui.cloudauth.common.log.annotation.Log;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientRoleBindBo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientRoleBo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientRoleDelBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientRoleVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 客户端角色管理
 *
 * @author sgchen
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/clientRole")
public class SysClientRoleController extends BaseController {

    private final ISysClientRoleService sysClientRoleService;

    /**
     * 查询客户端角色管理列表
     */
    @SaCheckPermission("system:clientRole:list")
    @GetMapping("/list")
    public TableDataInfo<SysClientRoleVo> list(SysClientRoleBo bo, PageQuery pageQuery) {
        return sysClientRoleService.queryPageList(bo, pageQuery);
    }


    /**
     * 新增客户端角色管理
     */
    @SaCheckPermission("system:clientRole:add")
    @Log(title = "客户端角色管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/add")
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SysClientRoleBo bo) {
        return toAjax(sysClientRoleService.insertByBo(bo));
    }

    /**
     * 修改客户端角色管理
     */
    @SaCheckPermission("system:clientRole:edit")
    @Log(title = "客户端角色管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/update")
    public R<Void> update(@Validated(EditGroup.class) @RequestBody SysClientRoleBo bo) {
        return toAjax(sysClientRoleService.updateByBo(bo));
    }

    /**
     * 删除客户端角色管理
     *
     * @param bo 主键串
     */
    @SaCheckPermission("system:clientRole:remove")
    @Log(title = "客户端角色管理", businessType = BusinessType.DELETE)
    @PostMapping("/delete")
    public R<Void> remove(@Validated @RequestBody SysClientRoleDelBo bo) {
        return toAjax(sysClientRoleService.deleteWithValidByIds(bo.getIdList()));
    }

    /**
     * 给客户端绑定角色
     */
    @SaCheckPermission("system:clientRole:add")
    @Log(title = "客户端角色管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/bindRoles")
    public R<Void> bindRoles(@Validated @RequestBody SysClientRoleBindBo bo) {
        return toAjax(sysClientRoleService.bindRolesToClient(bo.getClientId(), bo.getRoleIdList()));
    }


}
