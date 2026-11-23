import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { LoginData, LoginResult, VerifyCodeResult, TenantInfo, CodeData, ForgetPwdData, CodePwdResetData } from './types';
import { UserInfo } from '@/api/system/user/types';

// pc端固定客户端授权id
const clientId = import.meta.env.VITE_APP_CLIENT_ID;

/**
 * @param data {LoginData}
 * @returns
 */
export function login(data: LoginData): AxiosPromise<LoginResult> {
  const params = {
    ...data,
    clientId: data.clientId || clientId,
    grantType: data.grantType || 'password'
  };
  return request({
    url: '/auth/login',
    headers: {
      isToken: false,
      isEncrypt: true,
      repeatSubmit: false
    },
    method: 'post',
    data: params
  });
}

/**
 * @param data {LoginData}
 * @returns
 */
export function ssologin(data: LoginData): AxiosPromise<LoginResult> {
  const params = {
    ...data,
    clientId: data.clientId || clientId,
    grantType: data.grantType || 'password'
  };
  return request({
    url: '/auth/sso/doLogin',
    headers: {
      isToken: false,
      isEncrypt: true,
      repeatSubmit: false
    },
    method: 'post',
    data: params
  });
}

/**
 * 获取重定向url
 */
export function getRedirectUrl(data): AxiosPromise<any> {
  const LoginData = {
    ...data
  };
  return request({
    url: '/auth/sso/getRedirectUrl',
    method: 'post',
    headers: {
      'X-Requested-With': 'XMLHttpRequest',
      'Content-Type': 'multipart/form-data'
    },
    data: LoginData
  });
}

// 注册方法
export function register(data: any) {
  const params = {
    ...data,
    clientId: clientId,
    grantType: 'password'
  };
  return request({
    url: '/auth/register',
    headers: {
      isToken: false,
      isEncrypt: true,
      repeatSubmit: false
    },
    method: 'post',
    data: params
  });
}

/**
 * 注销
 */
export function logout() {
  if (import.meta.env.VITE_APP_SSE === 'true') {
    request({
      url: '/resource/sse/close',
      method: 'get'
    });
  }
  return request({
    url: '/auth/logout',
    method: 'post'
  });
}

/**
 * sso注销登录
 */
export function ssoLogout() {
  if (import.meta.env.VITE_APP_SSE === 'true') {
    request({
      url: '/resource/sse/close',
      method: 'get'
    });
  }
  return request({
    url: '/auth/sso/logout',
    method: 'get'
  });
}

/**
 * 获取验证码
 */
export function getCodeImg(): AxiosPromise<VerifyCodeResult> {
  return request({
    url: '/auth/code',
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  });
}

/**
 * 第三方登录
 */
export function callback(data: LoginData): AxiosPromise<any> {
  const LoginData = {
    ...data,
    clientId: clientId,
    grantType: 'social'
  };
  return request({
    url: '/auth/social/callback',
    method: 'post',
    data: LoginData
  });
}

// 获取用户详细信息
export function getInfo(): AxiosPromise<UserInfo> {
  return request({
    url: '/system/user/getInfo',
    method: 'get'
  });
}

// 获取租户列表
export function getTenantList(isToken: boolean): AxiosPromise<TenantInfo> {
  return request({
    url: '/auth/tenant/list',
    headers: {
      isToken: isToken
    },
    method: 'get'
  });
}

// 获取验证码
export function getCodeApi(phonenumber: string): AxiosPromise<any> {
  return request({
    url: '/resource/sms/code',
    headers: {
      isToken: false
    },
    method: 'get',
    params: {
      phonenumber: phonenumber
    }
  });
}

// 获取验证码通过手机号以及密码
export function getCodeByPwdApi(params: CodeData): AxiosPromise<any> {
  return request({
    url: '/auth/sendSmsCode',
    headers: {
      isToken: false
    },
    method: 'post',
    data: params
  });
}

/**
 * 手机号 + 验证码登录：按手机号发送短信验证码（免密码）
 */
export function sendSmsCodeByPhoneApi(params: { phonenumber: string; tenantId: string; clientId?: string }): AxiosPromise<any> {
  return request({
    url: '/auth/sendSmsCodeByPhone',
    headers: {
      isToken: false
    },
    method: 'post',
    data: {
      ...params,
      clientId: params.clientId || clientId
    }
  });
}

/**
 * 手机号 + 短信验证码登录（免密码）
 */
export function loginByPhoneApi(data: { phonenumber: string; smsCode: string; tenantId: string; clientId?: string }): AxiosPromise<LoginResult> {
  return request({
    url: '/auth/loginByPhone',
    headers: {
      isToken: false,
      isEncrypt: true,
      repeatSubmit: false
    },
    method: 'post',
    data: {
      ...data,
      clientId: data.clientId || clientId,
      grantType: 'sms'
    }
  });
}

/**
 * 手机号 + 短信验证码 SSO 登录（免密码，建立认证中心会话后回跳子系统）
 *
 * <p>与 {@link ssologin} 对称，仅登录凭据换成手机号 + 验证码，
 * 登录成功后浏览器会持有认证中心会话，可继续走 /auth/sso/getRedirectUrl 携带 ticket 回跳。</p>
 */
export function ssoLoginByPhoneApi(data: { phonenumber: string; smsCode: string; tenantId: string; clientId?: string }): AxiosPromise<LoginResult> {
  return request({
    url: '/auth/sso/doLoginByPhone',
    headers: {
      isToken: false,
      isEncrypt: true,
      repeatSubmit: false
    },
    method: 'post',
    data: {
      ...data,
      clientId: data.clientId || clientId,
      grantType: 'sms'
    }
  });
}

export function getCodeByResetPwdApi(data: CodePwdResetData): AxiosPromise<any> {
  return request({
    url: '/auth/sendSmsCodeByReset',
    headers: {
      isToken: false
    },
    method: 'post',
    data
  });
}

export function changePwdApi(data: ForgetPwdData): AxiosPromise<any> {
  return request({
    url: '/system/user/profile/updatePwdNoLogin',
    headers: {
      isToken: false
    },
    method: 'post',
    data
  });
}
