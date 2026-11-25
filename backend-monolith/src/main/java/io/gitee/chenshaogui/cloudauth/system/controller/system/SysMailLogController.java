package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailLogBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailLogVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMailLogService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮箱发送日志控制器
 *
 * @author sgchen
 * @since 2024-01-03
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mailLog")
public class SysMailLogController {

    private final ISysMailLogService sysMailLogService;

    /**
     * 查询邮箱发送日志列表
     */
    @Operation(summary = "系统管理-邮箱管理-发送日志-列表", tags = "系统管理")
    @SaCheckPermission("system:mailLog:list")
    @PostMapping("/list")
    public TableDataInfo<SysMailLogVo> list(SysMailLogBo param, PageQuery pageQuery) {
        return sysMailLogService.getPageByParam(param,pageQuery);
    }

}
