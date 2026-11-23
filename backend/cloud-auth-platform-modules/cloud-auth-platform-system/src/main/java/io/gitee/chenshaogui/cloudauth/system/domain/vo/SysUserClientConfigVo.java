package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import io.gitee.chenshaogui.cloudauth.system.domain.SysUserClientConfig;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-客户端配置关系实体
 */
@Data
@AutoMapper(target = SysUserClientConfig.class)
public class SysUserClientConfigVo implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端Key
     */
    private String clientKey;

    /**
     * 数据脱敏标识（0=不脱敏 1=脱敏）
     */
    private String desensitizeFlag = "0";

    /**
     * 归属地编码
     */
    private String belongCode;

    /**
     * 扩展配置（JSON格式）
     */
    private String extConfig;

    /**
     * 归属地名称
     */
    private String belongName;

}
