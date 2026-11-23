package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户信息
 *
 * @author Michelle.Chung
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudUserInfoVo implements Serializable {

    /**
     * 用户基本信息
     */
    private CloudUserVo user;

    /**
     * 菜单权限
     */
    private Set<String> permissions;

    /**
     * 角色权限
     */
    private Set<String> roles;

}
