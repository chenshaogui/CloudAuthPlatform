package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * 用户
 *
 * @author Michelle.Chung
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudSyncUserVo implements Serializable {

    /**
     * 用户账号
     */
    private String userName;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 手机号码
     */
    private String phonenumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    private String sex;

    /**
     * 数据是否脱敏 0=否 1=是
     */
    private Integer desensitizeFlag;

    /**
     * 归属地编码
     */
    private String belongCode;

    /**
     * 管辖归属地名称
     */
    private String belongName;

    /**
     * 客户端ID
     */
    private String clientId;

}
