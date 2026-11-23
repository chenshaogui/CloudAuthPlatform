package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 授权管理对象 sys_client
 *
 * @author Michelle.Chung
 * @date 2023-05-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_client")
public class SysClient extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 客户端id
     */
    private String clientId;

    /**
     * 客户端key
     */
    private String clientKey;

    /**
     * 客户端秘钥
     */
    private String clientSecret;

    /**
     * 允许授权地址
     */
    private String allowUrl;

    /**
     * 授权类型
     */
    private String grantType;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * token活跃超时时间
     */
    private Long activeTimeout;

    /**
     * token固定超时时间
     */
    private Long timeout;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 关联应用菜单id
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long menuId;


    /**
     * 关联应用keyId，用于客户端侧验签
     */
    private Long appKeyId;

    /**
     * 平台keyId，用于平台侧验签，取PC端应用的clientId去查询
     */
    private Long platformKeyId;


    /**
     * 应用ip列表，用英文逗号隔开
     */
    private String appIpList;

    /**
     * 密钥池大小
     */
    private Integer keyPoolSize;

    /**
     * 签名key,32位自动生成
     */
    private String signKey;

    /**
     * 登录/注册/重置密码页面布局配置（JSON 字符串）
     * <p>由鉴权中心前端按 clientId 拉取并解析，结构示例：
     * <pre>{@code
     * {
     *   "template": "default|liaoyuntong",
     *   "login":    { "backgroundImage": "...", "primaryColor": "#547bd6", ... },
     *   "register": { ... },
     *   "resetPwd": { ... }
     * }
     * }</pre>
     */
    private String layoutConfig;

    /**
     * 是否开放注册（0=关闭 1=开放），默认关闭
     */
    private String registerEnabled;

    /**
     * 是否开放重置密码（0=关闭 1=开放），默认开启
     */
    private String resetPwdEnabled;

}
