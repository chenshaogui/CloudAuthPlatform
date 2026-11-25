package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 客户端-角色关联实体
 */
@Data
public class SysClientRoleDelBo  {

    @NotEmpty(message = "idList不能为空")
    private List<Long> idList;

}
