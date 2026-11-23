/**
 * 注册
 */
export type RegisterForm = {
  tenantId: string;
  username: string;
  password: string;
  confirmPassword?: string;
  code?: string;
  uuid?: string;
  userType?: string;
};

/**
 * 登录请求
 */
export interface LoginData {
  tenantId?: string;
  username?: string;
  password?: string;
  rememberMe?: boolean;
  socialCode?: string;
  socialState?: string;
  source?: string;
  code?: string;
  uuid?: string;
  clientId: string;
  grantType: string;
}

/**
 * 登录响应
 */
export interface LoginResult {
  access_token: string;
}

/**
 * 验证码返回
 */
export interface VerifyCodeResult {
  captchaEnabled: boolean;
  uuid?: string;
  img?: string;
}

/**
 * 租户
 */
export interface TenantVO {
  companyName: string;
  domain: any;
  tenantId: string;
}

export interface TenantInfo {
  tenantEnabled: boolean;
  voList: TenantVO[];
}

export interface CodeData {
  username: string;
  tenantId: string;
  password: string;
}

export interface CodePwdResetData {
  username: string;
  oldPassword: string;
  newPassword: string;
  tenantId: string;
}

export interface ForgetPwdData {
  userName: string;
  code: string;
  /** 旧密码：手机号 + 短信验证码找回流程可不传 */
  oldPassword?: string;
  newPassword: string;
  tenantId: string;
  /** 手机号（免旧密码找回流程必传，后端按手机号定位用户并校验验证码） */
  phonenumber?: string;
  /** 客户端ID（用于校验该客户端是否开放重置密码） */
  clientId?: string;
}
