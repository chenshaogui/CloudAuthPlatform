package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.util.Map;

/**
 * 认证中心开放接口透传请求（{@code POST /sso/openProxy}）。
 *
 * <p>SDK 所在项目根据<b>接口路径</b>直接调用认证中心对外开放的 REST 接口
 * （注册、重置密码、发送验证码等），无需认证中心在服务端消息总线注册 type handler，
 * 也无需 SDK 侧为每个接口新增方法。</p>
 *
 * <p><b>典型用法</b>（前端 JSON body）：</p>
 * <pre>{@code
 *   // 注册：POST JSON
 *   { "method": "POST", "path": "/api/auth/register",
 *     "body": { "username": "alice", "password": "***", "phone": "131xxxx" } }
 *
 *   // 校验短信验证码：POST form
 *   { "method": "POST", "path": "/api/auth/smsVerify",
 *     "params": { "phone": "131xxxx", "code": "123456" } }
 *
 *   // 图形验证码：GET query
 *   { "method": "GET", "path": "/api/auth/captcha", "params": { "key": "uuid-xxx" } }
 * }</pre>
 *
 * <p>字段语义与 {@code CloudPlatformHelper.callOpenApi(method, path, params, body)} 一致，
 * 实际 HTTP 请求由 SDK 拼上 {@code sa-token.sso-client.server-url} 基址发起。</p>
 *
 * @author sgchen
 * @since 1.3.1
 * @see io.gitee.chenshaogui.cloudauth.securitycloud.oauth.controller.CloudTokenSsoClientController#openProxy(io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudOpenApiBo)
 */
@Data
public class CloudOpenApiBo {

    /**
     * HTTP 方法：GET / POST / PUT / DELETE，大小写不敏感；为空默认 POST
     */
    private String method;

    /**
     * 认证中心接口路径，必须以 "/" 开头（相对 sa-token.sso-client.server-url），
     * 例如 /api/auth/register。出于 SSRF 防护，不允许传完整 http(s) URL。
     */
    private String path;

    /**
     * 查询 / 表单参数：GET、DELETE 时拼为 query string；POST、PUT 且未传 body 时
     * 以 application/x-www-form-urlencoded 提交。可为 null。
     */
    private Map<String, Object> params;

    /**
     * JSON 请求体：非 null 时以 application/json 提交（忽略 params）。可为 null。
     */
    private Object body;
}
