package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.baomidou.mybatisplus.annotation.TableId;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClientRole;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户端-角色关联实体
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SysClientRole.class)
public class SysClientRoleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
