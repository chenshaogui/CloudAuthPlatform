package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.EditGroup;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClientRole;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 客户端-角色关联实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysClientRole.class, reverseConvertGenerate = false)
public class SysClientRoleBo extends BaseEntity {

    /**
     * id
     */
    @NotNull(message = "id不能为空", groups = { EditGroup.class })
    private Long id;

    @NotBlank(message = "clientId不能为空", groups = { AddGroup.class, EditGroup.class })
    private String clientId;

    @NotNull(message = "roleId不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long roleId;


    private List<Long> roleIdList;

}
