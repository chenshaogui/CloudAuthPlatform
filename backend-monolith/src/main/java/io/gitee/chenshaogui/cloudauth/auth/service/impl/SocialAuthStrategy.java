package io.gitee.chenshaogui.cloudauth.auth.service.impl;
import org.springframework.beans.factory.annotation.Autowired;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import io.gitee.chenshaogui.cloudauth.auth.domain.vo.LoginVo;
import io.gitee.chenshaogui.cloudauth.auth.form.SocialLoginBody;
import io.gitee.chenshaogui.cloudauth.auth.service.IAuthStrategy;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ValidatorUtils;
import io.gitee.chenshaogui.cloudauth.common.json.utils.JsonUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.social.config.properties.SocialProperties;
import io.gitee.chenshaogui.cloudauth.common.social.utils.SocialUtils;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteSocialService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteUserService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteClientVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteSocialVo;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 第三方授权策略
 *
 * @author thiszhc is 三三
 */
@Slf4j
@Service("social" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class SocialAuthStrategy implements IAuthStrategy {

    private final SocialProperties socialProperties;

    @Autowired
    private RemoteSocialService remoteSocialService;
    @Autowired
    private RemoteUserService remoteUserService;

    /**
     * 登录-第三方授权登录
     *
     * @param body     登录信息
     * @param client   客户端信息
     */
    @Override
    public LoginVo login(String body, RemoteClientVo client) {
        SocialLoginBody loginBody = JsonUtils.parseObject(body, SocialLoginBody.class);
        ValidatorUtils.validate(loginBody);
        log.info("当前第三方登录信息：请求参数：{}", JsonUtils.toJsonString(loginBody));
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(
            loginBody.getSource(), loginBody.getSocialCode(),
            loginBody.getSocialState(), socialProperties);
        if (!response.ok()) {
            throw new ServiceException(response.getMsg());
        }
        AuthUser authUserData = response.getData();

        List<RemoteSocialVo> list = remoteSocialService.selectByAuthId(authUserData.getSource() + authUserData.getUuid());
        if (CollUtil.isEmpty(list)) {
            throw new ServiceException("你还没有绑定第三方账号，绑定后才可以登录！");
        }
        RemoteSocialVo socialVo;
        if (TenantHelper.isEnable()) {
            Optional<RemoteSocialVo> opt = StreamUtils.findAny(list, x -> x.getTenantId().equals(loginBody.getTenantId()));
            if (opt.isEmpty()) {
                throw new ServiceException("对不起，你没有权限登录当前租户！");
            }
            socialVo = opt.get();
        } else {
            socialVo = list.get(0);
        }

        LoginUser loginUser = remoteUserService.getUserInfo(socialVo.getUserId(), socialVo.getTenantId());
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

}
