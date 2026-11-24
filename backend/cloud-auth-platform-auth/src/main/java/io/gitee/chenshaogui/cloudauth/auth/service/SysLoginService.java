package io.gitee.chenshaogui.cloudauth.auth.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.hutool.crypto.digest.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.lock.annotation.Lock4j;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthUser;
import org.apache.dubbo.config.annotation.DubboReference;
import io.gitee.chenshaogui.cloudauth.auth.form.RegisterBody;
import io.gitee.chenshaogui.cloudauth.auth.properties.CaptchaProperties;
import io.gitee.chenshaogui.cloudauth.auth.properties.UserPasswordProperties;
import io.gitee.chenshaogui.cloudauth.common.core.constant.*;
import io.gitee.chenshaogui.cloudauth.common.core.enums.LoginType;
import io.gitee.chenshaogui.cloudauth.common.core.enums.UserType;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaExpireException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.UserException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MessageUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.log.event.LogininforEvent;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.tenant.exception.TenantException;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteSocialService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteTenantService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteUserService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.bo.RemoteSocialBo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.bo.RemoteUserBo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteSocialVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteTenantVo;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * 登录校验方法
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class SysLoginService {

    @DubboReference
    private RemoteUserService remoteUserService;
    @DubboReference
    private RemoteTenantService remoteTenantService;
    @DubboReference
    private RemoteSocialService remoteSocialService;
    @DubboReference
    private RemoteConfigService remoteConfigService;

    @Autowired
    private UserPasswordProperties userPasswordProperties;
    @Autowired
    private CaptchaProperties captchaProperties;

    /**
     * 绑定第三方用户
     *
     * @param authUserData 授权响应实体
     */
    @Lock4j
    public void socialRegister(AuthUser authUserData) {
        String authId = authUserData.getSource() + authUserData.getUuid();
        // 第三方用户信息
        RemoteSocialBo bo = BeanUtil.toBean(authUserData, RemoteSocialBo.class);
        BeanUtil.copyProperties(authUserData.getToken(), bo);
        LoginUser loginUser = LoginHelper.getLoginUser();
        if(loginUser==null){
            throw new ServiceException("当前访问未登录!");
        }
        bo.setUserId(loginUser.getUserId());
        bo.setAuthId(authId);
        bo.setOpenId(authUserData.getUuid());
        bo.setUserName(authUserData.getUsername()==null?loginUser.getUsername():authUserData.getUsername());
        bo.setNickName(authUserData.getNickname()==null?loginUser.getNickname():authUserData.getNickname());
        List<RemoteSocialVo> checkList = remoteSocialService.selectByAuthId(authId);
        if (CollUtil.isNotEmpty(checkList)) {
            throw new ServiceException("此三方账号已经被绑定!");
        }
        // 查询是否已经绑定用户
        RemoteSocialBo params = new RemoteSocialBo();
        params.setUserId(loginUser.getUserId());
        params.setSource(bo.getSource());
        List<RemoteSocialVo> list = remoteSocialService.queryList(params);
        if (CollUtil.isEmpty(list)) {
            // 没有绑定用户, 新增用户信息
            remoteSocialService.insertByBo(bo);
        } else {
            // 更新用户信息
            bo.setId(list.get(0).getId());
            remoteSocialService.updateByBo(bo);
            // 如果要绑定的平台账号已经被绑定过了 是否抛异常自行决断
            // throw new ServiceException("此平台账号已经被绑定!");
        }
    }

    /**
     * 退出登录
     * <p>
     * 按 browserId 维度注销：杀掉同一浏览器所有 tab 的 token，不影响其他浏览器。
     * 老 token（部署前生成）没有 browserId extra，read 出 null，与任何新 browserId 都不匹配，
     * 不会被踢也不会误杀其他 session（迁移期内用户重新登录后老 token 自然过期）。
     * </p>
     */
    public void logout() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            if (ObjectUtil.isNull(loginUser)) {
                return;
            }
            if (TenantHelper.isEnable() && LoginHelper.isSuperAdmin()) {
                // 超级管理员 登出清除动态租户
                TenantHelper.clearDynamic();
            }
            recordLogininfor(loginUser.getTenantId(), loginUser.getUsername(), Constants.LOGOUT, MessageUtils.message("user.logout.success"));
        } catch (NotLoginException ignored) {
            return;
        }

        // 解析当前请求的 loginId 与 browserId
        Object loginId;
        String myBrowserId;
        try {
            loginId = StpUtil.getLoginIdDefaultNull();
            Object extraBid = StpUtil.getExtra(LoginHelper.BROWSER_ID_KEY);
            myBrowserId = extraBid == null ? null : String.valueOf(extraBid);
        } catch (Exception e) {
            return;
        }
        // 认证中心自身退出：历史 token 无 browserId 时，生成一个"只匹配它自己"的一次性 id，
        // 保证不会按 loginId 误广播到其它浏览器。
        if (myBrowserId == null || myBrowserId.isEmpty() || "null".equals(myBrowserId)) {
            myBrowserId = java.util.UUID.randomUUID().toString();
        }

        logoutByBrowser(loginId, myBrowserId);
    }

    /**
     * 按 browserId 维度登出：杀掉该 loginId 下、属于"同一浏览器"（browserId 相同）的所有 token，
     * 不影响该账号在其它浏览器/设备的会话。供认证中心自身退出（{@link #logout()}）与
     * 子系统发起的远程登出（总线 handler {@code remoteLogout}）复用。
     *
     * @param loginId   账号 id（sa-token loginId，形如 userType:userId）
     * @param browserId 浏览器维度 id；<b>为空时不做任何登出</b>（避免退化成 loginId 全端登出、误踢其它浏览器）。
     *                  子系统发起登出但无法提供 browserId 时，其本地登出即可，无需广播。
     */
    public void logoutByBrowser(Object loginId, String browserId) {
        if (loginId == null) {
            return;
        }
        // 关键：没有明确 browserId 绝不广播，防止退化成 loginId 全端登出
        if (browserId == null || browserId.isEmpty() || "null".equals(browserId)) {
            return;
        }
        List<String> tokens;
        try {
            tokens = StpUtil.getTokenValueListByLoginId(loginId);
        } catch (Exception e) {
            tokens = Collections.emptyList();
        }
        for (String token : tokens) {
            try {
                Object tBid = StpUtil.getExtra(token, LoginHelper.BROWSER_ID_KEY);
                String tBidStr = tBid == null ? null : String.valueOf(tBid);
                // 只踢完全同 browserId 的 token；老 token(extra 无 browserId) 不匹配，跳过
                if (browserId.equals(tBidStr)) {
                    StpUtil.logoutByTokenValue(token);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 注册
     */
    public void register(RegisterBody registerBody) {
        String tenantId = registerBody.getTenantId();
        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        // 校验用户类型是否存在
        String userType = UserType.getUserType(registerBody.getUserType()).getUserType();

        boolean captchaEnabled = captchaProperties.getEnabled();
        // 验证码开关
        if (captchaEnabled) {
            // 注册页短信验证码由 /resource/sms/code 按手机号发送，key = CAPTCHA_CODE_KEY + 手机号；
            // 未传手机号时回退 uuid（图片验证码场景）
            String captchaKey = StringUtils.isNotBlank(registerBody.getPhonenumber())
                ? registerBody.getPhonenumber()
                : registerBody.getUuid();
            validateCaptcha(tenantId, username, registerBody.getCode(), captchaKey);
        }

        // 注册用户信息
        RemoteUserBo remoteUserBo = new RemoteUserBo();
        remoteUserBo.setTenantId(tenantId);
        // 注册来源客户端ID（SDK 已自动兜底上报）：透传给服务端，用于
        // 1) sys_user_client_config 建立用户-客户端关系；2) 注册自动绑定角色时短标识补全为 {clientId}-{短标识}
        remoteUserBo.setClientId(registerBody.getClientId());
        remoteUserBo.setUserName(username);
        remoteUserBo.setNickName(username);
        // 密码选填：非空才做强度校验并加密；为空则保持 null，由 registerUserInfo 走默认密码 sys.user.initPassword
        // 强度校验统一走 remoteUserService.isSimplePassword（内部委托 PasswordStrengthChecker，按租户读取长度/弱口令/字符类型等规则），与改密/重置链路保持一致
        if (StringUtils.isNotBlank(password)) {
            if (remoteUserService.isSimplePassword(password, tenantId)) {
                throw new ServiceException("注册失败：" + remoteUserService.getSimpleReason(password, tenantId));
            }
            remoteUserBo.setPassword(BCrypt.hashpw(password));
        } else {
            remoteUserBo.setPassword(null);
        }
        remoteUserBo.setUserType(userType);
        // 手机号写入 sys_user.phonenumber（注册页验证码/找回密码均按手机号）
        remoteUserBo.setPhonenumber(registerBody.getPhonenumber());
        // 注册后自动绑定的角色标识透传（SDK 配置 sa-token.register-role-keys 上报，可空）
        remoteUserBo.setRoleKeys(registerBody.getRoleKeys());

        Long regUserId = remoteUserService.registerUserInfo(remoteUserBo);
        if (regUserId == null) {
            throw new UserException("user.register.error");
        }
        recordLogininfor(tenantId, username, Constants.REGISTER, MessageUtils.message("user.register.success"));
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code     验证码
     * @param uuid     唯一标识
     */
    public void validateCaptcha(String tenantId, String username, String code, String uuid) {
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + StringUtils.blankToDefault(uuid, "");
        String captcha = RedisUtils.getCacheObject(verifyKey);
        if (captcha == null) {
            recordLogininfor(tenantId, username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (!StringUtils.equalsIgnoreCase(code, captcha)) {
            recordLogininfor(tenantId, username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
        // 校验通过后再删除，避免输错一次即清空验证码导致后续误判为“已过期”
        RedisUtils.deleteObject(verifyKey);
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     * @return
     */
    public void recordLogininfor(String tenantId, String username, String status, String message) {
        // 封装对象
        LogininforEvent logininforEvent = new LogininforEvent();
        logininforEvent.setTenantId(tenantId);
        logininforEvent.setUsername(username);
        logininforEvent.setStatus(status);
        logininforEvent.setMessage(message);
        SpringUtils.context().publishEvent(logininforEvent);
    }

    /**
     * 登录校验
     */
    public void checkLogin(LoginType loginType, String tenantId, String username, Supplier<Boolean> supplier) {
        String errorKey = CacheConstants.PWD_ERR_CNT_KEY + username;
        String loginFail = Constants.LOGIN_FAIL;
        // 获取密码最大错误次数，优先从数据库查询
        Integer maxRetryCount = userPasswordProperties.getMaxRetryCount();
        String maxRetryCountTmp = remoteConfigService.selectConfigByKey(tenantId, "user.password.maxRetryCount");
        if(StringUtils.isNotBlank(maxRetryCountTmp)){
            maxRetryCount = Integer.parseInt(maxRetryCountTmp);
        }

        // 获取密码锁定时间（单位分钟），优先从数据库查询
        Integer lockTime = userPasswordProperties.getLockTime();
        String lockTimeStr = remoteConfigService.selectConfigByKey(tenantId, "user.password.lockTime");
        if(StringUtils.isNotBlank(lockTimeStr)){
            lockTime = Integer.parseInt(lockTimeStr);
        }

        // 获取用户登录错误次数，默认为0 (可自定义限制策略 例如: key + username + ip)
        int errorNumber = ObjectUtil.defaultIfNull(RedisUtils.getCacheObject(errorKey), 0);
        // 锁定时间内登录 则踢出
        if (errorNumber >= maxRetryCount) {
            recordLogininfor(tenantId, username, loginFail, MessageUtils.message(loginType.getRetryLimitExceed(), maxRetryCount, lockTime));
            throw new UserException(loginType.getRetryLimitExceed(), maxRetryCount, lockTime);
        }

        if (supplier.get()) {
            // 错误次数递增
            errorNumber++;
            RedisUtils.setCacheObject(errorKey, errorNumber, Duration.ofMinutes(lockTime));
            // 达到规定错误次数 则锁定登录
            if (errorNumber >= maxRetryCount) {
                recordLogininfor(tenantId, username, loginFail, MessageUtils.message(loginType.getRetryLimitExceed(), maxRetryCount, lockTime));
                throw new UserException(loginType.getRetryLimitExceed(), maxRetryCount, lockTime);
            } else {
                // 未达到规定错误次数
                recordLogininfor(tenantId, username, loginFail, MessageUtils.message(loginType.getRetryLimitCount(), errorNumber));
                throw new UserException(loginType.getRetryLimitCount(), errorNumber);
            }
        }

        // 登录成功 清空错误次数
        RedisUtils.deleteObject(errorKey);
    }

    /**
     * 校验租户
     *
     * @param tenantId 租户ID
     */
    public void checkTenant(String tenantId) {
        if (!TenantHelper.isEnable()) {
            return;
        }
        if (StringUtils.isBlank(tenantId)) {
            throw new TenantException("tenant.number.not.blank");
        }
        if (TenantConstants.DEFAULT_TENANT_ID.equals(tenantId)) {
            return;
        }
        RemoteTenantVo tenant = remoteTenantService.queryByTenantId(tenantId);
        if (ObjectUtil.isNull(tenant)) {
            log.info("登录租户：{} 不存在.", tenantId);
            throw new TenantException("tenant.not.exists");
        } else if (SystemConstants.DISABLE.equals(tenant.getStatus())) {
            log.info("登录租户：{} 已被停用.", tenantId);
            throw new TenantException("tenant.blocked");
        } else if (ObjectUtil.isNotNull(tenant.getExpireTime())
            && new Date().after(tenant.getExpireTime())) {
            log.info("登录租户：{} 已超过有效期.", tenantId);
            throw new TenantException("tenant.expired");
        }
    }
}
