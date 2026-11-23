import { to } from 'await-to-js';
import { getToken, removeToken, setToken } from '@/utils/auth';
import { login as loginApi, ssologin as ssologinApi, logout as logoutApi, getInfo as getUserInfo, ssoLogout as ssoLogoutApi } from '@/api/login';
import { LoginData } from '@/api/types';
import { saveTokenClientId, clearTokenClientId } from '@/utils/client';
import defAva from '@/assets/images/profile.jpg';
import { defineStore } from 'pinia';
import { ref } from 'vue';
import router from '@/router';

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken());
  const name = ref('');
  const nickname = ref('');
  const userId = ref<string | number>('');
  const tenantId = ref<string>('');
  const avatar = ref('');
  const roles = ref<Array<string>>([]); // 用户角色编码集合 → 判断路由权限
  const permissions = ref<Array<string>>([]); // 用户权限编码集合 → 判断按钮权限
  const deptName = ref('');

  /**
   * 登录
   * @param userInfo
   * @returns
   */
  const login = async (userInfo: LoginData): Promise<void> => {
    const [err, res] = await to(loginApi(userInfo));
    if (res) {
      const data = res.data;
      setToken(data.access_token);
      token.value = data.access_token;
      // 记录 token 归属 client，供请求头 clientid 与网关校验保持一致
      saveTokenClientId((userInfo as any)?.clientId);
      return Promise.resolve();
    }
    return Promise.reject(err);
  };
  /**
   * 单点登录
   * @param userInfo
   * @returns
   */
  const ssologin = async (userInfo: LoginData): Promise<void> => {
    const [err, res] = await to(ssologinApi(userInfo));
    if (res) {
      const data = res.data;
      setToken(data.access_token);
      token.value = data.access_token;
      // 记录 token 归属 client，供请求头 clientid 与网关校验保持一致
      saveTokenClientId((userInfo as any)?.clientId);
      return Promise.resolve();
    }
    return Promise.reject(err);
  };
  // 获取用户信息
  const getInfo = async (): Promise<void> => {
    const [err, res] = await to(getUserInfo());
    if (res) {
      const data = res.data;
      const user = data.user;
      const profile = user.avatar == '' || user.avatar == null ? defAva : user.avatar;

      if (data.roles && data.roles.length > 0) {
        // 验证返回的roles是否是一个非空数组
        roles.value = data.roles;
        permissions.value = data.permissions;
      } else {
        roles.value = ['ROLE_DEFAULT'];
      }
      name.value = user.userName;
      nickname.value = user.nickName;
      avatar.value = profile;
      userId.value = user.userId;
      tenantId.value = user.tenantId;
      deptName.value = user.deptName;
      return Promise.resolve();
    }
    return Promise.reject(err);
  };
  // 注销
  const logout = async (): Promise<void> => {
    await logoutApi();
    token.value = '';
    roles.value = [];
    permissions.value = [];
    const query = router.currentRoute.value.query;
    const client: any = query.client && query.client !== 'undefined' ? query.client : import.meta.env.VITE_APP_CLIENT_ID;
    localStorage.setItem('client', client);
    removeToken();
    // 清理 browserId，避免下一位同浏览器用户继承旧 ID 共享 session
    localStorage.removeItem('__sdk_browser_id__');
    clearTokenClientId();
  };
  // 注销
  const ssoLogout = async (): Promise<void> => {
    await ssoLogoutApi();
    token.value = '';
    roles.value = [];
    permissions.value = [];
    removeToken();
    // 清理 browserId
    localStorage.removeItem('__sdk_browser_id__');
    clearTokenClientId();
  };

  const setAvatar = (value: string) => {
    avatar.value = value;
  };

  return {
    userId,
    tenantId,
    token,
    nickname,
    deptName,
    avatar,
    roles,
    permissions,
    login,
    getInfo,
    logout,
    ssoLogout,
    ssologin,
    setAvatar
  };
});
