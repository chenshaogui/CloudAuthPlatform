import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getClientPublicConfig } from '@/api/clientConfig';
import type { ClientLayoutConfig, ClientPublicConfigVo } from '@/views/auth/types';

/**
 * 客户端个性化配置 Store（按 clientId 缓存）
 *
 * <p>Pinia store，存储每个 clientId 对应的 ClientPublicConfigVo，
 * 供登录/注册/重置密码页面按 clientId 读取配置。</p>
 *
 * <p>缓存策略：
 * <ul>
 *   <li>首次访问某 clientId 时调 {@code GET /client/config/public} 拉取</li>
 *   <li>同一 clientId 短时间内重复访问直接返回缓存（无 TTL，按需可加）</li>
 *   <li>客户端不存在/拉取失败时缓存 {@code null}，避免重复打接口阻塞页面</li>
 * </ul>
 * </p>
 */
export const useClientConfigStore = defineStore('clientConfig', () => {
  /** 按 clientId 缓存的客户端公共配置（null 表示已确认不存在/拉取失败） */
  const cache = ref<Record<string, ClientPublicConfigVo | null>>({});
  /** 正在拉取中的 clientId 集合（用于并发去重） */
  const loadingSet = ref<Set<string>>(new Set());
  /** 兜底默认 clientId */
  const defaultClientId = import.meta.env.VITE_APP_CLIENT_ID;

  /**
   * 解析 layoutConfig JSON 字符串，失败时返回空对象（不抛错）
   */
  const safeParse = (raw: string | undefined | null): ClientLayoutConfig | null => {
    if (!raw || typeof raw !== 'string') return null;
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') return parsed as ClientLayoutConfig;
      return null;
    } catch (e) {
      console.warn('[clientConfig] layoutConfig JSON 解析失败:', e);
      return null;
    }
  };

  /**
   * 拉取并缓存某个 clientId 的配置（已缓存则直接返回）
   * @param clientId 客户端ID，空则使用默认 clientId
   */
  const ensureLoaded = async (clientId?: string | null): Promise<ClientPublicConfigVo | null> => {
    const cid = clientId || defaultClientId;
    if (!cid) return null;

    // 已缓存（包括 null），直接返回
    if (Object.prototype.hasOwnProperty.call(cache.value, cid)) {
      return cache.value[cid];
    }
    // 正在加载：等待现有请求完成
    if (loadingSet.value.has(cid)) {
      return new Promise((resolve) => {
        const timer = setInterval(() => {
          if (!loadingSet.value.has(cid)) {
            clearInterval(timer);
            resolve(cache.value[cid] ?? null);
          }
        }, 50);
      });
    }

    loadingSet.value.add(cid);
    try {
      const res = await getClientPublicConfig(cid);
      const data = (res as any).data as ClientPublicConfigVo | undefined;
      if (data && data.status === '0') {
        data.layoutConfigParsed = safeParse(data.layoutConfig) || undefined;
        cache.value[cid] = data;
      } else {
        // 客户端不存在/已停用/拉取失败：缓存 null 避免重试
        cache.value[cid] = null;
        console.warn(`[clientConfig] 客户端 ${cid} 配置不可用:`, (res as any).msg);
      }
    } catch (e) {
      cache.value[cid] = null;
      console.warn(`[clientConfig] 客户端 ${cid} 拉取异常:`, e);
    } finally {
      loadingSet.value.delete(cid);
    }
    return cache.value[cid];
  };

  /**
   * 直接获取（不触发网络请求），未加载返回 null
   */
  const get = (clientId?: string | null): ClientPublicConfigVo | null => {
    const cid = clientId || defaultClientId;
    return cid ? cache.value[cid] ?? null : null;
  };

  /**
   * 解析后的 layoutConfig（无配置时返回空对象，避免模板里到处写可选链）
   */
  const getLayoutConfig = (clientId?: string | null): ClientLayoutConfig => {
    const cfg = get(clientId);
    return cfg?.layoutConfigParsed || {};
  };

  /**
   * 当前激活 clientId 对应的 layoutConfig
   * 注意：解析当前路由 clientId 请用 utils/client.ts 的 resolveClientId()，
   * store 不感知路由（Pinia setup 中 useRouter 不可靠）
   */
  const getLayoutConfigFor = (clientId: string): ClientLayoutConfig => {
    return getLayoutConfig(clientId);
  };

  /**
   * 清空某个 clientId 的缓存（如配置修改后强制刷新）
   */
  const invalidate = (clientId?: string | null) => {
    const cid = clientId || defaultClientId;
    if (cid) delete cache.value[cid];
  };

  /**
   * 清空所有缓存
   */
  const clearAll = () => {
    cache.value = {};
  };

  return {
    cache,
    ensureLoaded,
    get,
    getLayoutConfig,
    getLayoutConfigFor,
    invalidate,
    clearAll
  };
});
