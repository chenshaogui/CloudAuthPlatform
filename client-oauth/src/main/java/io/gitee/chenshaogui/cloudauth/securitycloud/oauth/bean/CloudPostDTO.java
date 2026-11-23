package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 岗位
 *
 * @author AprilWind
 */
@Data
@NoArgsConstructor
public class CloudPostDTO implements Serializable {


    /**
     * 岗位ID
     */
    private Long postId;

    /**
     * 部门id
     */
    private Long deptId;

    /**
     * 岗位编码
     */
    private String postCode;

    /**
     * 岗位名称
     */
    private String postName;

    /**
     * 岗位类别编码
     */
    private String postCategory;

}
