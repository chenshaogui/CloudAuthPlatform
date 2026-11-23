package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 用户信息
 *
 * @author sgchen
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudLoginUser implements Serializable {

    /**
     * 登录id
     */
    private String loginId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 部门类别编码
     */
    private String deptCategory;

    /**
     * 部门名
     */
    private String deptName;

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 登录地点
     */
    private String loginLocation;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 菜单权限
     */
    private Set<String> menuPermission;

    /**
     * 角色权限
     */
    private Set<String> rolePermission;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 手机号码
     * <p>与授权服务端 {@code io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser#phonenumber} 同名对应，
     * 由 {@code loginUser} 总线消息下发；服务端登录构建用户信息时（{@code buildLoginUser}）
     * 已从 {@code sys_user.phonenumber} 赋值。小程序等未落库手机号的登录方式为空。</p>
     */
    private String phonenumber;

    /**
     * 密码
     */
//    private String password;

    /**
     * 角色对象
     */
    private List<CloudRoleDTO> roles;

    /**
     * 岗位对象
     */
    private List<CloudPostDTO> posts;

    /**
     * 获取当前用户的岗位名称列表（便捷方法，从 posts 中提取）
     * <p>返回非 null 的 postName 列表，若 posts 为空则返回空列表。</p>
     *
     * <p>必须 @JsonIgnore：这是无对应字段的计算型 getter，Jackson 会把它当成名为
     * {@code postNames} 的属性写进 JSON；反序列化时它是 setterless 属性，
     * 在 sa-token 共享 Session 的 typed deser 下直接抛 InvalidDefinitionException，
     * 导致 loginUser 快照在 Session 里永远读写失败（每次请求都被迫远程拉取）。</p>
     *
     * @since 1.5.6
     */
    @JsonIgnore
    public List<String> getPostNames() {
        if (posts == null || posts.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>(posts.size());
        for (CloudPostDTO p : posts) {
            if (p != null && p.getPostName() != null && !p.getPostName().isEmpty()) {
                names.add(p.getPostName());
            }
        }
        return names;
    }

    /**
     * 数据权限 当前角色ID
     */
    private Long roleId;

    /**
     * 客户端
     */
    private String clientKey;

    /**
     * 设备类型
     */
    private String deviceType;

}
