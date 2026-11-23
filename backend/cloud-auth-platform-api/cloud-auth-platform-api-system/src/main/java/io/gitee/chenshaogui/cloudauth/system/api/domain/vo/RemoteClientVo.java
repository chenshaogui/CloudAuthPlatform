package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;


/**
 * 授权管理视图对象 sys_client
 *
 * @author Michelle.Chung
 */
@Data
public class RemoteClientVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
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
    private List<String> grantTypeList;

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
     * 关联应用菜单id
     */
    private Long menuId;

    /**
     * 关联应用keyId，用于客户端侧验签
     */
    private Long appKeyId;

    /**
     * 平台keyId，用于平台侧验签
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
     * 签名key
     */
    private String signKey;

    /**
     * 登录/注册/重置密码页面布局配置（JSON 字符串）
     */
    private String layoutConfig;

    /**
     * 是否开放注册（0=关闭 1=开放）
     */
    private String registerEnabled;

    /**
     * 是否开放重置密码（0=关闭 1=开放）
     */
    private String resetPwdEnabled;

}
