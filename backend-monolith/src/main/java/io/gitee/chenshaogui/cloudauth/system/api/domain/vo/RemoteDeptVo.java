package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部门
 *
 * @author AprilWind
 */

@Data
@NoArgsConstructor
public class RemoteDeptVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 父部门ID
     */
    private Long parentId;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 部门编码（业务自定义标识，区别于 dept_id 自增主键）
     */
    private String deptCode;

}
