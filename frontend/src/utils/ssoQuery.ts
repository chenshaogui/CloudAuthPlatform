import CryptoJS from 'crypto-js';
import { deflateRaw, inflateRaw } from 'pako';
import { parseQuery, stringifyQuery } from 'vue-router';
import type { LocationQuery, LocationQueryRaw } from 'vue-router';

/**
 * /ssologin 跳转参数的加密串兼容层
 *
 * <p>支持两种链接，业务代码（route.query.client / route.query.redirect）无需任何改动：</p>
 * <ol>
 *   <li>老链接：{@code #/ssologin?client=xxx&redirect=yyy}，明文参数照旧解析</li>
 *   <li>新链接：{@code #/ssologin?auth_request=xxx}，密文串解密后还原为 client/redirect 等参数</li>
 * </ol>
 *
 * <p>实现方式：接管 vue-router 的 parseQuery / stringifyQuery。
 * 地址栏始终只有密文，明文参数只存在于内存的 route.query 中；
 * 前端内部跳转（router.push / router.replace）产生的敏感参数也会被重新加密。</p>
 *
 * <p>密文格式（与 SDK 的 SsoParamCrypto 完全一致）：
 * {@code Base64URL( IV(16) + AES-CTR-NoPadding( flags(1) + 参数数据 ) )}，
 * AES key = SHA256(密钥) 前 16 字节，IV 随机。
 * CTR 为流式模式无需填充，密文长度等于明文长度；参数数据加密前还会做
 * 紧凑序列化（短键名 + 分隔符 KV + URL 前缀字典）与 raw DEFLATE 压缩
 * （压缩后更短才启用），以缩短 URL。</p>
 *
 * <p><b>安全说明</b>：前端必须持有密钥才能解密，因此这是"防明文暴露 / 防随手篡改"，
 * 不是机密级加密（打包产物可被逆向）。后端如需强校验，仍应在
 * /auth/sso/getRedirectUrl 侧对 redirect 做白名单校验。</p>
 */

/** 密文参数名 */
export const SSO_QUERY_KEY = 'auth_request';

/** sessionStorage 键：SDK 首次下发的原始 auth_request 密文串 */
const SSO_AUTH_REQUEST_STORAGE_KEY = 'sso_auth_request_token';

/**
 * 记住原始密文串。
 *
 * <p>退出登录 / 401 / 页面间跳转都会重建 /ssologin 的 query，若重新加密，
 * 随机 IV 与 timestamp/nonce 会让 auth_request 字面值变化，且容易漏掉 auth_mode；
 * 原样复用 SDK 下发的串可保证 URL 不变、授权模式不丢失。</p>
 */
const rememberAuthRequest = (token: string): void => {
  try {
    if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(SSO_AUTH_REQUEST_STORAGE_KEY, token);
  } catch (e) {
    // 隐私模式 / 存储被禁用时忽略，降级为重新加密
  }
};

/** 读取记住的原始密文串（无则返回空串） */
export const getRememberedAuthRequest = (): string => {
  try {
    return (typeof sessionStorage !== 'undefined' && sessionStorage.getItem(SSO_AUTH_REQUEST_STORAGE_KEY)) || '';
  } catch (e) {
    return '';
  }
};

/**
 * 构造重建 /ssologin 时的 query：优先原样复用记住的 auth_request，
 * 否则用调用方给出的明文参数（由 stringifySsoQuery 收进新密文）。
 *
 * <p>auth_request 不属于 ENCRYPTED_KEYS，会作为普通参数原样写回地址栏，
 * 再由 parseSsoQuery 解密平铺回 client/redirect/auth_mode。</p>
 */
export const buildSsoLoginQuery = (fallback: Record<string, string | null | undefined> = {}): Record<string, string> => {
  const token = getRememberedAuthRequest();
  if (token) return { [SSO_QUERY_KEY]: token };
  const query: Record<string, string> = {};
  Object.keys(fallback).forEach((key) => {
    const value = fallback[key];
    if (value !== null && value !== undefined && value !== '') query[key] = String(value);
  });
  return query;
};

/** 与 SDK 配置项 sa-token.sso-param-key 保持一致（不配置时使用同一默认值） */
const DEFAULT_SSO_PARAM_KEY = 'AesKeyForSso2026';
const SECRET = (import.meta.env.VITE_APP_SSO_PARAM_KEY as string) || DEFAULT_SSO_PARAM_KEY;

/** 需要收进密文的参数；其余参数（如 mode）仍以明文出现在 URL 上 */
const ENCRYPTED_KEYS = ['client', 'redirect', 'clientLoginUrl', 'back', 'auth_mode', 'timestamp', 'nonce', 'sign'];

/** 短键名别名表（与 SDK SsoParamCrypto 保持一致） */
const LONG_TO_SHORT: Record<string, string> = {
  client: 'c',
  redirect: 'r',
  clientLoginUrl: 'cl',
  back: 'b',
  timestamp: 'ts',
  nonce: 'n',
  sign: 's'
};
const SHORT_TO_LONG: Record<string, string> = Object.fromEntries(Object.entries(LONG_TO_SHORT).map(([k, v]) => [v, k]));

/** 紧凑 KV 格式：键值对分隔符、键值分隔符 */
const PAIR_SEP = '\u001f';
const KV_SEP = '\u001e';
/** URL 前缀字典标记：把常见的协议头压成 1 字节 */
const PREFIX_HTTPS = '\u0001';
const PREFIX_HTTP = '\u0002';

/** flags 位：1=raw DEFLATE 压缩，2=使用短键名，4=回退 JSON 格式 */
const FLAG_DEFLATE = 1;
const FLAG_SHORT_KEYS = 2;
const FLAG_JSON = 4;

/** SHA256(密钥) 前 16 字节作为 AES-128 密钥 */
const deriveKey = (): CryptoJS.lib.WordArray => {
  const hash = CryptoJS.SHA256(SECRET);
  return CryptoJS.lib.WordArray.create(hash.words.slice(0, 4), 16);
};

const toUrlSafe = (base64: string): string => base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

const fromUrlSafe = (token: string): string => {
  let base64 = token.replace(/-/g, '+').replace(/_/g, '/');
  while (base64.length % 4 !== 0) base64 += '=';
  return base64;
};

/** CryptoJS WordArray 与 Uint8Array 互转（压缩库需要字节数组） */
const u8ToWordArray = (u8: Uint8Array): CryptoJS.lib.WordArray => {
  const words: number[] = [];
  for (let i = 0; i < u8.length; i++) {
    words[i >>> 2] = (words[i >>> 2] || 0) | (u8[i] << (24 - (i % 4) * 8));
  }
  return CryptoJS.lib.WordArray.create(words, u8.length);
};

const wordArrayToU8 = (wa: CryptoJS.lib.WordArray): Uint8Array => {
  const u8 = new Uint8Array(wa.sigBytes);
  for (let i = 0; i < wa.sigBytes; i++) {
    u8[i] = (wa.words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff;
  }
  return u8;
};

/**
 * 参数序列化：短键名 + 紧凑 KV + URL 前缀字典，必要时回退 JSON；
 * 压缩后更短才启用 raw DEFLATE。返回 flags + 数据字节。
 */
const serializeParams = (params: Record<string, string>): { flags: number; data: Uint8Array } => {
  // 原始键名若与短名冲突（例如本来就有 c / r），整体不启用短名，避免解密歧义
  const shortKeys = Object.keys(params).every((k) => !(k in SHORT_TO_LONG));
  const mapped: Record<string, string> = {};
  Object.keys(params).forEach((k) => {
    mapped[shortKeys ? LONG_TO_SHORT[k] || k : k] = params[k];
  });

  // 值本身含控制字符时回退 JSON，保证可逆
  const conflict = Object.entries(mapped).some(
    ([k, v]) =>
      k.includes(PAIR_SEP) ||
      k.includes(KV_SEP) ||
      v.includes(PAIR_SEP) ||
      v.includes(KV_SEP) ||
      v.startsWith(PREFIX_HTTPS) ||
      v.startsWith(PREFIX_HTTP)
  );
  let flags = shortKeys ? FLAG_SHORT_KEYS : 0;
  let text: string;
  if (conflict) {
    text = JSON.stringify(mapped);
    flags |= FLAG_JSON;
  } else {
    text = Object.entries(mapped)
      .map(([k, v]) => {
        let value = v;
        if (value.startsWith('https://')) value = PREFIX_HTTPS + value.slice(8);
        else if (value.startsWith('http://')) value = PREFIX_HTTP + value.slice(7);
        return `${k}${KV_SEP}${value}`;
      })
      .join(PAIR_SEP);
  }

  const raw = new TextEncoder().encode(text);
  const zipped = deflateRaw(raw, { level: 9 });
  const compressed = zipped.length < raw.length;
  if (compressed) flags |= FLAG_DEFLATE;
  return { flags, data: compressed ? zipped : raw };
};

/** 反序列化：解压 → 解析紧凑 KV/JSON → 还原短键名与 URL 前缀 */
const deserializeParams = (flags: number, data: Uint8Array): Record<string, string> => {
  const text = flags & FLAG_DEFLATE ? inflateRaw(data, { to: 'string' }) : new TextDecoder().decode(data);
  const parsed: Record<string, string> = {};
  if (flags & FLAG_JSON) {
    const obj = JSON.parse(text);
    Object.keys(obj).forEach((k) => {
      parsed[k] = String(obj[k]);
    });
  } else {
    text.split(PAIR_SEP).forEach((pair) => {
      if (!pair) return;
      const idx = pair.indexOf(KV_SEP);
      if (idx < 0) return;
      const key = pair.slice(0, idx);
      let value = pair.slice(idx + 1);
      if (value.startsWith(PREFIX_HTTPS)) value = 'https://' + value.slice(1);
      else if (value.startsWith(PREFIX_HTTP)) value = 'http://' + value.slice(1);
      parsed[key] = value;
    });
  }
  const result: Record<string, string> = {};
  Object.keys(parsed).forEach((k) => {
    const key = flags & FLAG_SHORT_KEYS ? SHORT_TO_LONG[k] || k : k;
    result[key] = parsed[k];
  });
  return result;
};

/** 将参数对象加密为一个 URL 安全的密文串 */
export const encryptSsoParams = (params: Record<string, string>): string => {
  const { flags, data } = serializeParams(params);
  const body = new Uint8Array(data.length + 1);
  body[0] = flags;
  body.set(data, 1);
  const iv = CryptoJS.lib.WordArray.random(16);
  const encrypted = CryptoJS.AES.encrypt(u8ToWordArray(body), deriveKey(), {
    iv,
    mode: CryptoJS.mode.CTR,
    padding: CryptoJS.pad.NoPadding
  });
  return toUrlSafe(CryptoJS.enc.Base64.stringify(iv.concat(encrypted.ciphertext)));
};

/** 解密密文串为参数对象；密文非法 / 密钥不匹配时返回 null */
export const decryptSsoParams = (token: string): Record<string, string> | null => {
  try {
    const combined = CryptoJS.enc.Base64.parse(fromUrlSafe(token));
    // CTR 无填充：至少要有 IV(16) + 1 字节数据
    if (combined.sigBytes <= 16) return null;
    const words = combined.words;
    const iv = CryptoJS.lib.WordArray.create(words.slice(0, 4), 16);
    const ciphertext = CryptoJS.lib.WordArray.create(words.slice(4), combined.sigBytes - 16);
    const cipherParams = CryptoJS.lib.CipherParams.create({ ciphertext });
    const decrypted = CryptoJS.AES.decrypt(cipherParams, deriveKey(), {
      iv,
      mode: CryptoJS.mode.CTR,
      padding: CryptoJS.pad.NoPadding
    });
    if (decrypted.sigBytes < 2) return null;
    const body = wordArrayToU8(decrypted);
    return deserializeParams(body[0], body.subarray(1));
  } catch (e) {
    console.warn('[ssoQuery] 密文解析失败，按明文参数兼容处理:', e);
    return null;
  }
};

/**
 * vue-router parseQuery 兼容层：
 * 有 auth_request 密文时解密并平铺回 query，无密文时完全走 vue-router 原生解析
 */
export const parseSsoQuery = (search: string): LocationQuery => {
  const raw = parseQuery(search);
  const token = raw[SSO_QUERY_KEY];
  if (typeof token !== 'string' || !token) return raw;
  const decoded = decryptSsoParams(token);
  if (!decoded) return raw;
  rememberAuthRequest(token);
  const merged: LocationQuery = { ...raw, ...decoded };
  delete merged[SSO_QUERY_KEY];
  return merged;
};

/**
 * vue-router stringifyQuery 兼容层：
 * 内部跳转时把敏感参数收进密文，避免地址栏重新出现明文
 */
export const stringifySsoQuery = (query: LocationQueryRaw): string => {
  const rest: LocationQueryRaw = {};
  const secret: Record<string, string> = {};
  Object.keys(query).forEach((key) => {
    const value = query[key];
    if (ENCRYPTED_KEYS.includes(key) && value !== null && value !== undefined) {
      secret[key] = Array.isArray(value) ? String(value[0] ?? '') : String(value);
    } else {
      rest[key] = value;
    }
  });
  // vue-router 的 stringifyQuery 约定返回值不带前导 '?'，这里保持一致
  const search = stringifyQuery(rest);
  if (Object.keys(secret).length === 0) return search;
  const token = encryptSsoParams(secret);
  const pair = `${SSO_QUERY_KEY}=${encodeURIComponent(token)}`;
  return search ? `${search}&${pair}` : pair;
};

/**
 * 从当前地址栏（hash 优先，其次 search）读取密文并解密。
 * 供 utils/client.ts 等"直接读 location"的旧逻辑复用，保持其调用方式不变。
 */
export const readSsoParamsFromLocation = (): Record<string, string> => {
  try {
    const hash = window.location.hash || '';
    const hashIdx = hash.indexOf('?');
    const candidates = [hashIdx >= 0 ? hash.substring(hashIdx) : '', window.location.search || ''];
    for (const search of candidates) {
      if (!search) continue;
      const token = parseQuery(search)[SSO_QUERY_KEY];
      if (typeof token === 'string' && token) {
        const decoded = decryptSsoParams(token);
        if (decoded) return decoded;
      }
    }
  } catch (e) {
    // 地址栏异常时按无密文处理，不影响原有明文解析
  }
  return {};
};

/**
 * 解析 redirect 嵌套链中的查询串（如 /ssologin?client=x&redirect=y）：
 * 兼容内层参数也已被加密的情况，返回平铺后的参数对象。
 */
export const parseInnerSsoQuery = (queryStr: string): Record<string, string> => {
  const params: Record<string, string> = {};
  try {
    new URLSearchParams(queryStr || '').forEach((value, key) => {
      params[key] = value;
    });
    const token = params[SSO_QUERY_KEY];
    if (token) {
      delete params[SSO_QUERY_KEY];
      const decoded = decryptSsoParams(token);
      if (decoded) return { ...params, ...decoded };
    }
  } catch (e) {
    // 解析失败保持原值
  }
  return params;
};
