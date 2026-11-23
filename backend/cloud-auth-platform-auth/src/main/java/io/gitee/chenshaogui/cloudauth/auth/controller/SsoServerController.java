package io.gitee.chenshaogui.cloudauth.auth.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.util.SaFoxUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import io.gitee.chenshaogui.cloudauth.auth.domain.vo.LoginVo;
import io.gitee.chenshaogui.cloudauth.auth.form.RegisterBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsByPhoneBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsBySetBody;
import io.gitee.chenshaogui.cloudauth.auth.form.SendSmsForRegisterBody;
import io.gitee.chenshaogui.cloudauth.auth.form.ResetPasswordBody;
import io.gitee.chenshaogui.cloudauth.auth.form.UpdatePasswordBody;
import io.gitee.chenshaogui.cloudauth.auth.form.PasswordLoginBody;
import io.gitee.chenshaogui.cloudauth.auth.service.IAuthStrategy;
import io.gitee.chenshaogui.cloudauth.auth.service.SysLoginService;
import io.gitee.chenshaogui.cloudauth.auth.template.RemoteSsoServerProcessor;
import io.gitee.chenshaogui.cloudauth.auth.template.RemoteSsoServerUtil;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheConstants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaExpireException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.UserException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ObjectUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ServletUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ValidatorUtils;
import io.gitee.chenshaogui.cloudauth.common.json.utils.JsonUtils;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.PermissionChangeMarkUtils;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessStatus;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.log.enums.OperatorType;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.resource.api.RemoteFileService;
import io.gitee.chenshaogui.cloudauth.resource.api.domain.RemoteFile;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteAreaService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteClientService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteConfigService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteDeptService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteDictService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteLogService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteMenuService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteNoticeService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteRoleService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteUserService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.SysUserOnline;
import io.gitee.chenshaogui.cloudauth.system.api.domain.bo.RemoteOperLogBo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.bo.RemoteOperLogReportBo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.bo.RemoteUserBo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.*;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * sso登录接口层
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class SsoServerController {

    /**
     * 操作日志单批条数上限（防御性兜底，客户端默认每批 500 条）
     */
    private static final int MAX_OPER_LOG_BATCH = 1000;

    private final TokenController tokenController;

    private final CaptchaController captchaController;

    private final RemoteSsoServerProcessor remoteSsoServerProcessor;

    private final SysLoginService sysLoginService;

    @DubboReference
    private final RemoteMenuService remoteMenuService;

    @DubboReference
    private final RemoteUserService remoteUserService;

    @DubboReference
    private final RemoteRoleService remoteRoleService;

    @DubboReference
    private final RemoteLogService remoteLogService;

    @DubboReference
    private final RemoteDeptService remoteDeptService;

    @DubboReference
    private final RemoteClientService remoteClientService;

    @DubboReference
    private final RemoteDictService remoteDictService;

    @DubboReference
    private final RemoteConfigService remoteConfigService;

    // 1.3.0 新增：区域编码（sys_area）
    @DubboReference
    private final RemoteAreaService remoteAreaService;

    // 1.3.0 新增：通知公告（sys_notice）
    @DubboReference
    private final RemoteNoticeService remoteNoticeService;

    // 1.3.0 新增：文件存储（sys_oss，复用 resource 模块已有 RemoteFileService）
    @DubboReference
    private final RemoteFileService remoteFileService;


    /**
     * 返回当前是否已经登录
     */
    @RequestMapping("/sso/isLogin")
    public R<Boolean> isLogin() {
        return R.ok(LoginHelper.isLogin());
    }

    /**
     * 获取 redirectUrl
     */
    @RequestMapping("/sso/getRedirectUrl")
    public R<Object> getRedirectUrl(String client, String redirect) {
        // 未登录情况下，返回 code=401
        if(!LoginHelper.isLogin()) {
            return R.fail(401,"用户未登录");
        }
        // 如果重定向地址是空的或者不是http开头的，那么从菜单获取
        if(!StringUtils.hasText(redirect) || !redirect.startsWith("http")){
            RemoteClientVo remoteClientVo = remoteClientService.queryByClientId(client);
            Long menuId = remoteClientVo.getMenuId();
            if(menuId==null){
                return R.fail(500,"客户端未绑定菜单");
            }
            RemoteSysMenuVo menuInfoById = remoteMenuService.getMenuInfoById(menuId);
            if(menuInfoById==null){
                return R.fail(500,"客户端绑定菜单信息不存在");
            }
            redirect = menuInfoById.getPath();
            // 菜单 path 为应用内相对路由（如 /car-monitor）时，基于客户端 allow-url 白名单解析为完整地址，
            // 否则 checkRedirectUrl 会因“无效redirect”拒绝下发 ticket
            if(!StringUtils.hasText(redirect) || !redirect.startsWith("http")){
                String resolved = resolveRelativeRedirect(remoteClientVo.getAllowUrl(), redirect);
                if(resolved == null){
                    return R.fail(500,"应用菜单未配置完整跳转地址（需以 http/https 开头），且无法从客户端 allow-url 解析出站点根地址");
                }
                redirect = resolved;
            }
        }
        // 已登录情况下，构建 redirectUrl
        redirect = SaFoxUtil.decoderUrl(redirect);
        // 如何是非当前客户端登录，增加客户端登录功能
        if(!ObjectUtils.equals(client,LoginHelper.getClientId())){
            try {
                String aLoginToken = getLoginTokenByClient(client, StpUtil.getLoginIdAsString());
                String redirectUrl = RemoteSsoServerUtil.buildRedirectUrl(client, redirect, StpUtil.getLoginId(), aLoginToken);
                return R.ok("获取成功",redirectUrl);
            } catch (NotLoginException e) {
                // 仅在“该客户端下无有效会话”时补登录；其他异常（如 redirect 校验失败）不应触发重复登录
                RemoteClientVo remoteClientVo = remoteClientService.queryByClientId(client);
                SaLoginParameter model = new SaLoginParameter();
                model.setDeviceType(remoteClientVo.getDeviceType());
                model.setTimeout(remoteClientVo.getTimeout());
                model.setActiveTimeout(remoteClientVo.getActiveTimeout());
                model.setExtra(LoginHelper.CLIENT_KEY, remoteClientVo.getClientId());
                LoginHelper.login(Objects.requireNonNull(LoginHelper.getLoginUser()), model);
                String aLoginToken = getLoginTokenByClient(client, StpUtil.getLoginIdAsString());
                String redirectUrl = RemoteSsoServerUtil.buildRedirectUrl(client, redirect, StpUtil.getLoginId(), aLoginToken);
                return R.ok("获取成功",redirectUrl);
            }
        }
        String redirectUrl = RemoteSsoServerUtil.buildRedirectUrl(client, redirect, StpUtil.getLoginId(), StpUtil.getTokenValue());
        return R.ok("获取成功",redirectUrl);
    }

    /**
     * 退出登录
     * @return R
     */
    @RequestMapping("/sso/logout")
    public R<Void> logout() {
        remoteSsoServerProcessor.ssoSignout();
        return R.ok();
    }

    /**
     * SSO-Server端：手机号 + 短信验证码登录（免密码，建立认证中心 SSO 会话）
     *
     * <p>与 {@code /sso/doLogin}（账号密码）对称：请求体为加密 JSON，
     * 由 {@link TokenController#loginByPhone(String)} 校验手机号 + 短信验证码并签发 token。
     * 由于登录动作发生在本请求内，sa-token 会把会话写入响应，
     * 从而建立认证中心（SSO-Server）登录态，之后即可通过 {@code /sso/getRedirectUrl}
     * 携带 ticket 回跳子系统，实现跨系统免登。</p>
     *
     * <p>请求体：{@code {"phonenumber":"...","smsCode":"...","tenantId":"...","clientId":"..."}}</p>
     *
     * @return SaResult（data 为 LoginVo），结构与 /sso/doLogin 保持一致
     */
    @RequestMapping("/sso/doLoginByPhone")
    public Object ssoDoLoginByPhone() {
        String reqBody = ServletUtils.getBody(ServletUtils.getRequest());
        R<LoginVo> login = tokenController.loginByPhone(reqBody);
        // 与 /sso/doLogin 不同：失败时按原始状态码返回，避免前端拿到空 token 继续跳转
        if (login.getCode() != R.SUCCESS) {
            return SaResult.error(login.getMsg()).setCode(login.getCode());
        }
        return SaResult.ok().setData(login.getData());
    }

    /**
     * SSO-Server端：处理所有SSO相关请求
     * 		http://{host}:{port}/sso/auth			-- 单点登录授权地址
     * 		http://{host}:{port}/sso/doLogin		-- 账号密码登录接口，接受参数：name、pwd
     * 		http://{host}:{port}/sso/signout		-- 单点注销地址（isSlo=true时打开）
     */
    @RequestMapping("/sso/*")
    public Object ssoRequest() {
        return remoteSsoServerProcessor.dister();
    }

    // 配置SSO相关参数
    @Autowired
    private void configSso(SaSsoServerTemplate ssoServerTemplate) {

        // 配置：登录处理函数
        ssoServerTemplate.strategy.doLoginHandle = (name, pwd) -> {
            HttpServletRequest request = ServletUtils.getRequest();
            String reqBody = ServletUtils.getBody(request);
            R<LoginVo> login = tokenController.login(reqBody);
            return SaResult.ok().setData(login.getData());
        };

        // 添加消息处理器：userinfo (获取用户资料) （用于为 client 端开放拉取数据的接口）
        ssoServerTemplate.messageHolder.addHandle("userInfo", (ssoTemplate, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            String loginToken = getLoginTokenByClient(client, loginId);
            LoginUser loginUser = LoginHelper.getLoginUser(loginToken);
            RemoteUserInfoVo userInfoByLoginId = remoteUserService.getUserInfoByLoginToken(loginToken, loginUser.getTenantId());
            return SaResult.ok().setData(userInfoByLoginId);
        });

        ssoServerTemplate.messageHolder.addHandle("loginUser", (ssoTemplate, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            String loginToken = getLoginTokenByClient(client, loginId);
            LoginUser loginUser = LoginHelper.getLoginUser(loginToken);
            return SaResult.ok().setData(loginUser);
        });

        ssoServerTemplate.messageHolder.addHandle("getRouters", (ssoTemplate, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            String loginToken = getLoginTokenByClient(client, loginId);
            LoginUser loginUser = LoginHelper.getLoginUser(loginToken);
            if(loginUser == null){
                return SaResult.error("当前用户登录已过期");
            }
            List<RemoteRouterVo> remoteRouterVos = remoteMenuService.selectMenuRoutersByClient(loginUser.getUserId(), client);
            return SaResult.ok().setData(remoteRouterVos);
        });

        ssoServerTemplate.messageHolder.addHandle("isLogin", (ssoTemplate, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            String browserId = message.getString("browserId");
            try {
                String loginToken = getLoginTokenByClient(client, loginId, browserId);
                return SaResult.ok().setData(StringUtils.hasText(loginToken));
            } catch (NotLoginException e) {
                // 本浏览器无有效会话 → 归一为 false（不是异常冒泡成 500），SDK 据此本地登出
                return SaResult.ok().setData(false);
            }
        });

        ssoServerTemplate.messageHolder.addHandle("getUserInfoById", (ssoTemplate, message) -> {
            Long userId = messageId(message, "userId");
            List<RemoteUserVo> remoteUserVos = remoteUserService.selectListByIds(Collections.singletonList(userId));
            if(!remoteUserVos.isEmpty()){
                return SaResult.ok().setData(remoteUserVos.get(0));
            }
            return SaResult.ok();
        });

        // 【登录态】按 userId 或 username 查用户绑定的启用角色列表（二选一，userId 优先）
        ssoServerTemplate.messageHolder.addHandle("getUserRoles", (ssoTemplate, message) -> {
            Long userId = messageId(message, "userId");
            String username = message.getString("username");
            List<RemoteRoleVo> roles;
            if (userId != null) {
                roles = remoteRoleService.selectRolesByUserId(userId);
            } else if (StringUtils.hasText(username)) {
                // 多租户下同名需 tenantId 限定，SDK 消息已上报 tenantId
                roles = remoteRoleService.selectRolesByUsername(username, resolveTenantId(message));
            } else {
                return SaResult.error("userId 或 username 至少传一个");
            }
            return SaResult.ok().setData(roles);
        });

        // 【登录态】按 roleKey 查询单个角色信息（支持短标识自动补全 clientId）
        ssoServerTemplate.messageHolder.addHandle("getRoleByRoleKey", (ssoTemplate, message) -> {
            String roleKey = message.getString("roleKey");
            if (!StringUtils.hasText(roleKey)) {
                return SaResult.error("roleKey 不能为空");
            }
            RemoteRoleVo role = remoteRoleService.selectRoleByRoleKey(roleKey, resolveClientId(message), resolveTenantId(message));
            return SaResult.ok().setData(role);
        });

        // 【登录态】分页查询角色下绑定的用户列表（roleId 或 roleKey 二选一，roleId 优先）
        ssoServerTemplate.messageHolder.addHandle("getRoleUserPage", (ssoTemplate, message) -> {
            Long roleId = messageId(message, "roleId");
            String roleKey = message.getString("roleKey");
            if (roleId == null && !StringUtils.hasText(roleKey)) {
                return SaResult.error("roleId 或 roleKey 至少传一个");
            }
            int pageNum = Convert.toInt(message.get("pageNum"), 1);
            int pageSize = Convert.toInt(message.get("pageSize"), 10);
            Map<String, Object> page = remoteRoleService.selectRoleUserPage(
                roleId, roleKey, resolveClientId(message), resolveTenantId(message), pageNum, pageSize);
            return SaResult.ok().setData(page);
        });

        ssoServerTemplate.messageHolder.addHandle("getUserListByIds", (ssoTemplate, message) -> {
            String userIds = message.getString("userIds");
            if(!StringUtils.hasText(userIds)){
                return SaResult.error("查询参数不能为空");
            }
            String[] splitStr = StringUtils.split(userIds, ",");
            assert splitStr != null;
            List<Long> userIdList = Arrays.stream(splitStr)
                .filter(StringUtils::hasText)
                .map(Long::parseLong)
                .toList();
            List<RemoteUserVo> remoteUserVos = remoteUserService.selectListByIds(userIdList);
            return SaResult.ok().setData(remoteUserVos);
        });

        ssoServerTemplate.messageHolder.addHandle("getDeptNameByIds", (ssoTemplate, message) -> {
            String deptIds = message.getString("deptIds");
            if(!StringUtils.hasText(deptIds)){
                return SaResult.error("查询参数不能为空");
            }
            String deptNameByIds = remoteDeptService.selectDeptNameByIds(deptIds);
            return SaResult.ok().setData(deptNameByIds);
        });

        // 通过部门ID查询部门编码（与 getDeptNameByIds 对称；供客户端 SDK 按 id 拿编码，专用档位走参考数据档 300s TTL）
        ssoServerTemplate.messageHolder.addHandle("getDeptCodeByIds", (ssoTemplate, message) -> {
            String deptIds = message.getString("deptIds");
            if (!StringUtils.hasText(deptIds)) {
                return SaResult.error("查询参数不能为空");
            }
            String deptCodeByIds = remoteDeptService.selectDeptCodeByIds(deptIds);
            return SaResult.ok().setData(deptCodeByIds);
        });

        ssoServerTemplate.messageHolder.addHandle("syncUserInfo", (ssoTemplate, message) -> {
            String userInfo = JsonUtils.toJsonString(message);
            RemoteUserBo remoteUserBo = JsonUtils.parseObject(userInfo, RemoteUserBo.class);
            if(remoteUserBo==null){
                return SaResult.error("同步用户不能为空");
            }
            String client = message.getString("client");
            remoteUserBo.setClientId(client);
            // 同步到默认租户
            remoteUserBo.setTenantId(TenantConstants.DEFAULT_TENANT_ID);
            Long result = remoteUserService.registerUserInfo(remoteUserBo);
            return SaResult.ok().setData(result != null);
        });

        // 消息处理器：batchOperLog（客户端 SDK 的子系统操作日志批量上报）
        // 客户端已按"客户端+租户+用户+模块+操作"维度聚合（useCount 累加）并批量推送，
        // 服务端此处仅做兜底：单批条数上限 + 转换为 RemoteOperLogBo，复用已有
        // RemoteLogService.saveLog 异步链路写入 sys_oper_log 表（不新建表）。
        ssoServerTemplate.messageHolder.addHandle("batchOperLog", (ssoTemplate, message) -> {
            String logs = message.getString("logs");
            if (!StringUtils.hasText(logs)) {
                return SaResult.error("日志数据不能为空");
            }
            List<RemoteOperLogReportBo> logList = JsonUtils.parseArray(logs, RemoteOperLogReportBo.class);
            if (logList == null || logList.isEmpty()) {
                return SaResult.error("日志数据不能为空");
            }
            // 兜底限流：单批最多 1000 条，超出截断（客户端默认每批 500，此处防御性上限）
            if (logList.size() > MAX_OPER_LOG_BATCH) {
                log.warn("[batchOperLog] 单批条数超限: client={}, size={}, 已截断至 {}",
                    message.getString("client"), logList.size(), MAX_OPER_LOG_BATCH);
                logList = logList.subList(0, MAX_OPER_LOG_BATCH);
            }
            String client = message.getString("client");
            for (RemoteOperLogReportBo bo : logList) {
                // 客户端标识以消息签名为准（SDK 配置可伪造，来源不可信时以服务端识别为准）
                bo.setClientId(client);
                if (!StringUtils.hasText(bo.getTenantId())) {
                    bo.setTenantId(TenantConstants.DEFAULT_TENANT_ID);
                }
                // saveLog 为 @Async，逐条提交不阻塞消息处理线程（批量已由客户端聚合压缩）
                remoteLogService.saveLog(toOperLog(bo));
            }
            return SaResult.ok();
        });

        ssoServerTemplate.messageHolder.addHandle("getDeptChildListById", (ssoTemplate, message) -> {
            Long deptId = messageId(message, "deptId");
            List<RemoteDeptVo> remoteDeptVos = remoteDeptService.selectDeptsByBelongId(deptId);
            return SaResult.ok().setData(remoteDeptVos);
        });

        // ============== 字典信息查询（供客户端 SDK 拉取字典数据） ==============

        // 查询全部字典类型
        ssoServerTemplate.messageHolder.addHandle("getDictTypeAll", (ssoTemplate, message) -> {
            List<RemoteDictTypeVo> dictTypeVos = remoteDictService.selectDictTypeAll();
            return SaResult.ok().setData(dictTypeVos);
        });

        // 根据字典类型ID查询字典类型
        ssoServerTemplate.messageHolder.addHandle("getDictTypeById", (ssoTemplate, message) -> {
            Long dictId = messageId(message, "dictId");
            if (dictId == null) {
                return SaResult.error("查询参数【dictId】不能为空");
            }
            RemoteDictTypeVo dictTypeVo = remoteDictService.selectDictTypeById(dictId);
            return SaResult.ok().setData(dictTypeVo);
        });

        // 根据字典类型编码查询字典类型
        ssoServerTemplate.messageHolder.addHandle("getDictTypeByType", (ssoTemplate, message) -> {
            String dictType = message.getString("dictType");
            if (!StringUtils.hasText(dictType)) {
                return SaResult.error("查询参数【dictType】不能为空");
            }
            RemoteDictTypeVo dictTypeVo = remoteDictService.selectDictTypeByType(dictType);
            return SaResult.ok().setData(dictTypeVo);
        });

        // 根据字典数据ID查询字典数据
        ssoServerTemplate.messageHolder.addHandle("getDictDataById", (ssoTemplate, message) -> {
            Long dictCode = messageId(message, "dictCode");
            if (dictCode == null) {
                return SaResult.error("查询参数【dictCode】不能为空");
            }
            RemoteDictDataVo dictDataVo = remoteDictService.selectDictDataById(dictCode);
            return SaResult.ok().setData(dictDataVo);
        });

        // 根据字典类型查询字典数据集合
        ssoServerTemplate.messageHolder.addHandle("getDictDataByType", (ssoTemplate, message) -> {
            String dictType = message.getString("dictType");
            if (!StringUtils.hasText(dictType)) {
                return SaResult.error("查询参数【dictType】不能为空");
            }
            List<RemoteDictDataVo> dictDataVos = remoteDictService.selectDictDataByType(dictType);
            return SaResult.ok().setData(dictDataVos);
        });

        // ============== 参数配置查询（供客户端 SDK 拉取参数设置） ==============
        // 注意：sys_config 为租户隔离表，客户端需上报 tenantId；
        //       未上报时回退到默认租户（TenantConstants.DEFAULT_TENANT_ID）。

        // 查询全部参数配置
        ssoServerTemplate.messageHolder.addHandle("getConfigAll", (ssoTemplate, message) -> {
            List<RemoteConfigVo> configVos = remoteConfigService.selectConfigAll(resolveTenantId(message));
            return SaResult.ok().setData(configVos);
        });

        // 根据参数ID查询参数配置
        ssoServerTemplate.messageHolder.addHandle("getConfigById", (ssoTemplate, message) -> {
            Long configId = messageId(message, "configId");
            if (configId == null) {
                return SaResult.error("查询参数【configId】不能为空");
            }
            RemoteConfigVo configVo = remoteConfigService.selectConfigById(resolveTenantId(message), configId);
            return SaResult.ok().setData(configVo);
        });

        // 根据参数键名查询参数配置
        ssoServerTemplate.messageHolder.addHandle("getConfigByKey", (ssoTemplate, message) -> {
            String configKey = message.getString("configKey");
            if (!StringUtils.hasText(configKey)) {
                return SaResult.error("查询参数【configKey】不能为空");
            }
            RemoteConfigVo configVo = remoteConfigService.selectConfigInfoByKey(resolveTenantId(message), configKey);
            return SaResult.ok().setData(configVo);
        });

        // 根据参数键名查询参数键值
        ssoServerTemplate.messageHolder.addHandle("getConfigValueByKey", (ssoTemplate, message) -> {
            String configKey = message.getString("configKey");
            if (!StringUtils.hasText(configKey)) {
                return SaResult.error("查询参数【configKey】不能为空");
            }
            RemoteConfigVo configVo = remoteConfigService.selectConfigInfoByKey(resolveTenantId(message), configKey);
            return SaResult.ok().setData(configVo == null ? null : configVo.getConfigValue());
        });

        // ============== 1.3.0 区域编码（sys_area）查询 ==============
        // 客户端 SDK 通过 SSO push message 通道调用本处理器；
        // 全部参数均为可选（为空时不过滤），与客户端 CloudAreaBo 字段一一对应。

        // 区域列表查询
        ssoServerTemplate.messageHolder.addHandle("getAreaList", (ssoTemplate, message) -> {
            Long id = messageId(message, "id");
            String areaCode = message.getString("areaCode");
            String areaName = message.getString("areaName");
            Integer areaLevel = Convert.toInt(message.get("areaLevel"), null);
            String parentCode = message.getString("parentCode");
            String compartment = message.getString("compartment");
            List<RemoteAreaVo> areaVos = remoteAreaService.listArea(id, areaCode, areaName, areaLevel,
                    parentCode, compartment);
            return SaResult.ok().setData(areaVos);
        });

        // 根据区域编码查询单个区域
        ssoServerTemplate.messageHolder.addHandle("getAreaByCode", (ssoTemplate, message) -> {
            String areaCode = message.getString("areaCode");
            if (!StringUtils.hasText(areaCode)) {
                return SaResult.error("查询参数【areaCode】不能为空");
            }
            RemoteAreaVo areaVo = remoteAreaService.getByAreaCode(areaCode);
            return SaResult.ok().setData(areaVo);
        });

        // ============== 1.3.0 通知公告（sys_notice）查询 ==============
        // sys_notice 走 TenantHelper 隔离租户，客户端不必上报 tenantId（与服务端 sys_* 表一致）。

        // 公告分页查询
        ssoServerTemplate.messageHolder.addHandle("getNoticeList", (ssoTemplate, message) -> {
            Long noticeId = messageId(message, "noticeId");
            String noticeTitle = message.getString("noticeTitle");
            String noticeType = message.getString("noticeType");
            String status = message.getString("status");
            String createByName = message.getString("createByName");
            // SaSsoMessage 是 LinkedHashMap，没有 getInteger 便捷方法，这里手动转换为 Integer
            Integer pageNum = Convert.toInt(message.get("pageNum"), null);
            Integer pageSize = Convert.toInt(message.get("pageSize"), null);
            RemoteNoticePageVo page = remoteNoticeService.listNotice(noticeId, noticeTitle, noticeType, status,
                    createByName, pageNum, pageSize);
            return SaResult.ok().setData(page);
        });

        // 公告详情查询
        ssoServerTemplate.messageHolder.addHandle("getNoticeById", (ssoTemplate, message) -> {
            Long noticeId = messageId(message, "noticeId");
            RemoteNoticeVo noticeVo = remoteNoticeService.getByNoticeId(noticeId);
            return SaResult.ok().setData(noticeVo);
        });

        // ============== 1.3.0 文件存储（sys_oss）操作 ==============
        // 文件上传：客户端 SDK 通过 base64 字符串把字节内容推过来，
        // 由 auth 模块调用 resource 的 RemoteFileService 写入 OSS 并落 sys_oss 记录。
        // 注意：base64 走 form 表单，URL 安全字符会被 sa-token 编码，不影响解码。
        ssoServerTemplate.messageHolder.addHandle("uploadOss", (ssoTemplate, message) -> {
            String name = message.getString("name");
            String originalName = message.getString("originalName");
            String contentType = message.getString("contentType");
            String base64 = message.getString("base64");
            if (!StringUtils.hasText(originalName)) {
                return SaResult.error("上传文件原始文件名不能为空");
            }
            if (!StringUtils.hasText(base64)) {
                return SaResult.error("上传文件内容不能为空");
            }
            byte[] fileBytes;
            try {
                fileBytes = cn.hutool.core.codec.Base64.decode(base64);
            } catch (Exception e) {
                log.warn("[uploadOss] base64 解码失败: {}", e.getMessage());
                return SaResult.error("文件内容 base64 解码失败");
            }
            try {
                RemoteFile remoteFile = remoteFileService.upload(name, originalName, contentType, fileBytes);
                if (remoteFile == null) {
                    return SaResult.error("上传文件失败");
                }
                // 对齐客户端 CloudOssUploadVo：url / fileName / ossId
                Map<String, Object> upload = new HashMap<>(4);
                upload.put("url", remoteFile.getUrl());
                upload.put("fileName", remoteFile.getName() == null ? remoteFile.getOriginalName() : remoteFile.getName());
                upload.put("ossId", remoteFile.getOssId() == null ? null : String.valueOf(remoteFile.getOssId()));
                return SaResult.ok().setData(upload);
            } catch (Exception e) {
                log.warn("[uploadOss] 上传文件失败: {}", e.getMessage());
                return SaResult.error("上传文件失败: " + e.getMessage());
            }
        });

        // 文件 URL 查询（单 / 多个均走该通道，客户端按入参拆分）：
        // - 单 ID 时返回的 urls 取第一个；
        // - 多 ID 时按入参顺序返回，逗号分隔。
        ssoServerTemplate.messageHolder.addHandle("getOssUrlByIds", (ssoTemplate, message) -> {
            String ossIds = message.getString("ossIds");
            if (!StringUtils.hasText(ossIds)) {
                return SaResult.ok().setData("");
            }
            String urls = remoteFileService.selectUrlByIds(ossIds);
            return SaResult.ok().setData(urls == null ? "" : urls);
        });

        // 文件完整信息查询（按 ossId 串，返回 RemoteFile 列表）
        ssoServerTemplate.messageHolder.addHandle("getOssByIds", (ssoTemplate, message) -> {
            String ossIds = message.getString("ossIds");
            if (!StringUtils.hasText(ossIds)) {
                return SaResult.ok().setData(java.util.Collections.emptyList());
            }
            List<RemoteFile> files = remoteFileService.selectByIds(ossIds);
            return SaResult.ok().setData(files);
        });

        // ============== 权限同步：客户端检查是否有权限变更 ==============
        // 客户端 SDK 在请求链路中按节流周期调用本端点。
        // 语义：客户端上报"上次已处理的时间戳"，服务端返回最新的变更时间戳，由客户端判断是否刷新。
        //   - 服务端【不删除】标记，标记靠 TTL(120s) 自然过期，避免"刷新失败即丢通知"
        //   - 客户端按时间戳去重，全网广播不会引发 refresh 风暴
        //   - 标记不会被外部删除，无法通过伪造请求实施拒绝服务
        // 注意：userId 与 LoginUser.getLoginId()（userType:userId）不是同一个值，
        //       标记统一以 userId 为维度，因此客户端必须上报 userId。
        ssoServerTemplate.messageHolder.addHandle("checkPermission", (ssoTemplate, message) -> {
            Long userId = messageId(message, "userId");
            String tenantId = message.getString("tenantId");
            String loginId = message.getString("loginId");
            Long lastCheckTime = message.getLong("lastCheckTime");
            if (userId == null || !StringUtils.hasText(loginId)) {
                // 缺少 userId 时无法命中用户维度标记（兼容旧版 SDK），直接返回无变更
                log.warn("[permission-sync] checkPermission 参数不完整，loginId={}，请求将被忽略", loginId);
                return SaResult.ok().setData(buildCheckResult(false, 0L));
            }

            // 关键：校验服务端登录态。
            // 用户被禁用 / 删除 / 强退后，其 token 已在服务端失效，但客户端本地 Redis 中仍持有 token，
            // 仅返回"无变更"会让客户端继续放行。因此此处必须返回 401，
            // SDK 收到后会执行本地登出并引导重新登录。
            String client = message.getString("client");
            String browserId = message.getString("browserId");
            try {
                getLoginTokenByClient(client, loginId, browserId);
            } catch (Exception e) {
                log.info("[permission-sync] userId={} 本浏览器服务端登录态已失效({})，通知客户端登出", userId, e.getMessage());
                return SaResult.get(HttpServletResponse.SC_UNAUTHORIZED, "登录状态已失效，请重新登录", null);
            }

            try {
                long changeTime = PermissionChangeMarkUtils.resolveChangeTime(userId, tenantId);
                boolean changed = changeTime > 0L && changeTime > (lastCheckTime == null ? 0L : lastCheckTime);
                if (changed) {
                    log.debug("[permission-sync] 客户端 userId={} 检测到权限变更，changeTime={}", userId, changeTime);
                }
                return SaResult.ok().setData(buildCheckResult(changed, changeTime));
            } catch (Exception e) {
                // Redis 异常不应阻塞业务请求，降级为"无变更"
                log.warn("[permission-sync] checkPermission 查询失败: {}", e.getMessage());
                return SaResult.ok().setData(buildCheckResult(false, 0L));
            }
        });

        // 子系统发起的"同浏览器登出"：SDK 的 ssoLogout() 走 pushS 总线调用本 handler。
        // 只登出该 loginId 下、与上报 browserId 相同的认证中心 token（同浏览器），不广播到其它浏览器。
        // 其它子系统无需服务端主动推送——它们在下一次 checkPermission/isLogin 轮询中感知 401 并本地登出。
        ssoServerTemplate.messageHolder.addHandle("remoteLogout", (ssoTemplate, message) -> {
            String loginId = message.getString("loginId");
            String browserId = message.getString("browserId");
            if (!StringUtils.hasText(loginId)) {
                return SaResult.error("loginId 不能为空");
            }
            // browserId 为空：不做任何广播（避免退化成 loginId 全端登出）；SDK 本地仍会登出
            sysLoginService.logoutByBrowser(loginId, browserId);
            return SaResult.ok();
        });

        // ============== 客户端登录前辅助查询（不需要登录态） ==============
        // 客户端 SDK 的 /sso/getSsoAuthUrl 在调用方未传 clientLoginUrl 时，会通过 sa-token SSO
        // push message 通道（type=getDefaultLoginUrl）调用本处理器：按 clientId 查绑定菜单，
        // 取 menu.path 作为默认登录地址返回。无需登录态，登录前即可调用。
        ssoServerTemplate.messageHolder.addHandle("getDefaultLoginUrl", (ssoTemplate, message) -> {
            String client = message.getString("client");
            if (!StringUtils.hasText(client)) {
                return SaResult.error("客户端标识不能为空");
            }
            RemoteClientVo remoteClientVo = remoteClientService.queryByClientId(client);
            Long menuId = remoteClientVo.getMenuId();
            if (menuId == null) {
                return SaResult.error("客户端未绑定菜单");
            }
            RemoteSysMenuVo menuInfoById = remoteMenuService.getMenuInfoById(menuId);
            if (menuInfoById == null) {
                return SaResult.error("客户端绑定菜单信息不存在");
            }
            return SaResult.ok().setData(menuInfoById.getPath());
        });


    }

    // 配置SSO：认证类免登消息处理器（登录/注册/验证码短信/图形验证码），供子系统 SDK 走 pushS 总线调用
    @Autowired
    public void configAuth(SaSsoServerTemplate ssoServerTemplate) {

        // 账号密码登录：进程内委托 tokenController.login（绕开 @ApiEncrypt，总线只验签不验登录态），
        // 成功后签发一次性 ticket 回传，SDK 侧用现成 doLoginByTicket 完成本地登录。
        ssoServerTemplate.messageHolder.addHandle("login", (tpl, message) -> {
            String client = message.getString("client");
            String body = message.getString("body");
            R<LoginVo> login = tokenController.login(body);
            if (login.getCode() != R.SUCCESS) {
                return SaResult.error(login.getMsg());
            }
            String ticket = RemoteSsoServerUtil.createTicketAndSave(client, StpUtil.getLoginId(), login.getData().getAccessToken());
            return SaResult.ok().setData(ticket);
        });

        // 账号密码登录（免图形验证码）：路由到服务端 PasswordNoCaptchaAuthStrategy 策略 bean，
        // “免码”是服务端路由决策而非 body 字段，客户端无法构造伪造；其余与 login 完全一致。
        // SDK 侧对应端点 POST /sso/loginByPasswordNoCaptcha（发同 type=loginByPasswordNoCaptcha）。
        ssoServerTemplate.messageHolder.addHandle("loginByPasswordNoCaptcha", (tpl, message) -> {
            String client = message.getString("client");
            String body = message.getString("body");
            PasswordLoginBody loginBody = JsonUtils.parseObject(body, PasswordLoginBody.class);
            ValidatorUtils.validate(loginBody);
            RemoteClientVo clientVo = remoteClientService.queryByClientId(loginBody.getClientId());
            if (ObjectUtil.isNull(clientVo) || !SystemConstants.NORMAL.equals(clientVo.getStatus())) {
                return SaResult.error("客户端不存在或已停用");
            }
            // 有意免图形码的专用策略，不要求 client.grant_type 配置 passwordNoCaptcha，
            // 避免存量客户端需逐条补配置才能调通（与 login 不同：login 由 tokenController 校验 grantType）
            LoginVo loginVo;
            try {
                sysLoginService.checkTenant(loginBody.getTenantId());
                loginVo = IAuthStrategy.login(body, clientVo, "passwordNoCaptcha");
            } catch (Exception e) {
                log.warn("免验证码密码登录失败 username={}: {}", loginBody.getUsername(), e.getMessage());
                return SaResult.error(e.getMessage());
            }
            String ticket = RemoteSsoServerUtil.createTicketAndSave(client, StpUtil.getLoginId(), loginVo.getAccessToken());
            return SaResult.ok().setData(ticket);
        });

        // 手机号 + 短信验证码登录：同样以 ticket 桥接
        ssoServerTemplate.messageHolder.addHandle("loginByPhone", (tpl, message) -> {
            String client = message.getString("client");
            String body = message.getString("body");
            R<LoginVo> login = tokenController.loginByPhone(body);
            if (login.getCode() != R.SUCCESS) {
                return SaResult.error(login.getMsg());
            }
            String ticket = RemoteSsoServerUtil.createTicketAndSave(client, StpUtil.getLoginId(), login.getData().getAccessToken());
            return SaResult.ok().setData(ticket);
        });

        // 用户注册
        ssoServerTemplate.messageHolder.addHandle("register", (tpl, message) -> {
            RegisterBody registerBody = JsonUtils.parseObject(message.getString("body"), RegisterBody.class);
            try {
                R<Void> r = tokenController.register(registerBody);
                return r.getCode() == R.SUCCESS ? SaResult.ok() : SaResult.error(r.getMsg());
            } catch (CaptchaException | CaptchaExpireException e) {
                // 统一明文返回，避免 i18n 把验证码异常解析成英文 "Captcha invalid"
                return SaResult.error("验证码无效");
            }
        });

        // 发送短信验证码（用户名+密码维度）
        ssoServerTemplate.messageHolder.addHandle("sendSmsCode", (tpl, message) -> {
            SendSmsBody body = JsonUtils.parseObject(message.getString("body"), SendSmsBody.class);
            R<Void> r = tokenController.sendSmsCode(body);
            return r.getCode() == R.SUCCESS ? SaResult.ok() : SaResult.error(r.getMsg());
        });

        // 发送短信验证码（仅手机号）
        ssoServerTemplate.messageHolder.addHandle("sendSmsCodeByPhone", (tpl, message) -> {
            SendSmsByPhoneBody body = JsonUtils.parseObject(message.getString("body"), SendSmsByPhoneBody.class);
            R<Void> r = tokenController.sendSmsCodeByPhone(body);
            return r.getCode() == R.SUCCESS ? SaResult.ok() : SaResult.error(r.getMsg());
        });

        // 发送短信验证码（重置密码）
        ssoServerTemplate.messageHolder.addHandle("sendSmsCodeByReset", (tpl, message) -> {
            SendSmsBySetBody body = JsonUtils.parseObject(message.getString("body"), SendSmsBySetBody.class);
            R<Void> r = tokenController.sendSmsCodeByReset(body);
            return r.getCode() == R.SUCCESS ? SaResult.ok() : SaResult.error(r.getMsg());
        });

        // 发送短信验证码（注册场景，面向尚未注册的手机号，服务端不校验号码存在性）
        ssoServerTemplate.messageHolder.addHandle("sendSmsCodeForRegister", (tpl, message) -> {
            SendSmsForRegisterBody body = JsonUtils.parseObject(message.getString("body"), SendSmsForRegisterBody.class);
            R<Void> r = tokenController.sendSmsCodeForRegister(body);
            return r.getCode() == R.SUCCESS ? SaResult.ok() : SaResult.error(r.getMsg());
        });

        // 忘记密码 / 重置密码：手机号 + 短信验证码 + 新密码
        ssoServerTemplate.messageHolder.addHandle("resetPassword", (tpl, message) -> {
            ResetPasswordBody body = JsonUtils.parseObject(message.getString("body"), ResetPasswordBody.class);
            R<Void> r = tokenController.resetPassword(body);
            return r.getCode() == R.SUCCESS ? SaResult.ok() : SaResult.error(r.getMsg());
        });

        // 【登录态】修改密码：总线不携带终端用户 token，SDK 已随消息上报 loginId，服务端据此定位用户
        ssoServerTemplate.messageHolder.addHandle("updatePassword", (tpl, message) -> {
            String loginId = message.getString("loginId");
            UpdatePasswordBody body = JsonUtils.parseObject(message.getString("body"), UpdatePasswordBody.class);
            R<Void> r = tokenController.updatePassword(loginId, body);
            return r.getCode() == R.SUCCESS ? SaResult.ok() : SaResult.error(r.getMsg());
        });

        // 【登录态】新增用户：操作人（当前登录用户）在其所属租户下创建新用户，支持用户名/密码/手机/角色编码等
        // 账号已存在且带 roleKeys 时退化为"给既有用户补绑角色"（不重复建号），返回既有 userId
        ssoServerTemplate.messageHolder.addHandle("addUser", (tpl, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            // 解析操作人身份：新增用户归属到操作人所在租户，防止跨租户越权创建
            String loginToken = getLoginTokenByClient(client, loginId);
            LoginUser operator = LoginHelper.getLoginUser(loginToken);
            if (operator == null) {
                return SaResult.error("操作人登录态已失效，请重新登录");
            }
            RemoteUserBo bo = JsonUtils.parseObject(message.getString("body"), RemoteUserBo.class);
            if (bo == null || !StringUtils.hasText(bo.getUserName())) {
                return SaResult.error("用户账号不能为空");
            }
            // 归属维度以操作人会话为准，忽略 body 传入的 tenantId/clientId
            bo.setTenantId(operator.getTenantId());
            bo.setClientId(client);
            try {
                // 用户已存在且本次带了角色参数：不重复建号，只追加绑定角色（保留已有绑定），返回既有 userId。
                // 未带 roleKeys 时不走此分支，保持原有 "账号已存在" 报错语义；未命中角色也按成功处理（服务端仅记日志）。
                if (bo.getRoleKeys() != null && !bo.getRoleKeys().isEmpty()) {
                    Long existingUserId = remoteUserService.bindRolesIfUserExists(bo);
                    if (existingUserId != null) {
                        return SaResult.ok().setData(existingUserId);
                    }
                }
                // 密码：非空先做强度校验（与注册/改密统一走 isSimplePassword）再加密；为空交由 registerUserInfo 走默认密码
                if (StringUtils.hasText(bo.getPassword())) {
                    if (remoteUserService.isSimplePassword(bo.getPassword(), operator.getTenantId())) {
                        return SaResult.error("新增用户失败：" + remoteUserService.getSimpleReason(bo.getPassword(), operator.getTenantId()));
                    }
                    bo.setPassword(cn.hutool.crypto.digest.BCrypt.hashpw(bo.getPassword()));
                } else {
                    bo.setPassword(null);
                }
                // 昵称为空默认取账号，保持与注册一致
                if (!StringUtils.hasText(bo.getNickName())) {
                    bo.setNickName(bo.getUserName());
                }
                // registerUserInfo 成功时返回新建用户 userId（失败抛异常，由下方 catch 转错误响应）；
                // 注意：不能用 bo.getUserId() 读取——该调用跨 Dubbo RPC 边界，参数回写无法回到调用方
                Long newUserId = remoteUserService.registerUserInfo(bo);
                return SaResult.ok().setData(newUserId);
            } catch (ServiceException | UserException e) {
                return SaResult.error(e.getMessage());
            }
        });

        // 【登录态】启用/停用其他用户：操作人在自身租户内修改目标用户状态，并写审计日志。
        // 无服务端权限字符校验（权限由客户端菜单自行控制）；仅禁止禁用超级管理员，保证可追溯。
        // 目标用户支持 userId 或 userName 二选一定位（仅传 userName 时在操作人租户内解析为 userId）。
        ssoServerTemplate.messageHolder.addHandle("updateUserStatus", (tpl, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            String loginToken;
            try {
                loginToken = getLoginTokenByClient(client, loginId);
            } catch (Exception e) {
                return SaResult.error("操作人登录态已失效，请重新登录");
            }
            LoginUser operator = LoginHelper.getLoginUser(loginToken);
            if (operator == null) {
                return SaResult.error("操作人登录态已失效，请重新登录");
            }
            RemoteUserBo bo = JsonUtils.parseObject(message.getString("body"), RemoteUserBo.class);
            if (bo == null || (bo.getUserId() == null && !StringUtils.hasText(bo.getUserName()))
                || !StringUtils.hasText(bo.getStatus())) {
                return SaResult.error("用户ID或账号与状态不能为空");
            }
            // 归属维度以操作人会话为准，忽略 body 传入的 tenantId/clientId
            bo.setTenantId(operator.getTenantId());
            bo.setClientId(client);
            // 目标用户：优先 userId；仅传 userName 时在操作人租户内解析为 userId（跨租户 / 不存在 / 已删除解析不到）
            if (bo.getUserId() == null && StringUtils.hasText(bo.getUserName())) {
                bo.setUserId(remoteUserService.selectUserIdByUserName(bo.getUserName(), bo.getTenantId()));
            }
            if (bo.getUserId() == null) {
                // 解析不到目标用户：targetUserId 恒为 null，审计仅记录失败原因
                writeUserStatusAudit(operator, client, null, bo.getStatus(), false, "用户不存在或无权限");
                return SaResult.error("用户不存在或无权限");
            }
            // RemoteUserBo.userName 带 @NotBlank 校验，Dubbo 调用前必须补齐，
            // 否则仅传 userId 的请求会被 ValidationFilter 拦截在方法体之外
            if (!StringUtils.hasText(bo.getUserName())) {
                String targetName = remoteUserService.selectUserNameById(bo.getUserId());
                if (!StringUtils.hasText(targetName)) {
                    writeUserStatusAudit(operator, client, bo.getUserId(), bo.getStatus(), false, "用户不存在或无权限");
                    return SaResult.error("用户不存在或无权限");
                }
                bo.setUserName(targetName);
            }
            boolean disable = SystemConstants.DISABLE.equals(bo.getStatus());
            if (disable && SystemConstants.SUPER_ADMIN_ID.equals(bo.getUserId())) {
                writeUserStatusAudit(operator, client, bo.getUserId(), bo.getStatus(), false, "禁止禁用超级管理员账号");
                return SaResult.error("禁止禁用超级管理员账号");
            }
            if (!disable && !SystemConstants.NORMAL.equals(bo.getStatus())) {
                writeUserStatusAudit(operator, client, bo.getUserId(), bo.getStatus(), false, "非法的账号状态");
                return SaResult.error("非法的账号状态");
            }
            Boolean ok = remoteUserService.updateUserStatus(bo);
            boolean success = Boolean.TRUE.equals(ok);
            writeUserStatusAudit(operator, client, bo.getUserId(), bo.getStatus(), success,
                success ? null : "用户不存在或无权限");
            return success ? SaResult.ok() : SaResult.error("用户不存在或无权限");
        });

        // 【登录态】删除用户（角色解绑 / 删用户）：操作人在自身租户内解除目标用户与某角色的绑定；
        // 若该用户仅绑定此一个角色，解除后无角色，则删除用户。无服务端权限字符校验（权限由客户端菜单自行控制）；
        // 仅禁止操作超级管理员，保证可追溯。目标用户支持 userId 或 userName 二选一定位。
        ssoServerTemplate.messageHolder.addHandle("deleteUser", (tpl, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            String loginToken;
            try {
                loginToken = getLoginTokenByClient(client, loginId);
            } catch (Exception e) {
                return SaResult.error("操作人登录态已失效，请重新登录");
            }
            LoginUser operator = LoginHelper.getLoginUser(loginToken);
            if (operator == null) {
                return SaResult.error("操作人登录态已失效，请重新登录");
            }
            RemoteUserBo bo = JsonUtils.parseObject(message.getString("body"), RemoteUserBo.class);
            if (bo == null || (bo.getUserId() == null && !StringUtils.hasText(bo.getUserName()))
                || bo.getRoleId() == null) {
                return SaResult.error("用户ID或账号、角色ID不能为空");
            }
            // 归属维度以操作人会话为准，忽略 body 传入的 tenantId/clientId
            bo.setTenantId(operator.getTenantId());
            bo.setClientId(client);
            // 目标用户：优先 userId；仅传 userName 时在操作人租户内解析为 userId（跨租户 / 不存在 / 已删除解析不到）
            if (bo.getUserId() == null && StringUtils.hasText(bo.getUserName())) {
                bo.setUserId(remoteUserService.selectUserIdByUserName(bo.getUserName(), bo.getTenantId()));
            }
            if (bo.getUserId() == null) {
                // 解析不到目标用户（userId/userName 跨租户或不存在）：targetUserId 恒为 null，审计仅记录失败原因
                writeUserDeleteAudit(operator, client, null, bo.getRoleId(), false, "用户不存在或无权限");
                return SaResult.error("用户不存在或无权限");
            }
            // RemoteUserBo.userName 带 @NotBlank 校验，Dubbo 调用前必须补齐，
            // 否则仅传 userId 的请求会被 ValidationFilter 拦截在方法体之外
            if (!StringUtils.hasText(bo.getUserName())) {
                String targetName = remoteUserService.selectUserNameById(bo.getUserId());
                if (!StringUtils.hasText(targetName)) {
                    writeUserDeleteAudit(operator, client, bo.getUserId(), bo.getRoleId(), false, "用户不存在或无权限");
                    return SaResult.error("用户不存在或无权限");
                }
                bo.setUserName(targetName);
            }
            if (SystemConstants.SUPER_ADMIN_ID.equals(bo.getUserId())) {
                writeUserDeleteAudit(operator, client, bo.getUserId(), bo.getRoleId(), false, "禁止操作超级管理员账号");
                return SaResult.error("禁止操作超级管理员账号");
            }
            Boolean ok;
            try {
                ok = remoteUserService.deleteUser(bo);
            } catch (ServiceException e) {
                writeUserDeleteAudit(operator, client, bo.getUserId(), bo.getRoleId(), false, e.getMessage());
                return SaResult.error(e.getMessage());
            }
            boolean success = Boolean.TRUE.equals(ok);
            writeUserDeleteAudit(operator, client, bo.getUserId(), bo.getRoleId(), success,
                success ? null : "操作失败");
            return success ? SaResult.ok() : SaResult.error("操作失败");
        });

        // 【登录态】修改当前登录人信息：userId/tenant 一律取会话，禁止改他人；仅放开资料字段
        ssoServerTemplate.messageHolder.addHandle("updateUser", (tpl, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            String loginToken = getLoginTokenByClient(client, loginId);
            LoginUser operator = LoginHelper.getLoginUser(loginToken);
            if (operator == null) {
                return SaResult.error("登录态已失效，请重新登录");
            }
            RemoteUserBo bo = JsonUtils.parseObject(message.getString("body"), RemoteUserBo.class);
            if (bo == null) {
                return SaResult.error("参数不能为空");
            }
            // 只允许改本人：身份维度以会话为准，忽略 body 传入
            bo.setUserId(operator.getUserId());
            bo.setTenantId(operator.getTenantId());
            // 资料接口不放开账号/角色/状态，防越权提权；
            // userName 用会话值回填以满足 RemoteUserBo 的 @NotBlank 校验（updateUserProfile 实际不使用该字段）
            bo.setUserName(operator.getUsername());
            bo.setRoleKeys(null);
            bo.setStatus(null);
            try {
                boolean ok = remoteUserService.updateUserProfile(bo);
                return ok ? SaResult.ok() : SaResult.error("修改失败");
            } catch (ServiceException e) {
                return SaResult.error(e.getMessage());
            }
        });

        // 【登录态】刷新令牌/续期：校验 loginId 会话仍有效后重新签发一次性 ticket，SDK 再桥接登录
        ssoServerTemplate.messageHolder.addHandle("refreshToken", (tpl, message) -> {
            String client = message.getString("client");
            String loginId = message.getString("loginId");
            // 服务端校验该 loginId 在认证中心仍处登录态（未被踢/未禁用），否则返回错误
            R<Void> check = tokenController.checkSessionAlive(loginId);
            if (check.getCode() != R.SUCCESS) {
                return SaResult.error(check.getMsg());
            }
            // 无认证中心本地 token 时传 null（createTicketAndSave 按 loginId 签票）
            String ticket = RemoteSsoServerUtil.createTicketAndSave(client, loginId, null);
            return SaResult.ok().setData(ticket);
        });

        // 图形验证码（不走 IP 限流的总线版本）
        ssoServerTemplate.messageHolder.addHandle("captcha", (tpl, message) ->
            SaResult.ok().setData(captchaController.getCodeForBus()));
    }

    /**
     * 解析消息中的租户id，未上报时回退到默认租户
     * <p>sys_config 为租户隔离表，服务端查询需显式指定租户维度，
     * 否则租户过滤会被跳过，可能取到其它租户的数据。</p>
     *
    /**
     * SDK 上报体 → 操作日志实体（落库 sys_oper_log）
     *
     * <p>字段与 {@link RemoteOperLogBo} 一一对应：clientId 落新列 client_id；
     * title 超长截断至 50（列标准长度）；businessType / operatorType / status
     * 未上报时取枚举默认值（{@link BusinessType#OTHER} /
     * {@link OperatorType#MANAGE} / {@link BusinessStatus#SUCCESS}）；
     * operTime 兜底当前时间。编码取值见
     * {@code io.gitee.chenshaogui.cloudauth.common.log.enums} 包下各枚举。</p>
     *
     * @param bo 客户端 SDK 上报的操作日志
     */
    private RemoteOperLogBo toOperLog(RemoteOperLogReportBo bo) {
        RemoteOperLogBo operLog = new RemoteOperLogBo();
        operLog.setTenantId(bo.getTenantId());
        operLog.setClientId(bo.getClientId());
        // sys_oper_log.title 标准长度 50，超长截断防 insert 失败
        operLog.setTitle(bo.getTitle() != null && bo.getTitle().length() > 50
            ? bo.getTitle().substring(0, 50) : bo.getTitle());
        operLog.setBusinessType(BusinessType.valueOfCode(bo.getBusinessType()).getCode());
        operLog.setMethod(bo.getMethod());
        operLog.setRequestMethod(bo.getRequestMethod());
        operLog.setOperatorType(OperatorType.valueOfCode(bo.getOperatorType()).getCode());
        operLog.setOperName(bo.getOperName());
        operLog.setDeptName(bo.getDeptName());
        operLog.setOperUrl(bo.getOperUrl());
        operLog.setOperIp(bo.getOperIp());
        operLog.setOperLocation(bo.getOperLocation());
        operLog.setOperParam(bo.getOperParam());
        operLog.setJsonResult(bo.getJsonResult());
        operLog.setStatus(bo.getStatus() == null ? BusinessStatus.SUCCESS.getCode() : bo.getStatus());
        operLog.setErrorMsg(bo.getErrorMsg());
        operLog.setOperTime(bo.getOperTime() == null ? new Date() : bo.getOperTime());
        operLog.setCostTime(bo.getCostTime());
        return operLog;
    }

    /**
     * 写“用户状态变更”审计日志（落 sys_oper_log），便于后期追溯谁在何时启停了哪个用户。
     *
     * @param operator     操作人
     * @param client       客户端ID
     * @param targetUserId 目标用户ID
     * @param status       目标状态（0正常 / 1停用）
     * @param success      是否成功
     * @param errorMsg     失败原因（成功传 null）
     */
    private void writeUserStatusAudit(LoginUser operator, String client, Long targetUserId,
                                      String status, boolean success, String errorMsg) {
        RemoteOperLogBo log = new RemoteOperLogBo();
        log.setTenantId(operator.getTenantId());
        log.setClientId(client);
        boolean disable = SystemConstants.DISABLE.equals(status);
        log.setTitle((disable ? "停用" : "启用") + "用户");
        log.setBusinessType(BusinessType.OTHER.getCode());
        log.setOperatorType(OperatorType.MANAGE.getCode());
        log.setOperName(operator.getUsername());
        log.setDeptName(operator.getDeptName());
        log.setOperIp(operator.getIpaddr());
        log.setOperLocation(operator.getLoginLocation());
        log.setMethod("updateUserStatus");
        log.setOperUrl("bus:updateUserStatus");
        log.setOperParam("{\"targetUserId\":" + targetUserId + ",\"status\":\"" + status + "\"}");
        log.setJsonResult(success ? "ok" : "fail");
        log.setStatus(success ? BusinessStatus.SUCCESS.getCode() : BusinessStatus.FAIL.getCode());
        log.setErrorMsg(errorMsg);
        log.setOperTime(new Date());
        remoteLogService.saveLog(log);
    }

    /**
     * 写“删除用户（角色解绑/删用户）”审计日志（落 sys_oper_log），便于后期追溯谁在何时对哪个用户做了何种删除操作。
     *
     * @param operator     操作人
     * @param client       客户端ID
     * @param targetUserId 目标用户ID
     * @param roleId       待解除的角色ID
     * @param success      是否成功
     * @param errorMsg     失败原因（成功传 null）
     */
    private void writeUserDeleteAudit(LoginUser operator, String client, Long targetUserId,
                                      Long roleId, boolean success, String errorMsg) {
        RemoteOperLogBo log = new RemoteOperLogBo();
        log.setTenantId(operator.getTenantId());
        log.setClientId(client);
        log.setTitle("删除用户（解绑角色/删用户）");
        log.setBusinessType(BusinessType.OTHER.getCode());
        log.setOperatorType(OperatorType.MANAGE.getCode());
        log.setOperName(operator.getUsername());
        log.setDeptName(operator.getDeptName());
        log.setOperIp(operator.getIpaddr());
        log.setOperLocation(operator.getLoginLocation());
        log.setMethod("deleteUser");
        log.setOperUrl("bus:deleteUser");
        log.setOperParam("{\"targetUserId\":" + targetUserId + ",\"roleId\":" + roleId + "}");
        log.setJsonResult(success ? "ok" : "fail");
        log.setStatus(success ? BusinessStatus.SUCCESS.getCode() : BusinessStatus.FAIL.getCode());
        log.setErrorMsg(errorMsg);
        log.setOperTime(new Date());
        remoteLogService.saveLog(log);
    }

    /**
     * @param message 客户端上推的 SSO 消息
     * @return 租户id（不为空）
     */
    private String resolveTenantId(SaSsoMessage message) {
        String tenantId = message.getString("tenantId");
        return StringUtils.hasText(tenantId) ? tenantId : TenantConstants.DEFAULT_TENANT_ID;
    }

    /**
     * 解析消息中的客户端 id（SDK 上报的 client 字段），用于 roleKey 短标识自动补全匹配。
     */
    private String resolveClientId(SaSsoMessage message) {
        return message.getString("client");
    }

    /**
     * 从 SSO 消息中读取业务主键（Long），把"未传 / 0"统一视作 null。
     *
     * <p><b>为什么不直接用 {@code message.getLong(key)}</b>：
     * sa-token 的 {@code SaSsoMessage#getLong(String)} 是基本类型 {@code long}，未传时返回 {@code 0L}，
     * 自动装箱为 {@code Long(0)} 后传给 Dubbo 接口会被当作"主键=0"的过滤条件。
     * 业务自增主键从 1 开始（雪花 / mybatis-plus 雪花 / 自增均不会产生 0），
     * 直接用 0 过滤会查不到任何数据。</p>
     *
     * <p>本方法显式把"未传"和"显式 0"统一转 null，下游 {@code RemoteXxxService} 中
     * {@code eq(ObjectUtil.isNotNull(id), ...)} 即会跳过该过滤条件，符合"业务不传主键=不过滤"的预期。</p>
     *
     * <p><b>不适用场景</b>："0 本身是合法值"的字段（如 {@code lastCheckTime}=0 表示"无变更"）
     * 继续用 {@code message.getLong(...)}，自行处理。</p>
     *
     * @param message SSO 消息
     * @param key     键名
     * @return 解析后的主键；未传或为 0 时返回 null
     */
    private static Long messageId(SaSsoMessage message, String key) {
        Object raw = message.get(key);
        if (raw == null) {
            return null;
        }
        Long v = Convert.toLong(raw, null);
        return v == null || v == 0L ? null : v;
    }

    /**
     * 构建 checkPermission 响应体
     *
     * @param changed    客户端是否需要刷新登录用户
     * @param changeTime 服务端记录的最新变更时间戳（毫秒），客户端应回存用于去重
     */
    private Map<String, Object> buildCheckResult(boolean changed, long changeTime) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("changed", changed);
        data.put("changeTime", changeTime);
        return data;
    }

    /**
     * 将应用内相对路由（如 /car-monitor）基于客户端 allow-url 白名单解析为完整地址。
     * <p>allow-url 形如 "https://car.xxx.com/*" 或 "https://car.xxx.com"（逗号分隔多项），
     * 取第一个以 http 开头的配置项，去掉通配符与结尾斜杠作为站点根地址后拼接相对路由。</p>
     *
     * @param allowUrl     客户端允许授权地址（sys_client.allow_url）
     * @param relativePath 应用内相对路由
     * @return 完整跳转地址；无法解析时返回 null
     */
    private String resolveRelativeRedirect(String allowUrl, String relativePath) {
        if (!StringUtils.hasText(allowUrl) || !StringUtils.hasText(relativePath)) {
            return null;
        }
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        for (String allow : allowUrl.replaceAll(" ", "").split(",")) {
            if (!StringUtils.hasText(allow)) {
                continue;
            }
            // 去掉通配符与结尾斜杠，得到站点根地址
            String base = allow.endsWith("*") ? allow.substring(0, allow.length() - 1) : allow;
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            if (!base.startsWith("http")) {
                continue;
            }
            return base + path;
        }
        return null;
    }

    /**
     * 根据 clientId 和 loginId 过滤 SysUserOnline，返回最后一个（无则返回 null）
     */
    private String getLoginTokenByClient(String clientId, String loginId) {
        return getLoginTokenByClient(clientId, loginId, null);
    }

    /**
     * 根据 clientId、loginId（可选 browserId）过滤 SysUserOnline，返回最后一个 token。
     *
     * @param browserId 浏览器维度 id：<b>非空</b>时只认"同一浏览器"签发的 token（token extra 的 browserId 相等）；
     *                  <b>为空</b>时不过滤（向后兼容旧 SDK / 无 browserId 的调用）。
     */
    private String getLoginTokenByClient(String clientId, String loginId, String browserId) {
        // 1. 入参校验：clientId/ loginId
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(loginId)) {
            throw new NotPermissionException("client、loginId不能为空");
        }

        // 2. 根据 loginId 获取该用户的所有 Token 列表
        List<String> tokenIds;
        try {
            tokenIds = StpUtil.getTokenValueListByLoginId(loginId);
        } catch (Exception e) {
            // 无登录态/Token 列表为空时，返回 null
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1", "未能读取到有效token或者token已过期", null).setCode(11011);
        }

        // 3. 过滤：有效 Token →（按 browserId 收敛）→ 转换为 SysUserOnline → 按 clientId 匹配 → 收集列表
        List<SysUserOnline> userOnlineList = tokenIds.stream()
            // 过滤：Token 未过期（-1 表示永不过期，>=0 表示剩余有效期）
            .filter(token -> StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) >= -1)
            // 仅当客户端上报了 browserId 时，才只认同一浏览器的 token
            .filter(token -> {
                if (!StringUtils.hasText(browserId)) {
                    return true;
                }
                Object tBid = StpUtil.getExtra(token, LoginHelper.BROWSER_ID_KEY);
                return browserId.equals(tBid == null ? null : String.valueOf(tBid));
            })
            .map(token -> {
                LoginUser loginUser = LoginHelper.getLoginUser(token);
                // token 可能仍在会话列表中，但其 LoginUser 缓存已被清除（如刚被踢下线），此时跳过该 token
                if(loginUser == null){
                    return null;
                }
                return TenantHelper.dynamic(loginUser.getTenantId(), () -> {
                    String redisKey = CacheConstants.ONLINE_TOKEN_KEY + token;
                    return (SysUserOnline) RedisUtils.getCacheObject(redisKey);
                });
            })
            .filter(Objects::nonNull)
            .filter(online -> clientId.equals(online.getClientKey()))
            .toList();

        // 4. 取最后一个元素：非空则返回其 Token，否则抛未登录异常
        if (!userOnlineList.isEmpty()) {
            SysUserOnline lastOnline = userOnlineList.get(userOnlineList.size() - 1);
            return lastOnline.getTokenId();
        }
        throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1", "未能读取到有效token或者token已过期", null).setCode(11011);
    }

}
