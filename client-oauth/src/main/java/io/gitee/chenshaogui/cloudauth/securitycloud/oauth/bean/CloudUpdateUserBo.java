package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改当前登录人信息请求（<b>登录态</b>下修改本人资料，字段与认证中心 {@code RemoteUserBo} 资料字段对齐）。
 *
 * <p>SDK 自动注入当前 {@code loginId}，服务端只允许修改<b>本人</b>资料，
 * {@code userId}/{@code tenantId} 一律以会话为准（忽略本 body 传入）。</p>
 *
 * <p><b>按需更新</b>：只改传入的字段，未传（{@code null}/空）的字段保持原值不动，不会被清空。</p>
 *
 * <p>本请求仅涉及资料字段，不含账号、角色、状态（防越权提权）。</p>
 *
 * @author sgchen
 * @since 1.5.2
 */
@Data
public class CloudUpdateUserBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户昵称 */
    private String nickName;

    /** 手机号（唯一） */
    private String phonenumber;

    /** 邮箱（唯一） */
    private String email;

    /** 用户性别（0男 1女 2未知） */
    private String sex;

    /** 租户 ID：一般无需传，服务端以操作人会话为准 */
    private String tenantId;

}
