package io.gitee.chenshaogui.cloudauth.system.controller.system;


import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.exception.base.BaseException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserClientConfigBatchAddBo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserClientConfigBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserClientConfigVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserClientConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户-客户端配置控制器
 */
@RestController
@RequestMapping("/userClientConfig")
public class SysUserClientConfigController {

    @Resource
    private ISysUserClientConfigService configService;

    /**
     * 保存/更新用户-客户端配置
     */
    @PostMapping("/saveOrUpdate")
    public R<Boolean> saveOrUpdate(@Validated @RequestBody SysUserClientConfigBo bo) {
        Long userId = bo.getUserId();
        String clientId = bo.getClientId();
        if(userId==null && StringUtils.isBlank(clientId)){
            throw new BaseException("userId和clientId不能为空");
        }
        return R.ok(configService.saveOrUpdateConfig(bo));
    }

    /**
     * 删除用户-客户端配置
     */
    @PostMapping("/delete")
    public R<Boolean> delete(@Validated @RequestBody SysUserClientConfigBo bo) {
        Long userId = bo.getUserId();
        String clientId = bo.getClientId();
        if(userId==null && StringUtils.isBlank(clientId)){
            throw new BaseException("userId和clientId不能为空");
        }
        return R.ok(configService.delete(bo)>0);
    }


    /**
     * 批量保存配置
     */
    @PostMapping("/batchSave")
    public R<Boolean> batchSave(@Validated @RequestBody SysUserClientConfigBatchAddBo bo) {
        return R.ok(configService.batchSaveConfig(bo.getConfigList()));
    }


    /**
     * 根据用户ID+客户端ID查询配置
     */
    @PostMapping("/getConfigByClient")
    public R<SysUserClientConfigVo> getConfigByClient(@Validated @RequestBody SysUserClientConfigBo bo) {
        Long userId = bo.getUserId();
        String clientId = bo.getClientId();
        if(userId==null && StringUtils.isBlank(clientId)){
            throw new BaseException("userId和clientId不能为空");
        }
        return R.ok(configService.getConfigByUserIdAndClientId(userId, clientId));
    }

    @PostMapping("/getConfigById")
    public R<SysUserClientConfigVo> getConfigById(@Validated @RequestBody SysUserClientConfigBo bo) {
        return R.ok(configService.getConfigById(bo.getId()));
    }

    /**
     * 根据用户ID查询所有客户端配置
     */
    @PostMapping("/listByUserId")
    public R<List<SysUserClientConfigVo>> listByUserId(@Validated @RequestBody SysUserClientConfigBo bo) {
        Long userId = bo.getUserId();
        if(userId==null){
            throw new BaseException("userId不能为空");
        }
        return R.ok(configService.listByUserId(userId));
    }

}
