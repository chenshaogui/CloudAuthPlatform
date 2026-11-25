package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户-客户端配置关系实体
 */
@Data
@NoArgsConstructor
public class SysUserClientConfigBatchAddBo {

    @NotEmpty(message = "参数【configList】不能为空")
    List<SysUserClientConfigBo> configList;

}
