package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信小程序登录请求（字段与认证中心 {@code XcxLoginBody} 对齐）。
 *
 * <p>认证中心以 {@code IAuthStrategy} 按 {@code grantType} 分发登录策略，微信小程序对应
 * {@code grantType = "xcx"} 的 {@code XcxAuthStrategy}。因此本 BO 走<b>通用登录总线 type
 * {@code "login"}</b>（与账号密码登录同一个 handler），仅靠 {@link #grantType} 区分为小程序登录，
 * <b>无需认证中心单独注册 {@code loginByWechat} handler</b>。</p>
 *
 * <p>小程序端 {@code wx.login} 拿到临时凭证后放进 {@link #xcxCode}（注意字段名是 {@code xcxCode}，
 * 不是 {@code code}）；认证中心用 appid + xcxCode 调 code2session 换 openid，按 openid 匹配/绑定用户
 * 后签发一次性 ticket，SDK 侧以 ticket 桥接本地登录，返回子系统本地 token。</p>
 *
 * <p><b>服务端前置条件</b>：调用方 client 的授权类型（sys_client.grant_type）需包含 {@code xcx}，
 * 且 {@code XcxAuthStrategy} 需按环境补全 appid 对应的 appSecret 与 openid 用户查询实现。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudWechatLoginBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权类型：微信小程序固定为 {@code "xcx"}（认证中心据此路由到 XcxAuthStrategy） */
    private String grantType = "xcx";

    /** 小程序 wx.login 返回的临时登录凭证 code（对应服务端 XcxLoginBody.xcxCode，注意不是 code） */
    private String xcxCode;

    /** 小程序 appid：可不填，由认证中心按 client 配置的默认小程序凭证解析（对应服务端字段 appid） */
    private String appid;

    /** 租户 ID：可不填，SDK 默认 000000 */
    private String tenantId;

}
