package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新增用户请求（<b>登录态</b>下由当前用户创建新用户，字段与认证中心 {@code RemoteUserBo} 对齐）。
 *
 * <p>SDK 自动注入当前操作人 {@code loginId}，服务端以操作人会话的 {@code tenantId}/{@code clientId}
 * 为准（忽略本 body 中的归属字段），新增用户归属于操作人所在租户。</p>
 *
 * <p><b>密码</b>：可留空，为空时服务端使用默认密码（参数配置 {@code sys.user.initPassword}）；
 * 非空时服务端加密落库。</p>
 *
 * <p><b>角色编码 {@link #roleKeys}</b>：为纯透传字段，由接入 SDK 的业务系统自行填充，
 * SDK 不注入默认值、不做角色决策（区别于注册链路的 {@code sa-token.register-role-keys} 自动兜底）。</p>
 *
 * <p><b>账号已存在</b>：若传入了 {@link #roleKeys}，服务端不重复创建用户，只给既有用户追加绑定
 * 命中的角色（保留原有绑定，超级管理员角色禁止绑定），并返回既有用户 {@code userId}；
 * 未传角色编码时维持原有"账号已存在"报错。</p>
 *
 * @author sgchen
 * @since 1.5.2
 */
@Data
public class CloudAddUserBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户账号（登录名，必填） */
    private String userName;

    /** 用户密码：可留空，为空则用服务端默认密码 */
    private String password;

    /** 手机号（唯一） */
    private String phonenumber;

    /** 用户昵称：可留空，为空时服务端默认取账号 */
    private String nickName;

    /** 归属部门 ID */
    private Long deptId;

    /** 邮箱（唯一） */
    private String email;

    /** 用户性别（0男 1女 2未知） */
    private String sex;

    /** 账号状态（0正常 1停用），默认正常 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 角色编码列表（{@code sys_role.role_key}，可多个）。
     * <p>纯透传：由接入系统决定绑定哪些角色，SDK 不做默认注入；服务端超级管理员角色禁止经此通道绑定。</p>
     * <p>新建用户时按本列表绑定角色；账号已存在时服务端不重复建号，只追加绑定缺失的角色
     * （保留已有绑定），并返回既有用户 {@code userId}。</p>
     */
    private List<String> roleKeys;

    /** 租户 ID：一般无需传，服务端以操作人会话为准 */
    private String tenantId;

}
