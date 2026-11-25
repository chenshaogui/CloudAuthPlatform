package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户信息
 *
 * @author Michelle.Chung
 */
@Data
public class RemoteUserInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户基本信息
     */
    private RemoteUserVo user;

    /**
     * 菜单权限
     */
    private Set<String> permissions;

    /**
     * 角色权限
     */
    private Set<String> roles;

}
