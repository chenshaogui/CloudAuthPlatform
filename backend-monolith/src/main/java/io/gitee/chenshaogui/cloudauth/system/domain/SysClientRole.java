package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户端-角色关联实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_client_role")
public class SysClientRole extends BaseEntity {
    /**
     * id
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 角色ID
     */
    private Long roleId;

}
