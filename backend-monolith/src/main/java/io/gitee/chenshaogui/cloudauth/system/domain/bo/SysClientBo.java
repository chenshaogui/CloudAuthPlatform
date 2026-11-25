package io.gitee.chenshaogui.cloudauth.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.EditGroup;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;

import java.util.List;

/**
 * 授权管理业务对象 sys_client
 *
 * @author Michelle.Chung
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysClient.class, reverseConvertGenerate = false)
public class SysClientBo extends BaseEntity {

    /**
     * id
     */
    @NotNull(message = "id不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 客户端id
     */
    private String clientId;

    /**
     * 客户端key
     */
    @NotBlank(message = "客户端key不能为空", groups = { AddGroup.class, EditGroup.class })
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
    @NotNull(message = "授权类型不能为空", groups = { AddGroup.class, EditGroup.class })
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
     * 应用单点登录地址
     */
    private String menuUrl;


    /**
     * 应用ip列表
     */
    private String appIpList;

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
