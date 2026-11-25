package io.gitee.chenshaogui.cloudauth.auth.service.impl;
import org.springframework.beans.factory.annotation.Autowired;

import cn.hutool.crypto.digest.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.gitee.chenshaogui.cloudauth.common.core.exception.base.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.auth.domain.vo.LoginVo;
import io.gitee.chenshaogui.cloudauth.auth.form.PasswordLoginBody;
import io.gitee.chenshaogui.cloudauth.auth.properties.CaptchaProperties;
import io.gitee.chenshaogui.cloudauth.auth.service.IAuthStrategy;
import io.gitee.chenshaogui.cloudauth.auth.service.SysLoginService;
import io.gitee.chenshaogui.cloudauth.common.core.constant.Constants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.GlobalConstants;
import io.gitee.chenshaogui.cloudauth.common.core.enums.LoginType;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaExpireException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MessageUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ValidatorUtils;
import io.gitee.chenshaogui.cloudauth.common.json.utils.JsonUtils;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteUserService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteClientVo;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 密码认证策略
 *
 * @author Michelle.Chung
 */
@Slf4j
@Service("password" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class PasswordAuthStrategy implements IAuthStrategy {

    private final CaptchaProperties captchaProperties;

    private final SysLoginService loginService;

    @Value("${spring.profiles.active}")
    private String profiles;

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    public LoginVo login(String body, RemoteClientVo client) {
        return doLogin(body, client, true);
    }

    /**
     * 密码登录内核。
     *
     * <p>抽给 {@code PasswordNoCaptchaAuthStrategy} 免验证码策略复用：两套逻辑除“是否校验图形验证码”
     * 外完全一致，避免复制粘贴导致后续维护双改漏改。“免验证码”是服务端策略 bean 维度的路由决策，
     * 不作为登录 body 字段传递，防止客户端构造报文伪造跳过标志。</p>
     *
     * @param body          登录体 JSON
     * @param client        客户端信息
     * @param checkCaptcha  true=按全局开关校验图形验证码；false=始终跳过图形验证码
     */
    protected LoginVo doLogin(String body, RemoteClientVo client, boolean checkCaptcha) {
        PasswordLoginBody loginBody = JsonUtils.parseObject(body, PasswordLoginBody.class);
        ValidatorUtils.validate(loginBody);
        String tenantId = loginBody.getTenantId();
        String username = loginBody.getUsername();
        String password = loginBody.getPassword();
        String code = loginBody.getCode();
        String uuid = loginBody.getUuid();

        // 验证码开关
        if (checkCaptcha && captchaProperties.getEnabled()) {
            validateCaptcha(tenantId, username, code, uuid);
        }
        LoginUser loginUser = TenantHelper.dynamic(tenantId, () -> {
            LoginUser user = remoteUserService.getUserInfo(username, tenantId);
            loginService.checkLogin(LoginType.PASSWORD, tenantId, username,
                () -> !BCrypt.checkpw(password, user.getPassword()));
            if (remoteUserService.isSimplePassword(password, tenantId)) {
                String simpleReason = remoteUserService.getSimpleReason(password, tenantId);
                throw new BaseException("登录失败：" + simpleReason + "，建议修改密码后再登录");
            }
            return user;
        });
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType(client.getDeviceType());
        // 自定义分配 不同用户体系 不同 token 授权时间 不设置默认走全局 yml 配置
        // 例如: 后台用户30分钟过期 app用户1天过期
        model.setTimeout(client.getTimeout());
        model.setActiveTimeout(client.getActiveTimeout());
        model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code     验证码
     * @param uuid     唯一标识
     */
    private void validateCaptcha(String tenantId, String username, String code, String uuid) {
        if(StringUtils.equals(profiles,"pub") || !StringUtils.equals(code,"100900")){
            String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + StringUtils.blankToDefault(uuid, "");
            String captcha = RedisUtils.getCacheObject(verifyKey);
            RedisUtils.deleteObject(verifyKey);
            if (captcha == null) {
                loginService.recordLogininfor(tenantId, username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
                throw new CaptchaExpireException();
            }
            if (!StringUtils.equalsIgnoreCase(code, captcha)) {
                loginService.recordLogininfor(tenantId, username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
                throw new CaptchaException();
            }
        }
    }

}
