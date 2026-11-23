package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;


/**
 * 授权管理视图对象 sys_client
 *
 * @author JLL
 */
@Data
public class RemoteKeyInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 密钥标识
     */
    private String keyId;

    /**
     * 应用端ID
     */
    private Long clientId;

    /**
     * 密钥名称
     */
    private String keyName;

    /**
     * 密钥类型
     */
    private String keyType;

    /**
     * 密钥过期时间
     */
    private Date expireTime;

    /**
     * 密钥轮换次数
     */
    private String rotateCount;

    /**
     * 密钥最后轮换时间
     */
    private Date lastRotateTime;

    /**
     * 状态（0正常 1停用）
     */
    private String status;


    /**
     * 明文密钥
     */
    private String unkeys;


    /**
     * 使用次数
     */
    private Long useCount;

}
