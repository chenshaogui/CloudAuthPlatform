package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

/**
 * 登录验证信息
 *
 * <p><b>字段填充说明（ticket 桥接登录模型）</b>：SDK 通过消费认证中心签发的一次性 ticket
 * 完成本地登录，本地 token 即是子系统自身签发的 {@link #accessToken}。该模型下<b>不存在
 * OAuth 语义的 refreshToken / scope</b>，续期请调用 {@code POST /authCenter/refreshToken}
 * （以当前会话重新签票再登录），因此 {@link #refreshToken}、{@link #refreshExpireIn}、
 * {@link #scope} 为<b>预留字段，当前恒为 null</b>；{@link #openid} 仅在认证中心登录响应
 * 透传 openid 的场景（如微信小程序登录）才有值，普通账号/短信登录为 null。</p>
 *
 * @author Michelle.Chung
 */
@Data
public class CloudLoginVo {

    /**
     * 授权令牌（子系统本地 token，登录成功后由 SDK 填充）
     */
    private String accessToken;

    /**
     * 刷新令牌。预留字段：ticket 桥接模型下恒为 null，续期请改用 {@code /authCenter/refreshToken}
     */
    private String refreshToken;

    /**
     * 授权令牌 access_token 的有效期（登录成功后由 SDK 填充）
     */
    private Long expireIn;

    /**
     * 刷新令牌 refresh_token 的有效期。预留字段：ticket 桥接模型下恒为 null
     */
    private Long refreshExpireIn;

    /**
     * 应用id（登录成功后由 SDK 填充）
     */
    private String clientId;

    /**
     * 令牌权限。预留字段：ticket 桥接模型下恒为 null
     */
    private String scope;

    /**
     * 用户 openid。仅微信小程序等透传 openid 的登录场景有值，普通登录为 null
     */
    private String openid;

}
