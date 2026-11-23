package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.EditGroup;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 授权管理业务对象 sys_client
 *
 * @author Michelle.Chung
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysClient.class, reverseConvertGenerate = false)
public class SysClientKeysBo extends BaseEntity {

    /**
     * id
     */
    @NotNull(message = "id不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 客户端id
     */
    @NotNull(message = "客户端id不能为空", groups = { EditGroup.class })
    private String clientId;

    /**
     * 关联应用keyId
     */
    private Long appKeyId;

    /**
     * 平台keyId
     */
    private Long platformKeyId;


    /**
     * 应用ip列表
     */
    private String appIpList;

}
