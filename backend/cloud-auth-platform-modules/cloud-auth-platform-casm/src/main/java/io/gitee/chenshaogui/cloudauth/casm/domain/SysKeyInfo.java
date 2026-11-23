package io.gitee.chenshaogui.cloudauth.casm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 密钥信息管理对象 sys_key_info
 *
 * @author JLL
 * @date 2025-12-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_key_info")
public class SysKeyInfo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 密钥标识
     */
    private String keyId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 应用端ID
     */
    private String clientId;

    /**
     * 密钥名称
     */
    private String keyName;

    /**
     * 密钥级别
     */
    private Integer keyLevel;

    /**
     * 密钥类型
     */
    private String keyType;

    /**
     * 密钥算法
     */
    private String keyAlthm;

    /**
     * 加密密钥串
     */
    private String encryptedKey;

    /**
     * 加密密钥ID
     */
    private Long kekKeyId;



    /**
     * 客户端主键ID
     */
    private Long clientKeyId;

    /**
     * 数据状态
     */
    private String status;

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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;


}
