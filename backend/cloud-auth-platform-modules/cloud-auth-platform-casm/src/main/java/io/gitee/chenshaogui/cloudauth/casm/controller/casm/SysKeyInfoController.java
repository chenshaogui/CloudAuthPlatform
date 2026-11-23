package io.gitee.chenshaogui.cloudauth.casm.controller.casm;

import com.alibaba.fastjson.JSONObject;
import io.gitee.chenshaogui.cloudauth.casm.aspect.Auth;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.KeyInfoReqBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.RequestBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoRspVo;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysKeyInfoService;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.QueryGroup;
import io.gitee.chenshaogui.cloudauth.common.idempotent.annotation.RepeatSubmit;
import io.gitee.chenshaogui.cloudauth.common.log.annotation.Log;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.log.enums.OperatorType;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密钥信息管理
 *
 * @author JLL
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/hsm")
public class SysKeyInfoController extends BaseController {

    private final ISysKeyInfoService sysSysKeyInfoService;

    /**
     * 接口验证
     */
    @Log(title = "密钥申请", businessType = BusinessType.INSERT, operatorType = OperatorType.OTHER, ignoreUser = true)
    @RepeatSubmit()
    @Auth
    @PostMapping("/interior/key/test")
    public R<Void> test(@RequestBody JSONObject bo) {
        log.info("测试接口验证：{}", bo);
        return R.ok("It is ok!");
    }

    /**
     * 申请密钥
     */
    @Log(title = "密钥申请", businessType = BusinessType.INSERT, operatorType = OperatorType.OTHER, ignoreUser = true)
    @RepeatSubmit()
    @Auth
    @PostMapping("/interior/key/apply")
    public R<KeyInfoRspVo> applyKey(@Validated(AddGroup.class) @RequestBody RequestBo bo) {
        String data = bo.getData();
        JSONObject jsonObject = JSONObject.parseObject(data);
        KeyInfoReqBo reqBo = JSONObject.toJavaObject(jsonObject, KeyInfoReqBo.class);
        return R.ok(sysSysKeyInfoService.applyKey(reqBo));
    }

    /**
     * 密钥应用
     * @param bo
     */
    @Log(title = "密钥使用", businessType = BusinessType.EXPORT, operatorType = OperatorType.OTHER, ignoreUser = true)
    @Auth
    @PostMapping("/interior/key/use")
    public R<KeyInfoRspVo> useKey(@Validated(QueryGroup.class) @RequestBody RequestBo bo) {
        String data = bo.getData();
        JSONObject jsonObject = JSONObject.parseObject(data);
        KeyInfoReqBo reqBo = JSONObject.toJavaObject(jsonObject, KeyInfoReqBo.class);
        return R.ok(sysSysKeyInfoService.useKey(reqBo));
    }
}
