package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.EditGroup;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysKeyInfo;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 密钥信息管理业务对象 sys_key_info
 *
 * @author JLL
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysKeyInfo.class, reverseConvertGenerate = false)
public class SysKeyInfoBo extends BaseEntity {

    /**
     * id
     */
    private Long id;

    /**
     * 应用端ID
     */
    @NotBlank(message = "应用端ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private String clientId;


    /**
     * 密钥ID
     */
    private String keyId;

    /**
     * 密钥名称
     */
    @NotBlank(message = "密钥名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String keyName;

    /**
     * 密钥类型：TEK、KEK、PEK、DEK、AEK
     */
    @NotBlank(message = "密钥类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private String keyType;


    /**
     * 密钥算法：AES、RSA、SM2、SM4
     * 主要是SM2和SM4
     */
    private String keyAlthm;


    /**
     * 状态（0正常 1停用）
     */
    private String status;


}
