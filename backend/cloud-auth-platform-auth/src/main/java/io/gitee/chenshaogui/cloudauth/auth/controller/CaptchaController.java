package io.gitee.chenshaogui.cloudauth.auth.controller;

import cn.hutool.captcha.AbstractCaptcha;
import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.auth.domain.vo.CaptchaVo;
import io.gitee.chenshaogui.cloudauth.auth.enums.CaptchaType;
import io.gitee.chenshaogui.cloudauth.auth.properties.CaptchaProperties;
import io.gitee.chenshaogui.cloudauth.common.core.constant.Constants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.GlobalConstants;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.reflect.ReflectUtils;
import io.gitee.chenshaogui.cloudauth.common.ratelimiter.annotation.RateLimiter;
import io.gitee.chenshaogui.cloudauth.common.ratelimiter.enums.LimitType;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 验证码操作处理
 *
 * @author Lion Li
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
public class CaptchaController {

    private final CaptchaProperties captchaProperties;

    /**
     * 生成验证码
     */
    @GetMapping("/code")
    public R<CaptchaVo> getCode() {
        CaptchaVo captchaVo = new CaptchaVo();
        boolean captchaEnabled = captchaProperties.getEnabled();
        if (!captchaEnabled) {
            captchaVo.setCaptchaEnabled(false);
            return R.ok(captchaVo);
        }
        return R.ok(SpringUtils.getAopProxy(this).getCodeImpl());
    }

    /**
     * 生成验证码
     * 独立方法避免验证码关闭之后仍然走限流
     */
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public CaptchaVo getCodeImpl() {
        return generateCaptcha();
    }

    /**
     * 供 SSO 消息总线调用：不走 IP 限流，直接生成（或返回未开启标记）
     */
    public CaptchaVo getCodeForBus() {
        CaptchaVo captchaVo = new CaptchaVo();
        if (!captchaProperties.getEnabled()) {
            captchaVo.setCaptchaEnabled(false);
            return captchaVo;
        }
        return generateCaptcha();
    }

    /** 真正的验证码生成逻辑（原 getCodeImpl 方法体搬过来） */
    public CaptchaVo generateCaptcha() {
        // 保存验证码信息
        String uuid = IdUtil.simpleUUID();
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + uuid;
        // 生成验证码
        CaptchaType captchaType = captchaProperties.getType();
        CodeGenerator codeGenerator;
        if (CaptchaType.MATH == captchaType) {
            codeGenerator = ReflectUtils.newInstance(captchaType.getClazz(), captchaProperties.getNumberLength(), false);
        } else {
            codeGenerator = ReflectUtils.newInstance(captchaType.getClazz(), captchaProperties.getCharLength());
        }
        AbstractCaptcha captcha = SpringUtils.getBean(captchaProperties.getCategory().getClazz());
        captcha.setGenerator(codeGenerator);
        captcha.createCode();
        // 如果是数学验证码，使用SpEL表达式处理验证码结果
        String code = captcha.getCode();
        if (CaptchaType.MATH == captchaType) {
            ExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(StringUtils.remove(code, "="));
            code = exp.getValue(String.class);
        }
        RedisUtils.setCacheObject(verifyKey, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        CaptchaVo captchaVo = new CaptchaVo();
        captchaVo.setUuid(uuid);
        captchaVo.setImg(captcha.getImageBase64());
        return captchaVo;
    }

}
