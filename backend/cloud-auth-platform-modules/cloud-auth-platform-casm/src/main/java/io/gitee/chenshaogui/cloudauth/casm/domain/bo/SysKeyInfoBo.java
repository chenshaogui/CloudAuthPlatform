package io.gitee.chenshaogui.cloudauth.casm.domain.bo;

import io.gitee.chenshaogui.cloudauth.casm.domain.SysKeyInfo;
import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.EditGroup;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

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
    @NotNull(message = "id不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 密钥标识
     */
    @NotBlank(message = "密钥标识不能为空", groups = { AddGroup.class, EditGroup.class })
    private String keyId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 应用端ID
     */
    @NotBlank(message = "应用端ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private String clientId;

    /**
     * 密钥名称
     */
    @NotBlank(message = "密钥名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String keyName;

    /**
     * 密钥级别
     */
    private Integer keyLevel;

    /**
     * 密钥类型
     */
    @NotBlank(message = "密钥类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private String keyType;


    /**
     * 密钥算法
     */
    private String keyAlthm;

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
     * 加密密钥ID
     */
    private Long kekKeyId;

    /**
     * 客户端主键ID
     */
    private Long clientKeyId;

}
