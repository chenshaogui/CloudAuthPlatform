package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.idempotent.annotation.RepeatSubmit;
import io.gitee.chenshaogui.cloudauth.common.log.annotation.Log;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysKeyInfoBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyInfoVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysKeyInfoService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 密钥信息管理
 *
 * @author JLL
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/keyInfo")
public class SysKeyInfoController extends BaseController {

    private final ISysKeyInfoService sysKeyInfoService;

    /**
     * 查询密钥信息管理列表
     */
    @SaCheckPermission("system:keyInfo:list")
    @GetMapping("/list")
    public TableDataInfo<SysKeyInfoVo> list(SysKeyInfoBo bo, PageQuery pageQuery) {
        return sysKeyInfoService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取密钥信息管理详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("system:keyInfo:query")
    @GetMapping("/{id}")
    public R<SysKeyInfoVo> getInfo(@NotNull(message = "主键不能为空")
                                  @PathVariable Long id) {
        return R.ok(sysKeyInfoService.viewById(id));
    }

    /**
     * 新增密钥信息管理
     */
    @SaCheckPermission("system:keyInfo:add")
    @Log(title = "密钥申请", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/applyKey")
    public R<SysKeyInfoVo> applyKey(@RequestBody SysKeyInfoBo bo) {
        return R.ok(sysKeyInfoService.applyKey(bo));
    }

    /**
     * 查询密钥信息列表
     */
    @SaCheckPermission("system:keyInfo:query")
    @Log(title = "密钥信息列表", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/queryList")
    public R<List<SysKeyInfoVo>> queryList(@RequestBody SysKeyInfoBo bo) {
        return R.ok(sysKeyInfoService.queryList(bo));
    }

    /**
     * 密钥轮换
     */
    @SaCheckPermission("system:keyInfo:remove")
    @Log(title = "密钥轮换", businessType = BusinessType.UPDATE)
    @PostMapping("/rotationKey")
    public R<SysKeyInfoVo> rotationKey(@RequestBody SysKeyInfoBo bo) {
        return R.ok(sysKeyInfoService.rotationKey(bo.getId()));
    }

    /**
     * 密钥销毁
     * @param bo
     * @return
     */
    @SaCheckPermission("system:keyInfo:remove")
    @Log(title = "密钥销毁", businessType = BusinessType.UPDATE)
    @PostMapping("/distroyKey")
    public R<Void> distroyKey(@RequestBody SysKeyInfoBo bo) {
        return toAjax(sysKeyInfoService.distroyKey(bo.getId()));
    }
}
