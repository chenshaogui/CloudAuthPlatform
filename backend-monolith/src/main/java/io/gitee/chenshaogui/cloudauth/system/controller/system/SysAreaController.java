package io.gitee.chenshaogui.cloudauth.system.controller.system;

import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysAreaBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysAreaVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 区域信息操作处理
 *
 * @author sgchen
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/area")
public class SysAreaController extends BaseController {

    private final ISysAreaService areaService;

    /**
     * 获取区域列表
     */
    @GetMapping("/list")
    public R<List<SysAreaVo>> list(SysAreaBo bo) {
        return R.ok(areaService.getAreaList(bo));
    }

    /**
     * 根据id获取区域
     *
     * @param areaCode 区域编码
     */
    @GetMapping(value = "/{areaCode}")
    public R<SysAreaVo> getAreaCode(@PathVariable String areaCode) {
        SysAreaVo sysAreaByCode = areaService.getSysAreaByCode(areaCode);
        return R.ok(sysAreaByCode);
    }

}
