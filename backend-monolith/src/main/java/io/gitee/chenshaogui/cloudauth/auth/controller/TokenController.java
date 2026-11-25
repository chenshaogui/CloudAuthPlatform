package io.gitee.chenshaogui.cloudauth.auth.controller;
import org.springframework.beans.factory.annotation.Autowired;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsByPhoneBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsBySetBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsForRegisterBody;
import io.gitee.chenshaogui.cloudauth.auth.form.ResetPasswordBody;
import io.gitee.chenshaogui.cloudauth.auth.form.UpdatePasswordBody;
import io.gitee.chenshaogui.cloudauth.auth.form.PhoneLoginBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SmsLoginBody;
import io.gitee.chenshaogui.cloudauth.auth.util.SmsSendGuard;
import io.gitee.chenshaogui.cloudauth.common.core.constant.Constants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.GlobalConstants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.enums.LoginType;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.base.BaseException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.UserException;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import io.gitee.chenshaogui.cloudauth.resource.api.RemoteSmsService;
import io.gitee.chenshaogui.cloudauth.resource.api.domain.RemoteSms;
import io.gitee.chenshaogui.cloudauth.system.api.*;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import io.gitee.chenshaogui.cloudauth.auth.domain.vo.LoginTenantVo;
import io.gitee.chenshaogui.cloudauth.auth.domain.vo.LoginVo;
import io.gitee.chenshaogui.cloudauth.auth.domain.vo.TenantListVo;
import io.gitee.chenshaogui.cloudauth.auth.form.RegisterBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SocialLoginBody;
import io.gitee.chenshaogui.cloudauth.auth.service.IAuthStrategy;
import io.gitee.chenshaogui.cloudauth.auth.service.SysLoginService;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.domain.model.LoginBody;
import io.gitee.chenshaogui.cloudauth.common.core.utils.*;
import io.gitee.chenshaogui.cloudauth.common.encrypt.annotation.ApiEncrypt;
import io.gitee.chenshaogui.cloudauth.common.json.utils.JsonUtils;
import io.gitee.chenshaogui.cloudauth.common.ratelimiter.annotation.RateLimiter;
import io.gitee.chenshaogui.cloudauth.common.ratelimiter.enums.LimitType;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.social.config.properties.SocialLoginConfigProperties;
import io.gitee.chenshaogui.cloudauth.common.social.config.properties.SocialProperties;
import io.gitee.chenshaogui.cloudauth.common.social.utils.SocialUtils;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.resource.api.RemoteMessageService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteClientVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteTenantVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * token 控制
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class TokenController {

    private final SocialProperties socialProperties;
    private final SysLoginService sysLoginService;
    private final ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private final RemoteConfigService remoteConfigService;
    @Autowired
    private final RemoteTenantService remoteTenantService;
    @Autowired
    private final RemoteClientService remoteClientService;
    @Autowired
    private final RemoteSocialService remoteSocialService;
    @Autowired
    private final RemoteMessageService remoteMessageService;
    @Autowired
    private final RemoteUserService remoteUserService;
    @Autowired
    private final RemoteSmsService remoteSmsService;

    /**
     * 登录方法
     *
     * @param body 登录信息
     * @return 结果
     */
    @ApiEncrypt
    @PostMapping("/login")
    public R<LoginVo> login(@RequestBody String body) {
        LoginBody loginBody = JsonUtils.parseObject(body, LoginBody.class);
        ValidatorUtils.validate(loginBody);
        // 授权类型和客户端id
        String clientId = loginBody.getClientId();
        String grantType = loginBody.getGrantType();
        RemoteClientVo clientVo = remoteClientService.queryByClientId(clientId);

        // 查询不到 client 或 client 内不包含 grantType
        if (ObjectUtil.isNull(clientVo) || !StringUtils.contains(clientVo.getGrantType(), grantType)) {
            log.info("客户端id: {} 认证类型：{} 异常!.", clientId, grantType);
            return R.fail(MessageUtils.message("auth.grant.type.error"));
        } else if (!SystemConstants.NORMAL.equals(clientVo.getStatus())) {
            return R.fail(MessageUtils.message("auth.grant.type.blocked"));
        }
        // 校验租户
        sysLoginService.checkTenant(loginBody.getTenantId());
        // 登录
        LoginVo loginVo = IAuthStrategy.login(body, clientVo, grantType);

        Long userId = LoginHelper.getUserId();
        scheduledExecutorService.schedule(() -> {
            remoteMessageService.publishMessage(List.of(userId), "欢迎登录统一认证中心");
        }, 5, TimeUnit.SECONDS);
        return R.ok(loginVo);
    }

    /**
     * 第三方登录请求
     *
     * @param source 登录来源
     * @return 结果
     */
    @GetMapping("/binding/{source}")
    public R<String> authBinding(@PathVariable("source") String source,
                                 @RequestParam String tenantId, @RequestParam String domain) {
        SocialLoginConfigProperties obj = socialProperties.getType().get(source);
        if (ObjectUtil.isNull(obj)) {
            return R.fail(source + "平台账号暂不支持");
        }
        AuthRequest authRequest = SocialUtils.getAuthRequest(source, socialProperties);
        Map<String, String> map = new HashMap<>();
        map.put("tenantId", tenantId);
        map.put("domain", domain);
        map.put("state", AuthStateUtils.createState());
        String authorizeUrl = authRequest.authorize(Base64.encode(JsonUtils.toJsonString(map), StandardCharsets.UTF_8));
        return R.ok("操作成功", authorizeUrl);
    }

    /**
     * 第三方登录回调业务处理 绑定授权
     *
     * @param loginBody 请求体
     * @return 结果
     */
    @PostMapping("/social/callback")
    public R<Void> socialCallback(@RequestBody SocialLoginBody loginBody) {
        // 获取第三方登录信息
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(
            loginBody.getSource(), loginBody.getSocialCode(),
            loginBody.getSocialState(), socialProperties);
        AuthUser authUserData = response.getData();
        // 判断授权响应是否成功
        if (!response.ok()) {
            return R.fail(response.getMsg());
        }
        sysLoginService.socialRegister(authUserData);
        return R.ok();
    }


    /**
     * 取消授权
     *
     * @param socialId socialId
     */
    @DeleteMapping(value = "/unlock/{socialId}")
    public R<Void> unlockSocial(@PathVariable Long socialId) {
        Boolean rows = remoteSocialService.deleteWithValidById(socialId);
        return rows ? R.ok() : R.fail("取消授权失败");
    }

    /**
     * 登出方法
     */
    @PostMapping("logout")
    public R<Void> logout() {
        sysLoginService.logout();
        return R.ok();
    }

    /**
     * 用户注册
     */
    @ApiEncrypt
    @PostMapping("register")
    public R<Void> register(@RequestBody RegisterBody registerBody) {
        if (!remoteConfigService.selectRegisterEnabled(registerBody.getTenantId())) {
            return R.fail("当前系统没有开启注册功能！");
        }
        // 校验该 client 是否开放注册（sys_client.register_enabled：1=开放 0=关闭，默认关闭）
        try {
            RemoteClientVo clientVo = remoteClientService.queryByClientId(registerBody.getClientId());
            if (clientVo != null && !"1".equals(clientVo.getRegisterEnabled())) {
                return R.fail("该客户端未开放注册功能");
            }
        } catch (Exception ignore) {
            // 客户端不存在/查询失败时，按"未配置"放过，由租户总开关控制
        }
        // 用户注册
        sysLoginService.register(registerBody);
        return R.ok();
    }

    /**
     * 登录页面租户下拉框
     *
     * @return 租户列表
     */
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @GetMapping("/tenant/list")
    public R<LoginTenantVo> tenantList(HttpServletRequest request) throws Exception {
        // 返回对象
        LoginTenantVo result = new LoginTenantVo();
        boolean enable = TenantHelper.isEnable();
        result.setTenantEnabled(enable);
        // 如果未开启租户这直接返回
        if (!enable) {
            return R.ok(result);
        }

        List<RemoteTenantVo> tenantList = remoteTenantService.queryList();
        List<TenantListVo> voList = MapstructUtils.convert(tenantList, TenantListVo.class);
        try {
            // 如果只超管返回所有租户
            if (LoginHelper.isSuperAdmin()) {
                result.setVoList(voList);
                return R.ok(result);
            }
        } catch (NotLoginException ignored) {
        }

        // 获取域名
        String host;
        String referer = request.getHeader("referer");
        if (StringUtils.isNotBlank(referer)) {
            // 这里从referer中取值是为了本地使用hosts添加虚拟域名，方便本地环境调试
            host = referer.split("//")[1].split("/")[0];
        } else {
            host = new URL(request.getRequestURL().toString()).getHost();
        }
        // 根据域名进行筛选
        List<TenantListVo> list = StreamUtils.filter(voList, vo ->
            StringUtils.equalsIgnoreCase(vo.getDomain(), host));
        result.setVoList(CollUtil.isNotEmpty(list) ? list : voList);
        return R.ok(result);
    }

    /**
     * 兼容旧前端的租户下拉框路径（/auth/tenant）
     */
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @GetMapping("/tenant")
    public R<LoginTenantVo> tenant(HttpServletRequest request) throws Exception {
        return tenantList(request);
    }

    /**
     * 通过用户名称发送短信验证码
     *
     * @param smsBody 短信消息体
     */
    @PostMapping("/sendSmsCode")
    public R<Void> sendSmsCode(@RequestBody @Validated SendSmsBody smsBody) {
        LoginUser userInfo = remoteUserService.getUserInfo(smsBody.getUsername(), smsBody.getTenantId());
        if(StringUtils.isEmpty(userInfo.getPhonenumber())){
            log.error("登录账号手机号不存在 => {}", smsBody.getUsername());
            return R.fail("登录账号手机号不存在");
        }
        // 检查用户名和密码
        sysLoginService.checkLogin(LoginType.PASSWORD, smsBody.getTenantId(), smsBody.getUsername(), () -> !BCrypt.checkpw(smsBody.getPassword(), userInfo.getPassword()));

        // 发送前限流（只读，不消耗额度）
        String forbid = SmsSendGuard.checkBeforeSend(userInfo.getPhonenumber());
        if (forbid != null) {
            return R.fail(forbid);
        }

        String key = GlobalConstants.CAPTCHA_CODE_KEY + userInfo.getUsername();
        String code = RandomUtil.randomNumbers(6);
        RedisUtils.setCacheObject(key, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        // 验证码模板id
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
        map.put("code", code);
        RemoteSms smsResponse = remoteSmsService.sendMessage(userInfo.getPhonenumber(), map);
        if (!smsResponse.getSuccess()) {
            log.error("验证码短信发送异常 => {}", smsResponse);
            return R.fail(smsResponse.getResponse());
        }
        // 仅发送成功后才锁
        SmsSendGuard.markSent(userInfo.getPhonenumber());
        return R.ok();
    }

    /**
     * 通过用户名称发送短信验证码
     *
     * @param body 消息发送体
     */
    @PostMapping("/sendSmsCodeByReset")
    public R<Void> sendSmsCodeByReset(@RequestBody @Validated SendSmsBySetBody body) {
        // 校验该 client 是否开放重置密码（sys_client.reset_pwd_enabled：1=开放 0=关闭，默认开放）
        if (StringUtils.isNotBlank(body.getClientId())) {
            try {
                RemoteClientVo clientVo = remoteClientService.queryByClientId(body.getClientId());
                if (clientVo != null && !"1".equals(clientVo.getResetPwdEnabled())) {
                    return R.fail("该客户端未开放重置密码功能");
                }
            } catch (Exception ignore) {
                // 客户端不存在/查询失败时按"未配置"放过
            }
        }
        if (remoteUserService.isSimplePassword(body.getNewPassword(), body.getTenantId())) {
            String simpleReason = remoteUserService.getSimpleReason(body.getNewPassword(), body.getTenantId());
            throw new BaseException("发送验证码失败：" + simpleReason + "，建议修改密码后");
        }
        SendSmsBody smsBody = new SendSmsBody();
        smsBody.setUsername(body.getUsername());
        smsBody.setPassword(body.getOldPassword());
        smsBody.setTenantId(body.getTenantId());
        return this.sendSmsCode(smsBody);
    }

    /**
     * 通过手机号发送短信验证码（免密码，用于"手机号 + 验证码"登录）
     *
     * <p>与 {@link #sendSmsCode} 的区别：本接口仅需手机号，不校验账号密码。
     * 验证码写入 Redis 的 key 与 {@link io.gitee.chenshaogui.cloudauth.auth.service.impl.SmsAuthStrategy}
     * 保持一致（{@code CAPTCHA_CODE_KEY + phonenumber}），因此可直接配合 sms 授权类型登录。</p>
     *
     * @param body 手机号发送体
     */
    @PostMapping("/sendSmsCodeByPhone")
    public R<Void> sendSmsCodeByPhone(@RequestBody @Validated SendSmsByPhoneBody body) {
        LoginUser userInfo;
        try {
            userInfo = TenantHelper.dynamic(body.getTenantId(), () ->
                remoteUserService.getUserInfoByPhonenumber(body.getPhonenumber(), body.getTenantId()));
        } catch (ServiceException | UserException e) {
            // 业务异常透传明确原因（如"手机号绑定多个账号"），避免误报"未注册或不可用"
            log.warn("按手机号发送验证码：查询用户失败 phone={}, err={}", body.getPhonenumber(), e.getMessage());
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("按手机号发送验证码：查询用户失败 phone={}, err={}", body.getPhonenumber(), e.getMessage());
            return R.fail("该手机号未注册或不可用");
        }
        if (ObjectUtil.isNull(userInfo) || StringUtils.isEmpty(userInfo.getPhonenumber())) {
            log.error("登录手机号不存在 => {}", body.getPhonenumber());
            return R.fail("该手机号未注册或不可用");
        }

        // 发送前限流（只读，不消耗额度）
        String forbid = SmsSendGuard.checkBeforeSend(body.getPhonenumber());
        if (forbid != null) {
            return R.fail(forbid);
        }

        String key = GlobalConstants.CAPTCHA_CODE_KEY + body.getPhonenumber();
        String code = RandomUtil.randomNumbers(6);
        RedisUtils.setCacheObject(key, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        // 验证码模板id
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
        map.put("code", code);
        RemoteSms smsResponse = remoteSmsService.sendMessage(userInfo.getPhonenumber(), map);
        if (!smsResponse.getSuccess()) {
            log.error("验证码短信发送异常 => {}", smsResponse);
            return R.fail(smsResponse.getResponse());
        }
        // 仅发送成功后才锁
        SmsSendGuard.markSent(body.getPhonenumber());
        return R.ok();
    }

    /**
     * 注册场景发送短信验证码（面向尚未注册的手机号，服务端不校验号码存在性）
     *
     * <p>与 {@link #sendSmsCodeByPhone} 的区别：本接口不得调用 getUserInfoByPhonenumber
     * （那会因号码未注册而报错），直接生成验证码 + 限流 + 下发。</p>
     *
     * @param body 注册短信发送体
     */
    @PostMapping("/sendSmsCodeForRegister")
    public R<Void> sendSmsCodeForRegister(@RequestBody @Validated SendSmsForRegisterBody body) {
        // 发送前限流（只读，不消耗额度）
        String forbid = SmsSendGuard.checkBeforeSend(body.getPhonenumber());
        if (forbid != null) {
            return R.fail(forbid);
        }
        String key = GlobalConstants.CAPTCHA_CODE_KEY + body.getPhonenumber();
        String code = RandomUtil.randomNumbers(6);
        RedisUtils.setCacheObject(key, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
        map.put("code", code);
        RemoteSms smsResponse = remoteSmsService.sendMessage(body.getPhonenumber(), map);
        if (!smsResponse.getSuccess()) {
            log.error("注册验证码短信发送异常 => {}", smsResponse);
            return R.fail(smsResponse.getResponse());
        }
        // 仅发送成功后才锁
        SmsSendGuard.markSent(body.getPhonenumber());
        return R.ok();
    }

    /**
     * 忘记密码 / 重置密码：手机号 + 短信验证码 + 新密码
     *
     * <p>验证码由 sendSmsCodeByPhone 下发，key = CAPTCHA_CODE_KEY + phonenumber；
     * 校验通过并改密成功后删除该 key，防止一码多用。</p>
     *
     * @param body 重置密码体
     */
    @PostMapping("/resetPassword")
    public R<Void> resetPassword(@RequestBody @Validated ResetPasswordBody body) {
        // 校验该 client 是否开放重置密码（sys_client.reset_pwd_enabled：1=开放 0=关闭，默认开放）
        if (StringUtils.isNotBlank(body.getClientId())) {
            try {
                RemoteClientVo clientVo = remoteClientService.queryByClientId(body.getClientId());
                if (clientVo != null && !"1".equals(clientVo.getResetPwdEnabled())) {
                    return R.fail("该客户端未开放重置密码功能");
                }
            } catch (Exception ignore) {
                // 客户端不存在/查询失败时按"未配置"放过
            }
        }
        // 校验短信验证码：失败不动密码
        String key = GlobalConstants.CAPTCHA_CODE_KEY + body.getPhonenumber();
        String cached = RedisUtils.getCacheObject(key);
        if (StringUtils.isBlank(cached)) {
            return R.fail("验证码已过期，请重新获取");
        }
        if (!cached.equals(body.getSmsCode())) {
            return R.fail("短信验证码错误");
        }
        // 定位用户
        LoginUser userInfo;
        try {
            userInfo = TenantHelper.dynamic(body.getTenantId(), () ->
                remoteUserService.getUserInfoByPhonenumber(body.getPhonenumber(), body.getTenantId()));
        } catch (ServiceException | UserException e) {
            log.warn("重置密码：查询用户失败 phone={}, err={}", body.getPhonenumber(), e.getMessage());
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("重置密码：查询用户失败 phone={}", body.getPhonenumber(), e);
            return R.fail("该手机号未注册或不可用");
        }
        // 弱密码校验
        if (remoteUserService.isSimplePassword(body.getNewPassword(), body.getTenantId())) {
            String reason = remoteUserService.getSimpleReason(body.getNewPassword(), body.getTenantId());
            return R.fail("重置密码失败：" + reason);
        }
        // 新旧密码相同校验
        if (BCrypt.checkpw(body.getNewPassword(), userInfo.getPassword())) {
            return R.fail("新密码不能与旧密码相同");
        }
        boolean updated = remoteUserService.resetUserPwd(userInfo.getUserId(), BCrypt.hashpw(body.getNewPassword()), body.getTenantId());
        if (!updated) {
            return R.fail("重置密码异常，请联系管理员");
        }
        // 重置成功后消费验证码，防止一码多用
        RedisUtils.deleteObject(key);
        return R.ok();
    }

    /**
     * 登录态修改密码：SDK 上报 loginId，服务端据此定位用户，校验旧密码后改密
     *
     * @param loginId sa-token 登录标识（格式 userType:userId）
     * @param body    新旧密码
     */
    public R<Void> updatePassword(String loginId, UpdatePasswordBody body) {
        Long userId = parseUserIdByLoginId(loginId);
        if (userId == null) {
            return R.fail("登录标识非法");
        }
        LoginUser userInfo;
        try {
            userInfo = TenantHelper.dynamic(body.getTenantId(), () ->
                remoteUserService.getUserInfo(userId, body.getTenantId()));
        } catch (ServiceException | UserException e) {
            log.warn("修改密码：查询用户失败 loginId={}, err={}", loginId, e.getMessage());
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("修改密码：查询用户失败 loginId={}", loginId, e);
            return R.fail("用户不存在或不可用");
        }
        if (!BCrypt.checkpw(body.getOldPassword(), userInfo.getPassword())) {
            return R.fail("修改密码失败，旧密码错误");
        }
        if (BCrypt.checkpw(body.getNewPassword(), userInfo.getPassword())) {
            return R.fail("新密码不能与旧密码相同");
        }
        if (remoteUserService.isSimplePassword(body.getNewPassword(), body.getTenantId())) {
            String reason = remoteUserService.getSimpleReason(body.getNewPassword(), body.getTenantId());
            return R.fail("修改密码失败：" + reason);
        }
        boolean updated = remoteUserService.resetUserPwd(userId, BCrypt.hashpw(body.getNewPassword()), body.getTenantId());
        return updated ? R.ok() : R.fail("修改密码异常，请联系管理员");
    }

    /**
     * 会话存活校验（供 refreshToken 总线调用）：校验 loginId 在认证中心仍处登录态（未被踢/禁用/过期）
     */
    public R<Void> checkSessionAlive(String loginId) {
        if (StringUtils.isBlank(loginId)) {
            return R.fail("登录标识不能为空");
        }
        List<String> tokens;
        try {
            tokens = StpUtil.getTokenValueListByLoginId(loginId);
        } catch (Exception e) {
            return R.fail("登录状态已失效，请重新登录");
        }
        if (tokens == null || tokens.isEmpty()) {
            return R.fail("登录状态已失效，请重新登录");
        }
        return R.ok();
    }

    /**
     * 从 sa-token loginId（格式 userType:userId）解析用户ID
     */
    private Long parseUserIdByLoginId(String loginId) {
        if (StringUtils.isBlank(loginId) || !loginId.contains(":")) {
            return null;
        }
        String userIdStr = loginId.substring(loginId.lastIndexOf(':') + 1);
        try {
            return Long.parseLong(userIdStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 手机号 + 短信验证码登录（免密码）
     *
     * <p>与 {@link #login} 的区别：不校验客户端的 grantType 是否包含 sms，
     * 直接复用 sms 策略完成"手机号 + 验证码"校验与 token 签发，
     * 因此无需在客户端"授权类型"中额外勾选。</p>
     *
     * @param body 登录信息（phonenumber / smsCode / tenantId / clientId）
     */
    @ApiEncrypt
    @PostMapping("/loginByPhone")
    public R<LoginVo> loginByPhone(@RequestBody String body) {
        PhoneLoginBody loginBody = JsonUtils.parseObject(body, PhoneLoginBody.class);
        ValidatorUtils.validate(loginBody);
        String clientId = loginBody.getClientId();
        RemoteClientVo clientVo = remoteClientService.queryByClientId(clientId);
        if (ObjectUtil.isNull(clientVo)) {
            log.info("客户端id: {} 不存在，手机号验证码登录失败.", clientId);
            return R.fail(MessageUtils.message("auth.grant.type.error"));
        }
        if (!SystemConstants.NORMAL.equals(clientVo.getStatus())) {
            return R.fail(MessageUtils.message("auth.grant.type.blocked"));
        }
        // 校验租户
        sysLoginService.checkTenant(loginBody.getTenantId());

        // 复用 sms 策略：构造标准短信登录体后走 IAuthStrategy
        SmsLoginBody smsBody = new SmsLoginBody();
        smsBody.setClientId(clientId);
        smsBody.setGrantType("sms");
        smsBody.setTenantId(loginBody.getTenantId());
        smsBody.setPhonenumber(loginBody.getPhonenumber());
        smsBody.setSmsCode(loginBody.getSmsCode());

        LoginVo loginVo = IAuthStrategy.login(JsonUtils.toJsonString(smsBody), clientVo, "sms");

        Long userId = LoginHelper.getUserId();
        scheduledExecutorService.schedule(() -> {
            remoteMessageService.publishMessage(List.of(userId), "欢迎登录统一认证中心");
        }, 5, TimeUnit.SECONDS);
        return R.ok(loginVo);
    }

}
