package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysKeyRotationBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyRotationVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysKeyRotationService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密钥轮换管理
 *
 * @author JLL
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/keyRotation")
public class SysKeyRotationController extends BaseController {

    private final ISysKeyRotationService sysKeyRotationService;

    /**
     * 查询密钥轮换管理列表
     */
    @SaCheckPermission("system:keyRotaton:list")
    @GetMapping("/list")
    public TableDataInfo<SysKeyRotationVo> list(SysKeyRotationBo bo, PageQuery pageQuery) {
        return sysKeyRotationService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取密钥轮换管理详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("system:keyRotaton:query")
    @GetMapping("/{id}")
    public R<SysKeyRotationVo> getInfo(@NotNull(message = "主键不能为空")
                                  @PathVariable Long id) {
        return R.ok(sysKeyRotationService.viewById(id));
    }
}
