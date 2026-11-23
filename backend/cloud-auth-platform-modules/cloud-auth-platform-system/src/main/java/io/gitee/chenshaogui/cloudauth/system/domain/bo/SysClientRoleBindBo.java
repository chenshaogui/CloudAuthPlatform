package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 客户端-角色关联实体
 */
@Data
public class SysClientRoleBindBo {

    @NotEmpty(message = "roleIdList不能为空")
    private List<Long> roleIdList;

    /**
     * 客户端id
     */
    @NotBlank(message = "clientId不能为空")
    private String clientId;

}
