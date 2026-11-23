package io.gitee.chenshaogui.cloudauth.casm.domain.vo;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteKeyInfoVo;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Date;

/**
 * 密钥信息返回对象 用户存储密钥信息
 *
 * @author JLL
 * @date 2025-12-03
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@AutoMapper(target = RemoteKeyInfoVo.class)
public class KeyInfoRspVo {

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
     * 密钥类型
     * 密钥类型（KEK - 密钥加密密钥，DEK - 数据加密密钥）
     */
    private String keyType;

    /**
     * 密钥串
     */
    private String keys;


    /**
     * 密钥过期时间
     */
    private Date expireTime;



    /**
     * 密钥明文串
     */
    private String unkeys;

    /**
     * 密钥使用次数
     */
    private Long useCount;
}
