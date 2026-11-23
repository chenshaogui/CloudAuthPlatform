package io.gitee.chenshaogui.cloudauth.casm.domain.vo;

import io.gitee.chenshaogui.cloudauth.casm.domain.bo.SysKeyInfoBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyAlthmEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyLevelEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyTypeEnum;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Date;

/**
 * 密钥信息对象 用户存储密钥信息
 *
 * @author JLL
 * @date 2025-12-03
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@AutoMapper(target = SysKeyInfoBo.class, reverseConvertGenerate = false)
@AutoMapper(target = KeyInfoRspVo.class, reverseConvertGenerate = false)
public class KeyInfoVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 唯一标识ID
     */
    private Long id;

    /**
     * 密钥标识
     */
    private String keyId;


    /**
     * 客户端ID
     */
    private String clientId;


    /**
     * 密钥算法
     */
    private KeyAlthmEnum keyAlthm;

    /**
     * 密钥级别
     * 密钥等级（1 - 核心：存储于 HSM，2 - 普通：加密存储于 MySQL）
     */
    private KeyLevelEnum keyLevel;

    /**
     * 密钥类型
     * 密钥类型（KEK - 密钥加密密钥，DEK - 数据加密密钥）
     */
    private KeyTypeEnum keyType;

    /**
     * 加密密钥串
     */
    private String encryptedKey;

    /**
     * 加密密钥ID
     */
    private Long kekKeyId;

    /**
     * 密钥过期时间
     */
    private Date expireTime;


    /**
     * 密钥盐
     */
    private String salt;

    /**
     * 前一个密钥ID
     */
    private Long prevKeyId;


    /**
     * 密钥轮转密钥ID
     */
    private Long rotationKeyId;

    /**
     * 密钥轮转时间
     */
    private Date rotationTime;

    /**
     * 最后一次密钥轮转时间
     */
    private Date lastRotationTime;


    /**
     * 密钥使用次数
     */
    private Long useCount = 0L;
}
