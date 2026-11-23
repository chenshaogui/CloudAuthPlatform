package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.controller;

import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.text.CharSequenceUtil;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.*;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sa-Token-SSO Client 端登录后访问的 Controller（默认实现，业务方可自行覆盖）
 *
 * <p>提供与统一鉴权平台对接的、必须登录后才能访问的客户端接口，包括：
 * <ul>
 *     <li>获取当前登录用户信息（含 user / permissions / roles）</li>
 *     <li>获取当前登录用户的路由信息</li>
 *     <li>清除 SDK 全部缓存（后台维护用）</li>
 * </ul>
 * </p>
 *
 * <p>类级别<b>不标注</b> {@code @SaIgnore}，由 SDK 全局鉴权过滤器对端点进行登录校验，
 * 仅在用户成功登录（已完成 {@code StpUtil.login}）后方能调用，未登录访问将返回 401。</p>
 *
 * <p>业务方若需要自定义返回结构（例如包装成项目内部的 {@code R<T>}）或扩展 Swagger 注解，
 * 可在自身工程中定义同名 Controller，Spring 会优先加载业务方的 Bean。</p>
 *
 * <p><b>多模块注册开关</b>：本类标注
 * {@code @ConditionalOnProperty(prefix = "sa-token", name = "expose-controller",
 * havingValue = "true", matchIfMissing = true)}，业务方可在不需要暴露登录后接口的
 * 微服务模块（如 infra / openapi / app）显式配置
 * {@code sa-token.expose-controller: false} 来关闭本 Controller 注册，
 * 避免多模块重复暴露同一组鉴权查询接口引发的"用户信息/路由 数据来源不一致"问题。
 * 微服务架构下推荐 <b>只在登录入口模块</b>（如 system）开启，其他模块关闭。</p>
 *
 * @author sgchen
 * @since 1.2.0 增加 expose-controller 开关（多模块微服务架构防护）
 */
@Slf4j
@RestController
@RequestMapping("/authCenter")
@ConditionalOnProperty(prefix = "sa-token", name = "expose-controller", havingValue = "true", matchIfMissing = true)
public class CloudAuthCenterController {

	/**
	 * 获取当前登录用户的路由信息（远程调用认证中心，业务低频使用）
	 *
	 * <p>前端登录后调用一次，自行缓存路由。</p>
	 *
	 * @return SaResult.data(routers)
	 */
	@GetMapping("/getMenuRouters")
	public SaResult getMenuRouters() {
		List<CloudRouterVo> routers = CloudPlatformHelper.getRouters();
		return SaResult.data(routers);
	}

	/**
	 * 获取当前登录用户信息（含 user / permissions / roles，远程调用认证中心）
	 *
	 * <p>前端登录后调用一次，自行缓存。</p>
	 *
	 * @return SaResult.data(userInfo)
	 */
	@GetMapping("/getLoginUserInfo")
	public SaResult getLoginUserInfo() {
		CloudUserInfoVo userInfo = CloudPlatformHelper.getUserInfo();
		return SaResult.data(userInfo);
	}

	/**
	 * 清除 SDK 全部缓存（后台维护用）
	 *
	 * <p>清空共享远程调用结果缓存（Redis 命名空间 {@code cloudauth:security-client-oauth:cache:*}），
	 * 使后续读取立即重新拉取认证中心最新数据，无需等待各档位 TTL 自然过期。</p>
	 *
	 * <p>典型场景：后台刚改完部门 / 字典 / 参数配置 / 区域 / 公告 / 文件，希望业务侧马上生效。</p>
	 *
	 * <p><b>不会清除 SaSession 中的登录用户快照，用户无需重新登录</b>；
	 * 清空后首次读取会触发一次远程调用（并发场景由单飞机制合并为 1 次）。</p>
	 *
	 * @return SaResult.data(实际删除的 Redis key 数量)
	 */
	@PostMapping("/clearCache")
	public SaResult clearCache() {
		long deleted = CloudPlatformHelper.clearAllCache();
		log.info("[cache] /authCenter/clearCache 执行完毕，删除 {} 个 key", deleted);
		return SaResult.data(deleted);
	}

	/**
	 * 获取鉴权中心 URL 配置（前端查询并跳转使用）
	 *
	 * <p>基础字段：{@code client}（当前客户端标识）、{@code serverUrl}（鉴权中心主机地址）、
	 * {@code authUrl}（鉴权中心登录完整 URL，已合并 serverUrl + authPath）。</p>
	 *
	 * <p>传入 {@code clientLoginUrl} 时额外返回 {@code ssoUrl}（可直接跳转的完整登录 URL）；
	 * {@code encrypt=true} 或全局开关 {@code sa-token.sso-encrypt-param=true} 时，
	 * 其 client / redirect 会收进 {@code ?auth_request=xxx} 密文，避免明文暴露。</p>
	 *
	 * <p><b>典型用法</b>：前端页面初始化时调用一次，缓存备用，需要跳转登录时直接用 {@code vo.authUrl}：</p>
	 * <pre>{@code
	 *   const vo = await api.get('/authCenter/getSsoUrl')
	 *   const clientLoginUrl = `${location.origin}/sso/doLoginByTicket`
	 *   window.location.href = `${vo.authUrl}?client=${vo.client}&clientLoginUrl=${encodeURIComponent(clientLoginUrl)}&back=${encodeURIComponent(location.pathname + location.search)}`
	 * }</pre>
	 *
	 * <p><b>注意</b>：本端点需登录后才能访问。如果业务方希望登录前就拿到鉴权中心 URL，
	 * 请使用 {@code GET /sso/getSsoAuthUrl}（已存在于 {@code CloudTokenSsoClientController}），
	 * 它会一次性拼好完整 URL；如果想登录前拿到原始 {@code client/serverUrl/authUrl} 三元组，
	 * 后续可在 {@code CloudTokenSsoClientController} 加一个 {@code @SaIgnore} 的同名端点直接复用本方法。</p>
	 *
	 * @return SaResult.data(CloudSsoUrlVo)
	 */
	@GetMapping("/getSsoUrl")
	public SaResult getSsoUrl(
			@RequestParam(value = "clientLoginUrl", required = false) String clientLoginUrl,
			@RequestParam(value = "back", required = false) String back,
			@RequestParam(value = "encrypt", required = false, defaultValue = "false") boolean encrypt) {
		CloudSsoUrlVo vo = CloudPlatformHelper.getSsoUrlInfo();
		// 传入 clientLoginUrl 时一并返回可直接跳转的完整 URL；
		// encrypt=true（或全局开关 sa-token.sso-encrypt-param=true）时参数为 auth_request 密文
		if (CharSequenceUtil.isNotBlank(clientLoginUrl)) {
			String url = encrypt
					? CloudPlatformHelper.getEncryptedSsoAuthUrl(clientLoginUrl, back)
					: CloudPlatformHelper.getSsoAuthUrl(clientLoginUrl, back);
			vo.setSsoUrl(url);
		}
		return SaResult.data(vo);
	}

	// ================== 区域编码（sys_area）1.3.0 新增 ==================

	/**
	 * 查询行政地区编码列表（按可选查询条件过滤，远程拉取认证中心 sys_area）
	 *
	 * <p>参考数据档（默认 300s TTL），适用于下拉框 / 级联选择等低频维护场景。
	 * 所有查询参数均可空（为空时不过滤）。</p>
	 *
	 * <p>典型用法：前端页面首次加载时调用一次，结果在前端缓存 5 分钟。</p>
	 *
	 * <p><b>入参方式</b>：{@code application/json}，通过 {@link RequestBody} 接收 {@link CloudAreaBo}。
	 * bo 字段全部可空，前端不传过滤条件时直接传 {@code {}}（空对象）。</p>
	 *
	 * @param bo 查询条件（可空；为空时自动按空对象处理）
	 * @return SaResult.data(areaList)
	 */
	@PostMapping("/listAreas")
	public SaResult listAreas(@RequestBody(required = false) CloudAreaBo bo) {
		if (bo == null) {
			bo = new CloudAreaBo();
		}
		List<CloudAreaVo> list = CloudPlatformHelper.getAreaList(bo);
		return SaResult.data(list);
	}

	/**
	 * 根据区域编码查询单个区域详情（远程拉取认证中心 sys_area，参考数据档 300s TTL）
	 *
	 * @param areaCode 区域编码（如 "110000"）
	 * @return SaResult.data(areaVo)
	 */
	@GetMapping("/getAreaByCode")
	public SaResult getAreaByCode(@RequestParam("areaCode") String areaCode) {
		if (CharSequenceUtil.isBlank(areaCode)) {
			return SaResult.error("areaCode 不能为空");
		}
		CloudAreaVo vo = CloudPlatformHelper.getAreaByCode(areaCode);
		return SaResult.data(vo);
	}

	// ================== 通知公告（sys_notice）1.3.0 新增 ==================

	/**
	 * 分页查询通知公告列表（远程拉取认证中心 sys_notice，参考数据档 300s TTL）
	 *
	 * <p>{@code pageNum} 默认 1，{@code pageSize} 默认 10。
	 * 返回 {@link CloudNoticePageVo}（含 {@code rows} / {@code total}）。</p>
	 *
	 * <p><b>入参方式</b>：{@code application/json}，通过 {@link RequestBody} 接收 {@link CloudNoticeBo}。
	 * bo 字段全部可空，前端不传过滤条件时直接传 {@code {}}（空对象）。
	 * 分页字段（{@code pageNum} / {@code pageSize}）为空时使用默认 1 / 10。</p>
	 *
	 * @param bo 查询条件（可空；为空时自动按空对象处理）
	 * @return SaResult.data(CloudNoticePageVo)
	 */
	@PostMapping("/listNotices")
	public SaResult listNotices(@RequestBody(required = false) CloudNoticeBo bo) {
		if (bo == null) {
			bo = new CloudNoticeBo();
		}
		CloudNoticePageVo page = CloudPlatformHelper.getNoticeList(bo);
		return SaResult.data(page);
	}

	/**
	 * 根据公告 ID 查询公告详情（远程拉取认证中心 sys_notice，参考数据档 300s TTL）
	 *
	 * @param noticeId 公告 ID
	 * @return SaResult.data(CloudNoticeVo)
	 */
	@GetMapping("/getNoticeById")
	public SaResult getNoticeById(@RequestParam("noticeId") Long noticeId) {
		if (noticeId == null) {
			return SaResult.error("noticeId 不能为空");
		}
		CloudNoticeVo vo = CloudPlatformHelper.getNoticeById(noticeId);
		return SaResult.data(vo);
	}

	// ================== v1.3.3 登录态账户操作（修改密码 / 续期） ==================

	/**
	 * 修改密码（<b>需登录</b>）：当前登录用户凭旧密码提交新密码完成改密，走总线调认证中心 {@code updatePassword}。
	 *
	 * <p>与免登的 {@code /sso/resetPassword}（忘记密码、短信码）不同：本接口由全局鉴权过滤器保证已登录，
	 * SDK 自动注入当前 {@code loginId}，前端只需传 {@code oldPassword}、{@code newPassword}。</p>
	 *
	 * @param body 修改密码参数
	 * @return SaResult
	 * @since 1.3.3
	 */
	@PostMapping("/updatePassword")
	public SaResult updatePassword(@RequestBody CloudUpdatePasswordBo body) {
		CloudPlatformHelper.updatePassword(body);
		return SaResult.ok();
	}

	/**
	 * 刷新令牌 / 续期（<b>需登录</b>）：走总线请认证中心校验当前会话后重新签发 ticket，再桥接本地登录刷新 token 时长。
	 *
	 * <p>返回新的 {@link CloudLoginVo}（含刷新后的本地 accessToken）。</p>
	 *
	 * @return SaResult.data(CloudLoginVo)
	 * @since 1.3.3
	 */
	@PostMapping("/refreshToken")
	public SaResult refreshToken() {
		CloudLoginVo loginVo = CloudPlatformHelper.refreshToken();
		return SaResult.data(loginVo);
	}

	// ================== v1.5.2 用户管理（新增用户 / 修改当前登录人信息） ==================

	/**
	 * 新增用户（<b>需登录</b>）：当前登录用户在其所属租户下创建新用户，走总线调认证中心 {@code addUser}。
	 *
	 * <p>支持用户名、密码、手机号、昵称、部门、邮箱、性别、状态、备注、角色编码等。
	 * SDK 自动注入当前操作人 {@code loginId}，服务端以操作人会话的租户/客户端为准。</p>
	 *
	 * <p><b>密码可留空</b>：为空时服务端使用默认密码；<b>角色编码 {@code roleKeys}</b> 为纯透传字段，
	 * 由接入系统自行填充，SDK 不做默认注入。</p>
	 *
	 * <p><b>用户已存在且有 {@code roleKeys}</b>：服务端不重复建号，只给既有用户追加绑定角色
	 * （不覆盖已有绑定），返回既有用户 {@code userId}；未传 {@code roleKeys} 时仍返回"账号已存在"错误。</p>
	 *
	 * @param body 新增用户参数
	 * @return SaResult.data(用户 userId，新建或已存在补绑角色)，需服务端回传；未回传时 data 为 null
	 * @since 1.5.2
	 */
	@PostMapping("/addUser")
	public SaResult addUser(@RequestBody CloudAddUserBo body) {
		if (body == null || CharSequenceUtil.isBlank(body.getUserName())) {
			return SaResult.error("用户账号不能为空");
		}
		Long userId = CloudPlatformHelper.addUser(body);
		return SaResult.data(userId);
	}

	/**
	 * 修改当前登录人信息（<b>需登录</b>）：修改本人资料字段，走总线调认证中心 {@code updateUser}。
	 *
	 * <p>SDK 自动注入当前 {@code loginId}，服务端只允许改本人。<b>按需更新</b>：
	 * 只改传入的字段，未传字段保持原值不动，不会被清空。</p>
	 *
	 * @param body 修改资料参数（昵称 / 手机号 / 邮箱 / 性别，均可选）
	 * @return SaResult
	 * @since 1.5.2
	 */
	@PostMapping("/updateUser")
	public SaResult updateUser(@RequestBody CloudUpdateUserBo body) {
		if (body == null) {
			return SaResult.error("参数不能为空");
		}
		CloudPlatformHelper.updateCurrentUser(body);
		return SaResult.ok();
	}

	/**
	 * 获取指定用户的角色列表（<b>需登录</b>）：按 userId 或 username 查询该用户绑定的启用角色。
	 *
	 * <p>走总线调认证中心 {@code getUserRoles}，返回 {@link CloudRoleDTO}（roleId / roleName / roleKey / dataScope）。
	 * userId 优先；两者均未传时返回错误。用户无角色或账号不存在时返回空列表。</p>
	 *
	 * @param userId   用户 ID（与 username 二选一）
	 * @param username 用户登录账号（与 userId 二选一）
	 * @return SaResult.data(List&lt;CloudRoleDTO&gt;)
	 * @since 1.5.5
	 */
	@GetMapping("/getRoles")
	public SaResult getRoles(@RequestParam(value = "userId", required = false) Long userId,
	                         @RequestParam(value = "username", required = false) String username) {
		if (userId == null && CharSequenceUtil.isBlank(username)) {
			return SaResult.error("userId 或 username 至少传一个");
		}
		List<CloudRoleDTO> roles = (userId != null)
			? CloudPlatformHelper.getRolesByUserId(userId)
			: CloudPlatformHelper.getRolesByUsername(username);
		return SaResult.data(roles);
	}

	/**
	 * 根据角色权限标识（roleKey）查询单个角色信息（<b>需登录</b>）。
	 *
	 * <p>走总线调认证中心 {@code getRoleByRoleKey}，返回 {@link CloudRoleDTO}。
	 * 角色不存在时 data 为 null。</p>
	 *
	 * @param roleKey 角色权限标识（如 admin 或 894bd7683c885153e24844750d59134d-admin）
	 * @return SaResult.data(CloudRoleDTO)
	 * @since 1.5.6
	 */
	@GetMapping("/getRoleByKey")
	public SaResult getRoleByKey(@RequestParam("roleKey") String roleKey) {
		if (CharSequenceUtil.isBlank(roleKey)) {
			return SaResult.error("roleKey 不能为空");
		}
		CloudRoleDTO role = CloudPlatformHelper.getRoleByRoleKey(roleKey);
		return SaResult.data(role);
	}

	/**
	 * 分页查询角色下绑定的用户列表（<b>需登录</b>）：按 roleId 或 roleKey 查询该角色关联的启用用户。
	 *
	 * <p>走总线调认证中心 {@code getRoleUserPage}，返回 {@link CloudUserPageVo}（含 {@code rows} / {@code total}）。
	 * roleId 优先；两者均未传时返回错误。角色不存在或无关联用户时返回空分页。</p>
	 *
	 * <p><b>入参方式</b>：{@code application/json}，通过 {@link RequestBody} 接收 {@link CloudRoleUserQueryBo}。
	 * 分页字段（{@code pageNum} / {@code pageSize}）为空时使用默认 1 / 10。</p>
	 *
	 * @param bo 查询条件（roleId/roleKey 二选一 + 分页参数）
	 * @return SaResult.data(CloudUserPageVo)
	 * @since 1.5.6
	 */
	@PostMapping("/getRoleUsers")
	public SaResult getRoleUsers(@RequestBody CloudRoleUserQueryBo bo) {
		if (bo == null || (bo.getRoleId() == null && CharSequenceUtil.isBlank(bo.getRoleKey()))) {
			return SaResult.error("roleId 或 roleKey 至少传一个");
		}
		CloudUserPageVo page = CloudPlatformHelper.getRoleUserPage(bo);
		return SaResult.data(page);
	}

	// ================== 参数配置（sys_config） ==================

	/**
	 * 查询全部参数配置（远程拉取认证中心 sys_config，结果按参考数据档缓存）
	 *
	 * <p>适用于前端一次性拉取全部参数后自行缓存的场景。</p>
	 *
	 * @return SaResult.data(List&lt;CloudConfigVo&gt;)
	 */
	@GetMapping("/getConfigAll")
	public SaResult getConfigAll() {
		List<CloudConfigVo> list = CloudPlatformHelper.getConfigAll();
		return SaResult.data(list);
	}

	/**
	 * 根据参数ID查询参数配置（远程拉取认证中心 sys_config，结果按参考数据档缓存）
	 *
	 * @param configId 参数配置主键 ID
	 * @return SaResult.data(CloudConfigVo)
	 */
	@GetMapping("/getConfigById")
	public SaResult getConfigById(@RequestParam("configId") Long configId) {
		if (configId == null) {
			return SaResult.error("configId 不能为空");
		}
		CloudConfigVo vo = CloudPlatformHelper.getConfigById(configId);
		return SaResult.data(vo);
	}

	/**
	 * 根据参数键名查询参数配置（远程拉取认证中心 sys_config，结果按参考数据档缓存）
	 *
	 * @param configKey 参数键名，如 sys.user.initPassword
	 * @return SaResult.data(CloudConfigVo)
	 */
	@GetMapping("/getConfigByKey")
	public SaResult getConfigByKey(@RequestParam("configKey") String configKey) {
		if (CharSequenceUtil.isBlank(configKey)) {
			return SaResult.error("configKey 不能为空");
		}
		CloudConfigVo vo = CloudPlatformHelper.getConfigByKey(configKey);
		return SaResult.data(vo);
	}

	/**
	 * 根据参数键名查询参数键值（远程拉取认证中心 sys_config，结果按参考数据档缓存）
	 *
	 * <p>只读取开关 / 阈值等单值场景可直接使用本接口，无需前端自行解析 {@link CloudConfigVo}。</p>
	 *
	 * @param configKey 参数键名，如 sys.account.registerUser
	 * @return SaResult.data(configValue 字符串)
	 */
	@GetMapping("/getConfigValueByKey")
	public SaResult getConfigValueByKey(@RequestParam("configKey") String configKey) {
		if (CharSequenceUtil.isBlank(configKey)) {
			return SaResult.error("configKey 不能为空");
		}
		String value = CloudPlatformHelper.getConfigValueByKey(configKey);
		return SaResult.data(value);
	}

	// ================== v1.3.1 通用透传代理 ==================

	/**
	 * 通用透传代理：将任意已登录业务接口一次接入，无需 SDK 侧新增 Controller 方法。
	 *
	 * <p>前端只需传递 <b>type</b>（认证中心 {@code SsoServerController} 已注册的 SaSsoMessage type）
	 * 与 <b>JSON body</b>，本端点会自动转发到认证中心消息总线，行为与 SDK 内部
	 * {@code CloudPlatformHelper.callApi(type, body)} 完全一致。</p>
	 *
	 * <p><b>自动处理链路</b>：</p>
	 * <ul>
	 *   <li>SDK 鉴权过滤器已确保本端点<b>登录后才能访问</b>（未登录直接 401）</li>
	 *   <li>{@code client / sign / X-Browser-Id} 由 SDK 自动注入，前端无需传递</li>
	 *   <li>认证中心返回的 {@code SaResult.code} 非 200 时，会抛对应异常（401/403/其他），
	 *       由 {@code CloudTokenAuthExceptionHandler} 统一转为 HTTP 响应</li>
	 * </ul>
	 *
	 * <p><b>典型使用</b>：</p>
	 * <pre>{@code
	 *   // 1) 发送短信验证码（服务端已注册 type="sendSmsCode"）
	 *   POST /authCenter/proxy/sendSmsCode
	 *   { "phone": "131xxxx" }
	 *
	 *   // 2) 查当前用户任务列表（服务端已注册 type="listMyTasks"）
	 *   POST /authCenter/proxy/listMyTasks
	 *   { "status": "PENDING", "pageNum": 1, "pageSize": 20 }
	 *
	 *   // 3) 无需任何参数
	 *   POST /authCenter/proxy/getUnreadNoticeCount
	 *   {}
	 * }</pre>
	 *
	 * <p><b>安全提示</b>：本端点不区分具体 type 的业务权限，服务端应为每个需鉴权的
	 * handler 内部自行调用 {@code @SaCheckPermission} / 手写权限判断，避免
	 * “拿到 token 就能调任何 type”。SDK 仅保证登录，不代替业务级鉴权。</p>
	 *
	 * @param type 认证中心已注册的消息 type（路径变量）
	 * @param body 业务参数 JSON（可空；为空时按空对象处理）
	 * @return SaResult.data(认证中心原始响应 data)
	 * @since 1.3.1
	 */
	@PostMapping("/proxy/{type}")
	public SaResult proxy(@PathVariable("type") String type,
	                       @RequestBody(required = false) Map<String, Object> body) {
		if (type == null || type.isEmpty()) {
			return SaResult.error("type 不能为空");
		}
		Object data = CloudPlatformHelper.callApi(type, body);
		return SaResult.data(data);
	}

}