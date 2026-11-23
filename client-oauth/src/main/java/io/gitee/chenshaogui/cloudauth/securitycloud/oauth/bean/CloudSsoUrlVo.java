package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 鉴权中心 URL 配置（前端跳转使用）
 *
 * <p>仅返回前端跳转登录 / 注销所需的最小信息集：
 * {@link #client}（当前客户端标识）、{@link #serverUrl}（鉴权中心主机地址）、
 * {@link #authUrl}（鉴权中心登录完整 URL）。</p>
 *
 * @author sgchen
 * @since 1.3.1
 */
@Data
public class CloudSsoUrlVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前客户端标识（对应 {@code sa-token.sso-client.client}）
     */
    private String client;

    /**
     * 鉴权中心主机地址（对应 {@code sa-token.sso-client.server-url}，不含 path）
     */
    private String serverUrl;

    /**
     * 鉴权中心登录完整 URL（已合并 serverUrl + authPath，跳转登录时直接使用）
     */
    private String authUrl;

    /**
     * 可直接跳转的完整登录 URL（已携带 client / redirect）。
     *
     * <p>仅当 /authCenter/getSsoUrl 传入 clientLoginUrl 时返回；
     * 开启 {@code sa-token.sso-encrypt-param} 或 {@code encrypt=true} 时，
     * 其参数会收进 {@code ?auth_request=xxx} 密文，避免明文暴露。</p>
     */
    private String ssoUrl;

}
