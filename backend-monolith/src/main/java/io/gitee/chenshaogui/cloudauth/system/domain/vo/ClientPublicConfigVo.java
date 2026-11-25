package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户端公共配置（鉴权中心前端公开访问，无需登录）
 *
 * <p>由鉴权中心前端在进入 /ssologin、/login、/register、/resetPwd 时按 clientId 拉取，
 * 用于动态渲染登录/注册/重置密码页面（不同子系统客户端可指定不同模板与配置）。</p>
 *
 * <p>出于安全考虑，{@code clientSecret} 等敏感字段不出现在本 Vo 中。</p>
 *
 * @author CodeBuddy
 */
@Data
public class ClientPublicConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户端id
     */
    private String clientId;

    /**
     * 客户端key（子系统唯一标识，可作为路由参数与日志标识）
     */
    private String clientKey;

    /**
     * 客户端名称（菜单名/系统名）
     */
    private String clientName;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 设备类型（pc/android/...）
     */
    private String deviceType;

    /**
     * 登录/注册/重置密码页面布局配置（JSON 字符串，前端按 template 字段分发到对应模板渲染）
     * <p>结构示例：
     * <pre>{@code
     * {
     *   "template": "default|liaoyuntong",
     *   "login":    { "backgroundImage": "...", "primaryColor": "#547bd6", ... },
     *   "register": { "title":"用户注册", ... },
     *   "resetPwd": { ... }
     * }
     * }</pre>
     */
    private String layoutConfig;

    /**
     * 是否开放注册（0=关闭 1=开放）
     */
    private String registerEnabled;

    /**
     * 是否开放重置密码（0=关闭 1=开放）
     */
    private String resetPwdEnabled;
}
