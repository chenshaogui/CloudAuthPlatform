package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.chenshaogui.cloudauth.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户-客户端配置关系实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_client_config")
public class SysUserClientConfig extends TenantEntity {
    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 客户端ID
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 数据脱敏标识（0=不脱敏 1=脱敏）
     */
    @TableField("desensitize_flag")
    private String desensitizeFlag;

    /**
     * 归属地编码
     */
    @TableField("belong_code")
    private String belongCode;

    /**
     * 扩展配置（JSON格式）
     */
    @TableField("ext_config")
    private String extConfig; // 若需JSON解析，可改用 Map<String, Object> + 类型处理器

    /**
     * 归属地名称
     */
    private String belongName;

}
