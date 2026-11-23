package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.util.StrUtil;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudLoginBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudLoginNoCaptchaBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudLoginVo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudOpenApiBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudPhoneLoginBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudRegisterBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudRegisterByCaptchaBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudRegisterBySmsBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudResetPasswordBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudSendSmsBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudSendSmsByPhoneBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudSendSmsByResetBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudSendSmsForRegisterBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudWechatLoginBo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sa-Token-SSO Client 端 Controller（默认实现，业务方可自行覆盖）
 *
 * <p>提供与统一鉴权平台对接的客户端接口（登录前 + 注销相关），包括：
 * <ul>
 *     <li>SSO 认证地址获取</li>
 *     <li>ticket 消费 / 单点登录</li>
 *     <li>单点注销 / 单点注销回调</li>
 *     <li>消息推送接收</li>
 *     <li>当前应用独自注销</li>
 * </ul>
 * </p>
 *
 * <p>登录后才能调用的"获取用户信息""获取路由"接口请见
 * {@link CloudAuthCenterController}（{@code /authCenter}）。</p>
 *
 * <p>类级别标注 {@link SaIgnore}，避开 SDK 全局鉴权过滤器，避免登录前被拦截。</p>
 *
 * <p>业务方若需要自定义登录逻辑（例如自定义返回结构 / 登录前埋点），
 * 可在自身工程中定义同名 Controller，Spring 会优先加载业务方的 Bean。</p>
 *
 * <p><b>多模块注册开关</b>：本类标注
 * {@code @ConditionalOnProperty(prefix = "sa-token", name = "expose-controller",
 * havingValue = "true", matchIfMissing = true)}，业务方可在不需要暴露 SSO 登录接口的
 * 微服务模块（如 infra / openapi / app）显式配置
 * {@code sa-token.expose-controller: false} 来关闭本 Controller 注册，
 * 避免多模块重复暴露同一组登录接口引发的"SSO 入口错乱 / sa-token session 串号"问题。
 * 微服务架构下推荐 <b>只在登录入口模块</b>（如 system）开启，其他模块关闭。</p>
 *
 * @author sgchen
 * @since 1.2.0 增加 expose-controller 开关（多模块微服务架构防护）
 */
@SaIgnore
@Slf4j
@RestController
@RequestMapping("/sso")
@ConditionalOnProperty(prefix = "sa-token", name = "expose-controller", havingValue = "true", matchIfMissing = true)
public class CloudTokenSsoClientController {

	/**
	 * 返回 SSO 认证中心登录地址
	 *
	 * @param clientLoginUrl 客户端登录地址，授权中心登录后回跳该地址（票据投递地址）；
	 *                        传空时由 SDK 自动按 clientId 从服务端拉取默认地址
	 * @param back           客户端登录成功后再次回跳到哪（用户原本想去的业务页面），可空
	 * @return SaResult.data(serverAuthUrl)
	 */
	@GetMapping("/getSsoAuthUrl")
	public SaResult getSsoAuthUrl(
			@RequestParam(value = "clientLoginUrl", required = false) String clientLoginUrl,
			@RequestParam(value = "back", required = false) String back,
			@RequestParam(value = "encrypt", required = false, defaultValue = "false") boolean encrypt) {
		if (StrUtil.isBlank(clientLoginUrl)) {
			clientLoginUrl = CloudPlatformHelper.getDefaultLoginUrl();
		}
		// encrypt=true 强制加密；也可通过配置 sa-token.sso-encrypt-param=true 全局开启
		String serverAuthUrl = encrypt
				? CloudPlatformHelper.getEncryptedSsoAuthUrl(clientLoginUrl, back)
				: CloudPlatformHelper.getSsoAuthUrl(clientLoginUrl, back);
		log.info("Get OAuth Cloud Url: {}", serverAuthUrl);
		return SaResult.data(serverAuthUrl);
	}

	/**
	 * 返回 client / redirect 已加密为 {@code ?auth_request=xxx} 的 SSO 登录地址（免登录）。
	 *
	 * <p>与 {@link #getSsoAuthUrl} 参数一致，仅返回密文版 URL，便于业务方按需切换；
	 * 认证中心前端解密后按原有 client/redirect 逻辑处理，旧明文链接同样兼容。
	 * 本端点不带授权模式，认证中心保留原有"已登录"确认弹窗。</p>
	 */
	@GetMapping("/getEncryptedSsoAuthUrl")
	public SaResult getEncryptedSsoAuthUrl(
			@RequestParam(value = "clientLoginUrl", required = false) String clientLoginUrl,
			@RequestParam(value = "back", required = false) String back) {
		return buildEncryptedSsoAuthUrl(clientLoginUrl, back, null);
	}

	/**
	 * 返回"直接进入授权中心"的加密 SSO 登录地址（免登录）。
	 *
	 * <p>授权模式固定为 {@code center}，由本端点决定，不通过 URL 参数传输；
	 * 认证中心已登录时跳过确认弹窗直接进入授权中心，未登录时停留登录页。
	 * 模式值随其它参数一起收进 {@code auth_request} 密文，地址栏不出现明文。</p>
	 */
	@GetMapping("/getEncryptedSsoAuthUrlForCenter")
	public SaResult getEncryptedSsoAuthUrlForCenter(
			@RequestParam(value = "clientLoginUrl", required = false) String clientLoginUrl,
			@RequestParam(value = "back", required = false) String back) {
		return buildEncryptedSsoAuthUrl(clientLoginUrl, back, CloudPlatformHelper.AUTH_MODE_CENTER);
	}

	/**
	 * 返回"免确认直接授权"的加密 SSO 登录地址（免登录）。
	 *
	 * <p>授权模式固定为 {@code silent}，由本端点决定，不通过 URL 参数传输；
	 * 认证中心已登录时直接完成授权并回跳客户端（不弹确认框），未登录时停留登录页。
	 * 模式值随其它参数一起收进 {@code auth_request} 密文，地址栏不出现明文。</p>
	 */
	@GetMapping("/getEncryptedSsoAuthUrlForSilent")
	public SaResult getEncryptedSsoAuthUrlForSilent(
			@RequestParam(value = "clientLoginUrl", required = false) String clientLoginUrl,
			@RequestParam(value = "back", required = false) String back) {
		return buildEncryptedSsoAuthUrl(clientLoginUrl, back, CloudPlatformHelper.AUTH_MODE_SILENT);
	}

	/** 生成加密跳转 URL：空 clientLoginUrl 时取服务端默认地址；authMode 为空时保留原弹窗逻辑 */
	private SaResult buildEncryptedSsoAuthUrl(String clientLoginUrl, String back, String authMode) {
		if (StrUtil.isBlank(clientLoginUrl)) {
			clientLoginUrl = CloudPlatformHelper.getDefaultLoginUrl();
		}
		String serverAuthUrl = CloudPlatformHelper.getEncryptedSsoAuthUrl(clientLoginUrl, back, authMode);
		log.info("Get Encrypted OAuth Cloud Url(authMode={}): {}", authMode, serverAuthUrl);
		return SaResult.data(serverAuthUrl);
	}

	/**
	 * 根据 ticket 完成本地登录（消费 ticket + 自动 StpUtil.login）
	 * @param ticket 授权码
	 * @return SaResult.data(loginVo)
	 */
	@GetMapping("/doLoginByTicket")
	public SaResult doLoginByTicket(@RequestParam("ticket") String ticket) {
		CloudLoginVo loginVo = CloudPlatformHelper.doLoginByTicket(ticket);
		return SaResult.data(loginVo);
	}

	/**
	 * 登录地址（由 SDK 跳转到认证中心）
	 * @return obj
	 */
	@GetMapping("/login")
	public Object ssoLogin() {
		return CloudPlatformHelper.ssoLogin();
	}

	/**
	 * 单点注销地址（全局注销，通知认证中心踢下线其他已登录应用）
	 * @return obj
	 */
	@GetMapping("/logout")
	public Object ssoLogout() {
		return CloudPlatformHelper.ssoLogout();
	}

	/**
	 * 单点注销回调（认证中心回调客户端，必须实现否则 SSO 全局注销失败）
	 * @return obj
	 */
	@GetMapping("/logoutCall")
	public Object ssoLogoutCall() {
		return CloudPlatformHelper.ssoLogoutCall();
	}

	/**
	 * 接收消息推送（认证中心回调客户端，必须实现）
	 * @return obj
	 */
	@PostMapping("/pushC")
	public Object ssoPushC() {
		return CloudPlatformHelper.ssoPushC();
	}

	/**
	 * 当前应用独自注销（不退出其它应用）
	 * @return obj
	 */
	@GetMapping("/logoutByAlone")
	public Object logoutByAlone() {
		return CloudPlatformHelper.logoutByAlone();
	}

	/**
	 * 认证中心开放接口通用透传（按接口路径直连，<b>登录前后均可调用</b>）
	 *
	 * <p>SDK 所在项目无需为每个认证中心 REST 接口新增方法，也无需认证中心在
	 * 消息总线注册 type handler：前端传 <b>path + 参数</b>，本端点直接拼
	 * {@code sa-token.sso-client.server-url} 基址转发请求，响应原样回传。</p>
	 *
	 * <p><b>典型使用</b>（注册、重置密码等登录前接口）：</p>
	 * <pre>{@code
	 *   // 注册：POST application/json
	 *   POST /sso/openProxy
	 *   { "method": "POST", "path": "/api/auth/register",
	 *     "body": { "username": "alice", "password": "***", "phone": "131xxxx" } }
	 *
	 *   // 发送短信验证码：POST form
	 *   POST /sso/openProxy
	 *   { "method": "POST", "path": "/api/auth/smsCode",
	 *     "params": { "phone": "131xxxx" } }
	 *
	 *   // 图形验证码：GET query
	 *   POST /sso/openProxy
	 *   { "method": "GET", "path": "/api/auth/captcha", "params": { "key": "uuid-xxx" } }
	 * }</pre>
	 *
	 * <p><b>与 {@code /authCenter/proxy/{type}} 的分工</b>：本端点走直连 REST 路径，
	 * 适用于认证中心未注册消息 handler、或登录前无 token 的公开接口；
	 * {@code /authCenter/proxy/{type}} 走 sa-token 消息总线（自动 sign + token），
	 * 适用于登录后、服务端已注册同名 type handler 的业务接口。</p>
	 *
	 * <p><b>安全边界</b>：path 必须以 "/" 开头且不允许携带协议头与 ".."，
	 * 防止被利用透传到任意地址（SSRF）；具体接口的访问控制（防刷、验签、
	 * 权限）由认证中心对应接口自行保障，本端点只做登录态无关的路径转发。
	 * 若认证中心某些接口不应对外，请通过网关/路由层限制本端点可透传的路径前缀。</p>
	 *
	 * @param bo 透传请求（method / path / params / body，见 {@link CloudOpenApiBo}）
	 * @return 认证中心原始响应的 code / msg / data 透传（接口不存在、未匿名放行被 401 等
	 *         场景，前端可直接看到认证中心真实错误，不会被二次转换为本地登录失效提示）
	 * @since 1.3.1
	 */
	@PostMapping("/openProxy")
	public SaResult openProxy(@RequestBody CloudOpenApiBo bo) {
		if (bo == null || StrUtil.isBlank(bo.getPath())) {
			return SaResult.error("path 不能为空");
		}
		String path = bo.getPath().trim();
		// SSRF 防护：仅允许相对 server-url 的路径，拒绝协议头与目录穿越
		if (!path.startsWith("/") || path.contains("://") || path.contains("..")) {
			return SaResult.error("path 必须是以 / 开头的相对路径，且不允许包含协议头或 ..");
		}
		String method = StrUtil.isBlank(bo.getMethod()) ? "POST" : bo.getMethod();
		// 透传认证中心原始 code/msg/data，便于前端 / 排障时区分“认证中心拒绝”与“本地登录失效”
		return CloudPlatformHelper.callOpenApiAsSaResult(method, path, bo.getParams(), bo.getBody());
	}

	// ================== v1.3.3 认证中心免登接口（登录前，走 pushS 消息总线） ==================

	/**
	 * 账号密码登录（登录前）：通过 sa-token pushS 总线调用认证中心 {@code login}，
	 * 成功后以一次性 ticket 桥接本地登录，返回子系统本地 token。
	 *
	 * <p>与 {@code /sso/openProxy} 的区别：本端点走总线（自动 sign），能绕开认证中心
	 * {@code /login} 的 {@code @ApiEncrypt}，客户端无需 RSA 公钥。</p>
	 *
	 * <p>请求体（前端只传业务字段）：{@code username}、{@code password}，开启图形验证码时再加
	 * {@code code}、{@code uuid}；{@code clientId}、{@code grantType}、{@code tenantId} 由 SDK 自动兜底。</p>
	 *
	 * @param body 登录参数
	 * @return SaResult.data(CloudLoginVo)（含子系统本地 accessToken）
	 * @since 1.3.3
	 */
	@PostMapping("/loginByPassword")
	public SaResult loginByPassword(@RequestBody CloudLoginBo body) {
		CloudLoginVo loginVo = CloudPlatformHelper.loginByPassword(body);
		return SaResult.data(loginVo);
	}

	/**
	 * 账号密码登录——<b>免图形验证码</b>（登录前）：走服务端独立的 {@code loginByPasswordNoCaptcha} handler，明确不校验图形验证码。
	 *
	 * <p>是否跳过验证码由“命中哪个端点”决定，客户端无法用 body 篡改；因此 {@code /sso/loginByPassword}
	 * 的图形验证码不会被绕过。适用于子系统自有风控、无需图形码的密码登录。</p>
	 *
	 * <p>请求体（{@link io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudLoginNoCaptchaBo}，前端只传业务字段）：{@code username}、{@code password}（无需 {@code code}/{@code uuid}）；{@code clientId}、{@code grantType}、{@code tenantId} 由 SDK 自动兜底。</p>
	 *
	 * @param body 登录参数（免图形验证码专用实体，不含 code/uuid）
	 * @return SaResult.data(CloudLoginVo)（含子系统本地 accessToken）
	 * @since 1.3.4
	 */
	@PostMapping("/loginByPasswordNoCaptcha")
	public SaResult loginByPasswordNoCaptcha(@RequestBody CloudLoginNoCaptchaBo body) {
		CloudLoginVo loginVo = CloudPlatformHelper.loginByPasswordNoCaptcha(body);
		return SaResult.data(loginVo);
	}

	/**
	 * 手机号 + 短信验证码登录（登录前）：走总线调认证中心 {@code loginByPhone}，同样以 ticket 桥接本地登录。
	 *
	 * <p>请求体：{@code phonenumber}、{@code smsCode}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @return SaResult.data(CloudLoginVo)
	 * @since 1.3.3
	 */
	@PostMapping("/loginByPhone")
	public SaResult loginByPhone(@RequestBody CloudPhoneLoginBo body) {
		CloudLoginVo loginVo = CloudPlatformHelper.loginByPhone(body);
		return SaResult.data(loginVo);
	}

	/**
	 * 微信小程序登录（登录前）：小程序 {@code wx.login} 拿到临时 code 后走总线调认证中心通用 {@code login}（{@code grantType=xcx}），
	 * 认证中心用 xcxCode 换 openid 并匹配/绑定用户，成功后以一次性 ticket 桥接本地登录。
	 *
	 * <p><b>实现说明</b>：与 {@code /sso/loginByPassword} 复用同一个服务端 {@code login} handler（按 {@code grantType} 路由），
	 * 认证中心<b>无需为小程序单独注册 handler</b>。</p>
	 *
	 * <p>请求体：{@code xcxCode}（小程序临时凭证，字段名非 {@code code}；{@code appid} 可选；{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @return SaResult.data(CloudLoginVo)
	 * @since 1.3.3
	 */
	@PostMapping("/loginByWechat")
	public SaResult loginByWechat(@RequestBody CloudWechatLoginBo body) {
		CloudLoginVo loginVo = CloudPlatformHelper.loginByWechat(body);
		return SaResult.data(loginVo);
	}

	/**
	 * 用户注册（登录前）：走总线调认证中心 {@code register}。
	 *
	 * <p>请求体：{@code username}、{@code password}，
	 * 按注册策略可能还需 {@code phonenumber} 短信码或图形验证码（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/register")
	public SaResult register(@RequestBody CloudRegisterBo body) {
		CloudPlatformHelper.register(body);
		return SaResult.ok();
	}

	/**
	 * 用户注册——图形验证码场景（登录前）：走总线调认证中心 {@code register}，服务端按 {@code uuid} 校验 {@code code}（图形码）。
	 *
	 * <p>需先调 {@code /sso/captcha} 拿 {@code uuid} 与图片，用户输入 {@code code} 后一起提交。与 {@code /sso/register} 走同一个服务端 {@code register}，仅入参更收敛。</p>
	 *
	 * <p>请求体：{@code username}、{@code password}、{@code code}、{@code uuid}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/registerByCaptcha")
	public SaResult registerByCaptcha(@RequestBody CloudRegisterByCaptchaBo body) {
		CloudPlatformHelper.registerByCaptcha(body);
		return SaResult.ok();
	}

	/**
	 * 用户注册——手机号短信验证码场景（登录前）：走总线调认证中心 {@code register}，服务端按 {@code phonenumber} 校验 {@code code}（短信码）。
	 *
	 * <p>需先调 {@code /sso/sendSmsCodeForRegister}（面向未注册号码）收码，再把 6 位码放进 {@code code} 字段（短信码字段名是 {@code code}，非 {@code smsCode}；{@code uuid} 无需传）。与 {@code /sso/register} 走同一个服务端 {@code register}。</p>
	 *
	 * <p>请求体：{@code username}、{@code password}、{@code phonenumber}、{@code code}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/registerBySms")
	public SaResult registerBySms(@RequestBody CloudRegisterBySmsBo body) {
		CloudPlatformHelper.registerBySms(body);
		return SaResult.ok();
	}

	/**
	 * 发送短信验证码（用户名+密码维度，登录前）：走总线调认证中心 {@code sendSmsCode}。
	 *
	 * <p>请求体：{@code username}、{@code password}（{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/sendSmsCode")
	public SaResult sendSmsCode(@RequestBody CloudSendSmsBo body) {
		CloudPlatformHelper.sendSmsCode(body);
		return SaResult.ok();
	}

	/**
	 * 发送短信验证码（仅手机号，登录前，用于手机号登录）：走总线调认证中心 {@code sendSmsCodeByPhone}。
	 *
	 * <p>请求体：{@code phonenumber}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/sendSmsCodeByPhone")
	public SaResult sendSmsCodeByPhone(@RequestBody CloudSendSmsByPhoneBo body) {
		CloudPlatformHelper.sendSmsCodeByPhone(body);
		return SaResult.ok();
	}

	/**
	 * 发送短信验证码（注册场景，登录前）：走总线调认证中心 {@code sendSmsCodeForRegister}，面向<b>尚未注册</b>的手机号。
	 *
	 * <p>与 {@code /sso/sendSmsCodeByPhone} 的区别：后者要求号码已注册（登录用），本接口不校验存在性，配合 {@code /sso/register} 使用。</p>
	 *
	 * <p>请求体：{@code phonenumber}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/sendSmsCodeForRegister")
	public SaResult sendSmsCodeForRegister(@RequestBody CloudSendSmsForRegisterBo body) {
		CloudPlatformHelper.sendSmsCodeForRegister(body);
		return SaResult.ok();
	}

	/**
	 * 发送短信验证码（重置密码，登录前）：走总线调认证中心 {@code sendSmsCodeByReset}。
	 *
	 * <p>请求体：{@code username}、{@code oldPassword}、{@code newPassword}（{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/sendSmsCodeByReset")
	public SaResult sendSmsCodeByReset(@RequestBody CloudSendSmsByResetBo body) {
		CloudPlatformHelper.sendSmsCodeByReset(body);
		return SaResult.ok();
	}

	/**
	 * 忘记密码 / 重置密码（登录前）：走总线调认证中心 {@code resetPassword}，凭手机号 + 短信验证码提交新密码完成改密。
	 *
	 * <p>需先用 {@code /sso/sendSmsCodeByPhone} 用手机号收取短信码，再提交本接口。</p>
	 *
	 * <p>请求体：{@code phonenumber}、{@code smsCode}、{@code newPassword}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
	 *
	 * @since 1.3.3
	 */
	@PostMapping("/resetPassword")
	public SaResult resetPassword(@RequestBody CloudResetPasswordBo body) {
		CloudPlatformHelper.resetPassword(body);
		return SaResult.ok();
	}

	/**
	 * 获取图形验证码（登录前）：走总线调认证中心 {@code captcha}，返回 uuid + base64 图片。
	 *
	 * <p>前端把 {@code uuid} 连同用户输入的 {@code code} 一起放进登录 / 注册 body 回传即可。</p>
	 *
	 * @return SaResult.data(CloudCaptchaVo)
	 * @since 1.3.3
	 */
	@GetMapping("/captcha")
	public SaResult captcha() {
		return SaResult.data(CloudPlatformHelper.getCaptcha());
	}

}