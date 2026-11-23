export interface ClientVO {
  /**
   * id
   */
  id: string | number;

  /**
   * 客户端id
   */
  clientId: string;

  /**
   * 客户端key
   */
  clientKey: string;

  /**
   * 客户端秘钥
   */
  clientSecret: string;

  /**
   * 授权类型
   */
  grantTypeList: string[];

  /**
   * 设备类型
   */
  deviceType: string;

  /**
   * token活跃超时时间
   */
  activeTimeout: number;

  /**
   * token固定超时
   */
  timeout: number;

  /**
   * 状态（0正常 1停用）
   */
  status: string;

  /**
   * 登录/注册/重置密码页面布局配置（JSON 字符串）
   * <p>由鉴权中心前端按 clientId 拉取并解析，结构详见
   * {@link import('@/views/auth/types').ClientLayoutConfig}</p>
   */
  layoutConfig?: string;
}

export interface ClientForm extends BaseEntity {
  /**
   * id
   */
  id?: string | number;

  /**
   * 客户端id
   */
  clientId?: string | number;

  /**
   * 客户端key
   */
  clientKey?: string;

  /**
   * 客户端秘钥
   */
  clientSecret?: string;

  /**
   * 授权类型
   */
  grantTypeList?: string[];

  /**
   * 设备类型
   */
  deviceType?: string;

  /**
   * token活跃超时时间
   */
  activeTimeout?: number;

  /**
   * token固定超时
   */
  timeout?: number;

  /**
   * 状态（0正常 1停用）
   */
  status?: string;
  /**
   * 允许授权地址
   */
  allowUrl?: string;
  /**
   * 登录/注册/重置密码页面布局配置（JSON 字符串）
   * <p>由鉴权中心前端按 clientId 拉取并解析，结构详见
   * {@link import('@/views/auth/types').ClientLayoutConfig}</p>
   */
  layoutConfig?: string;
  /**
   * 是否开放注册（0=关闭 1=开放）
   */
  registerEnabled?: string;
  /**
   * 是否开放重置密码（0=关闭 1=开放）
   */
  resetPwdEnabled?: string;
  /**
   * 应用url
   */
  menuUrl: string;
  /**
   * 绑定应用id
   */
  menuId: string;
  /**
   * 绑定密钥id
   */
  appKeyId: string;
  /**
   * 绑定平台密钥id
   */
  platformKeyId: string;
  /**
   * 应用 ip列表
   */
  appIpList: string;
}

export interface ClientQuery extends PageQuery {
  /**
   * 客户端id
   */
  clientId?: string | number;

  /**
   * 客户端key
   */
  clientKey?: string;

  /**
   * 客户端秘钥
   */
  clientSecret?: string;

  /**
   * 授权类型
   */
  grantType?: string;

  /**
   * 设备类型
   */
  deviceType?: string;

  /**
   * token活跃超时时间
   */
  activeTimeout?: number;

  /**
   * token固定超时
   */
  timeout?: number;

  /**
   * 状态（0正常 1停用）
   */
  status?: string;
}

export interface BindingForm {
  /**
   * id
   */
  id: string | number;
  /**
   * 绑定密钥id
   */
  appKeyId: string;
  /**
   * 绑定平台密钥id
   */
  platformKeyId: string;
  /**
   * 绑定应用id
   */
  clientId: string;
}

export interface KeysForm {
  /**
   * 密钥id
   */
  id: string | number;
  /**
   * 绑定应用key
   */
  appKeyId: string;
  /**
   * 平台密钥id
   */
  platformKeyId: string;
}
