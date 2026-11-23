package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.template;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.http.SaHttpTemplate;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.name.ParamName;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaFoxUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.http.HttpRequest;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.BrowserIdContext;

import java.util.HashMap;
import java.util.Map;

public class RemoteSaSsoClientTemplate extends SaSsoClientTemplate {

    /**
     * 自定义策略实例。
     * <p>
     * 注意：不能以同名字段遮蔽父类的 {@code strategy}（Java 字段访问是静态绑定的，
     * 父类方法内部读到的仍是父类自己声明的字段），因此这里改用不同名的字段，
     * 并在构造器中把它赋给父类的 {@code strategy}，保证父类的
     * checkTicket / pushMessage / getData 等出站请求全部走自定义策略。
     * </p>
     */
    public final RemoteSaSsoClientStrategy remoteStrategy = new RemoteSaSsoClientStrategy();

    public RemoteSaSsoClientTemplate() {
        // 用自定义策略替换父类默认实例：所有 SSO 出站请求自动注入 X-Browser-Id header
        this.strategy = remoteStrategy;
    }

    /**
     * 推送自定义业务消息到授权中心。
     * <p>
     * 与父类 SaSsoClientTemplate.pushMessageAsSaResult 行为一致，
     * 区别在于改用 POST form 提交并支持附加参数。
     * </p>
     */
    public SaResult pushMessageAsSaResult(SaSsoMessage message, Map<String,Object> params) {
        SaSsoClientConfig ssoConfig = this.getClientConfig();
        String pushUrl = ssoConfig.splicingPushUrl();
        if (!SaFoxUtil.isUrl(pushUrl)) {
            throw new SaSsoException("无效 push-url 地址：" + pushUrl).setCode(30023);
        }
        ParamName paramName = this.paramName;
        // SaSsoMessage 继承自 LinkedHashMap，可直接使用 containsKey
        if (!message.containsKey(paramName.client)) {
            message.set(paramName.client, this.getClient());
        }
        message.checkType();
        if (params != null) {
            message.putAll(params);
        }
        this.getSignTemplate().addSignParamsAndJoin(message);
        return remoteStrategy.requestAsSaResult(pushUrl, message);
    }

    /**
     * 仅用于单元测试或特殊场景：获取底层 HTTP 模板
     */
    public SaHttpTemplate getHttpTemplate() {
        return SaManager.getSaHttpTemplate();
    }

    // ================== v1.3.1 新增：通用调用出口 ==================

    /**
     * 通用：走 sa-token pushS 消息通道调用认证中心（要求已登录，SDK 自动注入 client + sign + X-Browser-Id）。
     *
     * <p>业务方新增一类接口时，只需一行：
     * <pre>{@code
     *   SaResult r = template.invoke("resetPassword",
     *           Collections.singletonMap("loginId", StpUtil.getLoginId()));
     * }</pre>
     * 无需在 SDK 里再新增一个 wrapper 方法。</p>
     *
     * @param apiType 服务端注册的消息 type（例如 "userInfo" / "getRouters" / "resetPassword"）
     * @param params  业务参数（可空），会与 client / sign 一起以 POST form 提交
     * @return 认证中心返回的原始 {@link SaResult}；状态码到异常的转换在 Helper 层统一处理
     */
    public SaResult invoke(String apiType, Map<String, Object> params) {
        if (SaFoxUtil.isEmpty(apiType)) {
            throw new SaSsoException("apiType 不能为空").setCode(30090);
        }
        SaSsoMessage message = new SaSsoMessage();
        message.setType(apiType);
        if (params != null) {
            params.forEach(message::set);
        }
        // 复用父类链路：自动补 client / sign / checkType；本 SDK 覆盖了 strategy 会带上 X-Browser-Id
        return pushMessageAsSaResult(message, null);
    }

    /**
     * 通用：直连认证中心开放接口（不要求登录态），基址为 {@code sa-token.sso-client.server-url}。
     *
     * <p>典型使用：注册、忘记密码、发送短信/邮件验证码、图形验证码等公开接口，
     * 服务端就是普通 REST Controller，业务方无需 SDK 侧新增任何方法。</p>
     *
     * <p>约定：
     * <ul>
     *   <li>{@code path} 以 "/" 开头（相对 server-url），也可直接给完整 http(s) URL</li>
     *   <li>{@code method} 支持 GET / POST / PUT / DELETE（大小写不敏感）</li>
     *   <li>{@code params}：GET/DELETE 走 query，POST/PUT 若无 jsonBody 则走 form-urlencoded</li>
     *   <li>{@code jsonBody}：非 null 时以 application/json 提交，忽略 params</li>
     *   <li>{@code extraHeaders}：业务自定义 header，SDK 内部会追加 X-Browser-Id</li>
     * </ul>
     * </p>
     *
     * @return 原始响应字符串（JSON），由调用方交给 {@code CloudPlatformHelper#callOpenApi} 解析
     */
    public String executeOpenApi(String method,
                                 String path,
                                 Map<String, ?> params,
                                 Object jsonBody,
                                 Map<String, String> extraHeaders) {
        String url = splicingServerUrl(path);
        String m = method == null ? "GET" : method.trim().toUpperCase();

        // 统一使用 hutool-http（pom 已显式声明 hutool-http 5.8.20），
        // 避免 Forest 1.6.x 在不同子版本里 setBody / addBody 的 API 差异。
        HttpRequest req;
        switch (m) {
            case "GET":    req = HttpRequest.get(url);    break;
            case "POST":   req = HttpRequest.post(url);   break;
            case "PUT":    req = HttpRequest.put(url);    break;
            case "DELETE": req = HttpRequest.delete(url); break;
            default:
                throw new SaSsoException("不支持的 HTTP 方法: " + method).setCode(30091);
        }

        // header：先加 SDK 内置的 X-Browser-Id，再加业务自定义
        req.header(BrowserIdContext.HEADER_NAME, BrowserIdContext.current());
        if (extraHeaders != null) {
            extraHeaders.forEach(req::header);
        }

        if (jsonBody != null) {
            // JSON 体：hutool 会自动设置 Content-Type（传入的第二个参数）
            req.body(SaManager.getSaJsonTemplate().objectToJson(jsonBody),
                    "application/json;charset=UTF-8");
        } else if (params != null && !params.isEmpty()) {
            // GET/DELETE 下 hutool 会拼成 query；POST/PUT 下默认 form-urlencoded
            Map<String, Object> form = new HashMap<>();
            params.forEach((k, v) -> form.put(k, v == null ? "" : String.valueOf(v)));
            req.form(form);
        }
        return req.execute().body();
    }

    /**
     * 拼接 {@code sa-token.sso-client.server-url} 与相对路径。
     * <p>{@code path} 已是绝对 URL 时原样返回；未配置 server-url 时抛 {@link SaSsoException}。</p>
     */
    public String splicingServerUrl(String path) {
        if (SaFoxUtil.isEmpty(path)) {
            throw new SaSsoException("开放接口 path 不能为空").setCode(30092);
        }
        if (SaFoxUtil.isUrl(path)) {
            return path;
        }
        String base = this.getClientConfig().getServerUrl();
        if (SaFoxUtil.isEmpty(base)) {
            throw new SaSsoException(
                    "未配置 sa-token.sso-client.server-url，无法调用开放接口：" + path).setCode(30093);
        }
        boolean baseSlash = base.endsWith("/");
        boolean pathSlash = path.startsWith("/");
        if (baseSlash && pathSlash)    return base + path.substring(1);
        if (!baseSlash && !pathSlash)  return base + "/" + path;
        return base + path;
    }

}