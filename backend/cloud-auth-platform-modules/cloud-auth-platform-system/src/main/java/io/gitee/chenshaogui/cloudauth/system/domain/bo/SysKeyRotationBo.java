package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import io.gitee.chenshaogui.cloudauth.common.core.validate.EditGroup;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysKeyRotation;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 密钥轮换信息管理业务对象 sys_key_rotation
 *
 * @author JLL
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysKeyRotation.class, reverseConvertGenerate = false)
public class SysKeyRotationBo extends BaseEntity {

    /**
     * id
     */
    @NotNull(message = "id不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 客户端id
     */
    private String clientId;

    /**
     * 旧密钥ID
     */
    private Long oldKeyId;

    /**
     * 状态（0正常 1停用）
     */
    private String status;


}
