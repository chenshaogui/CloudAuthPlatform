package io.gitee.chenshaogui.cloudauth.auth.service.impl;

import io.gitee.chenshaogui.cloudauth.auth.domain.vo.LoginVo;
import io.gitee.chenshaogui.cloudauth.auth.properties.CaptchaProperties;
import io.gitee.chenshaogui.cloudauth.auth.service.IAuthStrategy;
import io.gitee.chenshaogui.cloudauth.auth.service.SysLoginService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteClientVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 密码认证策略（免图形验证码）
 *
 * <p>供总线 {@code loginByPasswordNoCaptcha} handler 经
 * {@code IAuthStrategy.login(body, client, "passwordNoCaptcha")} 按 bean 名路由进入，
 * 与 {@link PasswordAuthStrategy} 共用同一套登录内核（密码校验、错数锁定、弱密码提示等
 * 全部保留），仅固定跳过图形验证码——面向子系统自有风控、无需图形码的场景。</p>
 *
 * <p><b>安全边界</b>：“免验证码”是服务端策略 bean 维度的路由决策，不作为登录 body 字段传递：
 * 认证中心自己的 {@code POST /login}（{@code grantType=password}）仍走 {@link PasswordAuthStrategy}
 * 正常校验图形码，客户端构造报文无从伪造跳过标志。</p>
 *
 * <p>本策略有意跳过全局图形码闸门，因此<b>不要求</b>调用方 client 的
 * {@code sys_client.grant_type} 配置 {@code passwordNoCaptcha}；总线消息已由持有
 * secret-key 的客户端签名，是否开放该端点由各子系统接入决定。</p>
 *
 * @author sgchen
 */
@Slf4j
@Service("passwordNoCaptcha" + IAuthStrategy.BASE_NAME)
public class PasswordNoCaptchaAuthStrategy extends PasswordAuthStrategy {

    // 父类为 @RequiredArgsConstructor（captchaProperties、loginService 两个 final 字段），
    // 无隐式无参构造，子类必须显式透传依赖调用 super(...)。
    // 父类的 @DubboReference / @Value 字段仍由 Spring 沿继承链注入，无需在此重复声明。
    public PasswordNoCaptchaAuthStrategy(CaptchaProperties captchaProperties, SysLoginService loginService) {
        super(captchaProperties, loginService);
    }

    @Override
    public LoginVo login(String body, RemoteClientVo client) {
        // 与 PasswordAuthStrategy 同内核，仅跳过图形验证码
        return doLogin(body, client, false);
    }

}
