import type { LocationQuery } from 'vue-router';
import { parseInnerSsoQuery, readSsoParamsFromLocation } from '@/utils/ssoQuery';

/**
 * 客户端 ID 解析统一工具
 *
 * <p>三个数据源（按优先级）：</p>
 * <ol>
 *   <li>URL 查询参数 {@code ?client=xxx}（来自 SDK 跳转链接）</li>
 *   <li>localStorage 中上一次保存的 client（用户已登录过该 client）</li>
 *   <li>环境变量 {@code VITE_APP_CLIENT_ID}（默认 PC 客户端）</li>
 * </ol>
 *
 * <p>解析结果会写回 localStorage，供后续 401 兜底跳转（见 request.ts）使用。</p>
 */

const DEFAULT_CLIENT_KEY = 'client';
const DEFAULT_CLIENT_ID = import.meta.env.VITE_APP_CLIENT_ID || '';

/**
 * 从路由查询参数中提取 client
 */
const fromQuery = (query?: LocationQuery | Record<string, any>): string | null => {
  if (!query) return null;
  // vue-router 4 的 query 是 LocationQuery 对象，键为 string，值为 string | string[] | null
  const raw = (query as any).client;
  if (raw == null) return null;
  if (Array.isArray(raw)) return raw[0] || null;
  return String(raw);
};

/**
 * 从 location.href 中提取 client（兜底：URL hash 中可能也带 ?client=）
 */
const fromLocation = (): string | null => {
  try {
    // 兼容加密串：地址栏只有 ?auth_request=xxx 时，从解密结果里取 client
    const encryptedParams = readSsoParamsFromLocation();
    if (encryptedParams.client) return encryptedParams.client;
    const search = window.location.search || '';
    if (search) {
      const params = new URLSearchParams(search);
      const c = params.get('client');
      if (c) return c;
    }
    // hash 路由：# 后可能带 ?client=xxx
    const hash = window.location.hash || '';
    const idx = hash.indexOf('?');
    if (idx >= 0) {
      const params = new URLSearchParams(hash.substring(idx));
      const c = params.get('client');
      if (c) return c;
    }
  } catch (e) {
    // ignore
  }
  return null;
};

/**
 * 从 localStorage 中读取
 */
const fromStorage = (key: string = DEFAULT_CLIENT_KEY): string | null => {
  try {
    return localStorage.getItem(key);
  } catch (e) {
    return null;
  }
};

/**
 * 解析当前 clientId，优先级：URL > location > localStorage > 默认
 */
export const resolveClientId = (routerQuery?: LocationQuery | Record<string, any>): string => {
  return fromQuery(routerQuery) || fromLocation() || fromStorage() || DEFAULT_CLIENT_ID || '';
};

/**
 * 解析"登录用" clientId：只认 URL（query 或 hash 中的 ?client=），不回退 localStorage
 *
 * <p>为什么不回退 localStorage：{@code /ssologin?redirect=/index} 这类授权中心自身登录页
 * 没有 client 参数，若回退到上次第三方授权残留的 client，签发 token 的 clientId
 * 会与请求头 clientid（默认客户端）不一致，网关直接 401"客户端ID与Token不匹配"。</p>
 */
export const resolveLoginClientId = (routerQuery?: LocationQuery | Record<string, any>): string => {
  return fromQuery(routerQuery) || fromLocation() || DEFAULT_CLIENT_ID || '';
};

/**
 * 当前 token 归属 client 的存储键
 *
 * <p>与 {@code client}（供 401 兜底跳转用的"目标 client"）不同，这里存的是
 * "最近一次登录签发 token 的 client"。网关会校验请求头 clientid 与 token 里的
 * clientId 是否一致，所以请求头 clientid 必须跟随登录 client（见 request.ts）。</p>
 */
const TOKEN_CLIENT_KEY = 'tokenClientId';

/** 登录成功后记录 token 归属的 client */
export const saveTokenClientId = (clientId: string | null | undefined): void => {
  if (!clientId) return;
  try {
    localStorage.setItem(TOKEN_CLIENT_KEY, clientId);
  } catch (e) {
    // ignore
  }
};

/** 读取 token 归属的 client（未登录/无记录返回空串） */
export const readTokenClientId = (): string => {
  try {
    return localStorage.getItem(TOKEN_CLIENT_KEY) || '';
  } catch (e) {
    return '';
  }
};

/** 注销时清理 */
export const clearTokenClientId = (): void => {
  try {
    localStorage.removeItem(TOKEN_CLIENT_KEY);
  } catch (e) {
    // ignore
  }
};

/**
 * 显式写入 localStorage（用于首次进入时把 URL 中的 client 缓存下来）
 */
export const persistClientId = (clientId: string, key: string = DEFAULT_CLIENT_KEY): void => {
  if (!clientId) return;
  try {
    localStorage.setItem(key, clientId);
  } catch (e) {
    // ignore
  }
};

/**
 * 解析 redirect 跳转目标，处理嵌套 /ssologin?client=&redirect= 链
 * @returns target 真实目标地址，targetClient 目标 clientId
 */
export const resolveRedirectTarget = (
  rawRedirect: string | null | undefined,
  fallbackClient: string | null | undefined
): { target: string; targetClient: string } => {
  let target = rawRedirect || '/';
  let targetClient = fallbackClient || DEFAULT_CLIENT_ID || '';

  if (typeof target === 'string' && target.startsWith('/') && target.includes('/ssologin')) {
    try {
      const queryStr = target.split('?')[1] || '';
      // 兼容内层 /ssologin?auth_request=xxx 的加密跳转链
      const params = parseInnerSsoQuery(queryStr);
      const innerClient = params.client;
      const innerRedirect = params.redirect;
      if (innerRedirect) {
        target = innerRedirect;
        if (innerClient) targetClient = innerClient;
      }
    } catch (e) {
      // 解析失败保持原值
    }
  }
  return { target, targetClient };
};
