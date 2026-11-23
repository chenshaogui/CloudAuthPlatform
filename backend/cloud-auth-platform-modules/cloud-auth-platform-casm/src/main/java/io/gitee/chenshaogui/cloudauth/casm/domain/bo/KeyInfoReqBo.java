package io.gitee.chenshaogui.cloudauth.casm.domain.bo;

import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyAlthmEnum;
import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.QueryGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 密钥申请请求对象
 *
 * @author JLL
 */
@Data
public class KeyInfoReqBo {

    /**
     * clientId
     */
    @NotNull(message = "ClientId不能为空", groups = { AddGroup.class, QueryGroup.class })
    private String clientId;



    /**
     * 密钥算法
     * 参考算法枚举
     */
    private String keyAlthm = KeyAlthmEnum.SM4.getType();


    /**
     * 密钥id
     */
    @NotBlank(message = "密钥ID不能为空", groups = { QueryGroup.class })
    private String keyId;

    /**
     * 密钥类型
     */
    @NotBlank(message = "密钥类型不能为空", groups = { AddGroup.class, QueryGroup.class })
    private String keyType;

    /**
     * 请求id，用于跟踪请求日志
     */
    private String requestId;


}
