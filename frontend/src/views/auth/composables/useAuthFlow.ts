import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { to } from 'await-to-js';
import { useUserStore } from '@/store/modules/user';
import {
  register as apiRegister,
  getTenantList,
  getCodeByPwdApi,
  sendSmsCodeByPhoneApi,
  loginByPhoneApi,
  ssoLoginByPhoneApi,
  getCodeByResetPwdApi,
  changePwdApi,
  getRedirectUrl,
  getCodeImg
} from '@/api/login';
import { resolveLoginClientId, resolveRedirectTarget, persistClientId, saveTokenClientId } from '@/utils/client';
import { buildSsoLoginQuery } from '@/utils/ssoQuery';
import { setToken } from '@/utils/auth';
import { useClientConfigStore } from '@/store/modules/clientConfig';

/**
 * 把 el-form 的 validate(callback) 包装成 Promise<boolean>
 *
 * <p>Element Plus 的 validate 是同步返回的，并不会 await callback，
 * 写成 {@code await form.validate(cb)} 会让 handleXxx 立即返回，
 * 业务逻辑在 callback 里跑，导致 loading 状态错乱、用户可重复点击。</p>
 *
 * <p>本工具把 callback 转成 Promise，让 await 真正等待校验完成。</p>
 */
export const validateForm = (formRef: any): Promise<boolean> => {
  return new Promise((resolve) => {
    if (!formRef || !formRef.value) return resolve(false);
    formRef.value.validate((valid: boolean) => resolve(!!valid));
  });
};

/**
 * 登录/注册/重置密码流程 Composable
 *
 * <p>把所有页面（默认模板、辽运通模板）共用的业务逻辑收敛到这里，
 * 模板组件只负责 UI 展示。</p>
 *
 * <p>职责：</p>
 * <ul>
 *   <li>获取/缓存 tenantId、username、password（记住密码）</li>
 *   <li>解析 redirect + clientId 跳转链</li>
 *   <li>登录、注册、发送短信码、重置密码等 API 调用</li>
 *   <li>图形验证码、租户列表拉取</li>
 *   <li>登录后的 redirect 跳转（内部 router.push 或外部 location.href）</li>
 * </ul>
 */
export const useAuthFlow = () => {
  const router = useRouter();
  const userStore = useUserStore();
  const configStore = useClientConfigStore();

  const loading = ref(false);
  const captchaEnabled = ref(true);
  const captchaImg = ref('');
  const captchaUuid = ref('');
  const tenantEnabled = ref(true);
  const tenantList = ref<Array<{ companyName: string; tenantId: string; domain?: any }>>([]);

  // 登录用 client：只认 URL（?client=），不回退 localStorage——
  // 避免 /ssologin?redirect=/index 这类无 client 页面被上次授权残留的 client 劫持，
  // 导致 token 的 clientId 与请求头 clientid 不一致（网关 401 客户端ID与Token不匹配）
  const clientId = ref<string>(resolveLoginClientId());

  /**
   * 记住密码：从 localStorage 读 username/password/tenantId/rememberMe
   */
  const restoreRemembered = (target: {
    tenantId: string;
    username: string;
    password: string;
    rememberMe: boolean;
  }) => {
    const tenantId = localStorage.getItem('tenantId');
    const username = localStorage.getItem('username');
    const password = localStorage.getItem('password');
    const rememberMe = localStorage.getItem('rememberMe');
    if (tenantId !== null) target.tenantId = tenantId;
    if (username !== null) target.username = username;
    if (password !== null) target.password = password;
    target.rememberMe = rememberMe === 'true';
  };

  /**
   * 记住密码：写 localStorage
   */
  const persistRemembered = (tenantId: string, username: string, password: string, rememberMe: boolean) => {
    if (rememberMe) {
      localStorage.setItem('tenantId', String(tenantId));
      localStorage.setItem('username', String(username));
      localStorage.setItem('password', String(password));
      localStorage.setItem('rememberMe', 'true');
    } else {
      localStorage.removeItem('tenantId');
      localStorage.removeItem('username');
      localStorage.removeItem('password');
      localStorage.removeItem('rememberMe');
    }
  };

  /**
   * 加载租户列表
   */
  const loadTenantList = async () => {
    const { data } = await getTenantList(false);
    tenantEnabled.value = data.tenantEnabled === undefined ? true : data.tenantEnabled;
    if (tenantEnabled.value) {
      tenantList.value = data.voList || [];
    }
  };

  /**
   * 加载图形验证码
   */
  const loadCaptcha = async (uuidKey: string = 'uuid'): Promise<{ uuid: string; img: string } | null> => {
    try {
      const res = await getCodeImg();
      const data = res.data;
      captchaEnabled.value = data.captchaEnabled === undefined ? true : data.captchaEnabled;
      if (captchaEnabled.value) {
        captchaImg.value = 'data:image/gif;base64,' + data.img;
        captchaUuid.value = data.uuid;
        return { uuid: data.uuid, img: captchaImg.value };
      }
      return null;
    } catch (e) {
      captchaEnabled.value = false;
      return null;
    }
  };

  /**
   * 登录成功后处理 redirect 跳转
   */
  const handlePostLoginRedirect = async (targetClient?: string) => {
    const redirectRaw = (router.currentRoute.value.query?.redirect as string) || '';
    const { target, targetClient: tc } = resolveRedirectTarget(redirectRaw, targetClient || clientId.value);
    const finalClient = tc || clientId.value || import.meta.env.VITE_APP_CLIENT_ID;
    persistClientId(finalClient);

    // auth_mode=center：登录成功后进入授权中心首页，不生成 ticket 回跳 SDK 客户端
    if (String(router.currentRoute.value.query?.auth_mode || '').toLowerCase() === 'center') {
      await router.push('/index');
      return;
    }

    if (target.startsWith('http')) {
      try {
        const { data } = await getRedirectUrl({
          redirect: encodeURIComponent(target),
          client: finalClient
        });
        if (data) {
          location.href = data;
          return;
        }
      } catch (e) {
        console.warn('[useAuthFlow] getRedirectUrl 失败:', e);
      }
      // 外部回跳地址拿不到 ticket 时不能 router.push 到 http URL（会被当成内部路由落到 404），
      // 留在授权中心首页，避免落到带无效票据的空白客户端页
      await router.push('/index');
      return;
    }
    await router.push(target);
  };

  /**
   * 密码登录
   */
  const loginByPassword = async (form: {
    tenantId: string;
    username: string;
    password: string;
    rememberMe: boolean;
    code?: string;
    uuid?: string;
  }) => {
    loading.value = true;
    try {
      persistRemembered(form.tenantId, form.username, form.password, form.rememberMe);
      const loginData: any = {
        ...form,
        clientId: clientId.value || import.meta.env.VITE_APP_CLIENT_ID,
        grantType: 'password'
      };
      // sso 优先（有 redirect 时走 sso，否则走普通 login）
      const redirectRaw = (router.currentRoute.value.query?.redirect as string) || '';
      const [err] = redirectRaw
        ? await to(userStore.ssologin(loginData))
        : await to(userStore.login(loginData));
      if (!err) {
        await handlePostLoginRedirect();
      } else {
        if (captchaEnabled.value) await loadCaptcha();
      }
    } finally {
      loading.value = false;
    }
  };

  /**
   * 短信登录（sendSmsCode + sms grantType 提交到 /auth/login）
   */
  const sendLoginSmsCode = async (username: string, password: string, tenantId: string) => {
    const res = await getCodeByPwdApi({ username, password, tenantId });
    return res.code === 200;
  };

  /**
   * 手机号 + 验证码登录：按手机号发送短信码（免密码）
   */
  const sendLoginSmsCodeByPhone = async (phonenumber: string, tenantId: string) => {
    const res = await sendSmsCodeByPhoneApi({
      phonenumber,
      tenantId,
      clientId: clientId.value || import.meta.env.VITE_APP_CLIENT_ID
    });
    return res.code === 200;
  };

  /**
   * 手机号 + 短信验证码登录（免密码）
   *
   * <p>无需客户端配置 sms 授权类型。与 {@link loginByPassword} 对称：</p>
   * <ul>
   *   <li>有 redirect 参数（SDK 授权入口 /ssologin?client=x&redirect=y）→ 走
   *       /auth/sso/doLoginByPhone，在认证中心建立 SSO 会话，随后携带 ticket 回跳子系统；</li>
   *   <li>否则走 /auth/loginByPhone 普通登录。</li>
   * </ul>
   *
   * <p>登录成功后写入 token 并处理 redirect 跳转。</p>
   */
  const loginByPhoneCode = async (phonenumber: string, smsCode: string, tenantId: string) => {
    loading.value = true;
    try {
      const payload = {
        phonenumber,
        smsCode,
        tenantId,
        clientId: clientId.value || import.meta.env.VITE_APP_CLIENT_ID
      };
      persistClientId(payload.clientId);
      const redirectRaw = (router.currentRoute.value.query?.redirect as string) || '';
      const [err, res] = (await to(
        redirectRaw ? ssoLoginByPhoneApi(payload) : loginByPhoneApi(payload)
      )) as [any, any];
      if (err) return;
      const accessToken = res?.data?.access_token;
      if (accessToken) {
        setToken(accessToken);
        userStore.token = accessToken;
        // 记录 token 归属 client，供请求头 clientid 与网关校验保持一致
        saveTokenClientId(payload.clientId);
      }
      await handlePostLoginRedirect();
    } finally {
      loading.value = false;
    }
  };

  /**
   * 注册
   */
  const register = async (form: {
    tenantId: string;
    username: string;
    password: string;
    confirmPassword?: string;
    code?: string;
    uuid?: string;
    userSourceType?: string;
  }) => {
    loading.value = true;
    try {
      const data: any = {
        ...form,
        clientId: clientId.value || import.meta.env.VITE_APP_CLIENT_ID,
        grantType: 'password'
      };
      const [err] = await to(apiRegister(data));
      if (!err) {
        await ElMessageBox.alert(
          `<span style="color: red; ">注册成功！请使用账号 ${form.username} 登录</span>`,
          '系统提示',
          {
            dangerouslyUseHTMLString: true,
            type: 'success'
          }
        );
        // 注册成功后跳登录页，保留完整 SSO 上下文（含 auth_mode），避免授权模式丢失
        const q = router.currentRoute.value.query || {};
        const query = buildSsoLoginQuery({
          client: clientId.value,
          redirect: q.redirect ? String(q.redirect) : '',
          auth_mode: q.auth_mode ? String(q.auth_mode) : ''
        });
        await router.push({ path: '/login', query });
      } else {
        if (captchaEnabled.value) await loadCaptcha();
      }
    } finally {
      loading.value = false;
    }
  };

  /**
   * 发送重置密码短信码
   */
  const sendResetSmsCode = async (params: {
    username: string;
    oldPassword: string;
    newPassword: string;
    tenantId: string;
  }) => {
    const data = { ...params, clientId: clientId.value || import.meta.env.VITE_APP_CLIENT_ID };
    const res = await getCodeByResetPwdApi(data);
    return res.code === 200;
  };

  /**
   * 提交重置密码
   *
   * <p>两种方式二选一：传 oldPassword 走"账号 + 旧密码 + 验证码"；
   * 不传 oldPassword、传 phonenumber 走"手机号 + 短信验证码"找回（验证码由 sendLoginSmsCodeByPhone 发送）。</p>
   */
  const submitResetPwd = async (params: {
    userName: string;
    code: string;
    oldPassword?: string;
    newPassword: string;
    tenantId: string;
    phonenumber?: string;
  }) => {
    const data = { ...params, clientId: clientId.value || import.meta.env.VITE_APP_CLIENT_ID };
    const res = await changePwdApi(data);
    return res.code === 200;
  };

  /**
   * 模板内跨页面跳转：保留当前 URL 的 redirect 上下文
   *
   * <p>SDK 流程中 redirect 是回跳到子系统的关键参数，
   * 用户从 /ssologin?client=A&redirect=... 跳到 /register 或 /login 时必须带上，
   * 否则注册/登录成功后无法回到子系统。</p>
   */
  const pushAuthRoute = (path: string) => {
    const q = router.currentRoute.value.query || {};
    const query = buildSsoLoginQuery({
      client: clientId.value,
      redirect: q.redirect ? String(q.redirect) : '',
      auth_mode: q.auth_mode ? String(q.auth_mode) : ''
    });
    router.push({ path, query });
  };

  return {
    loading,
    captchaEnabled,
    captchaImg,
    captchaUuid,
    tenantEnabled,
    tenantList,
    clientId,
    restoreRemembered,
    persistRemembered,
    loadTenantList,
    loadCaptcha,
    loginByPassword,
    sendLoginSmsCode,
    sendLoginSmsCodeByPhone,
    loginByPhoneCode,
    register,
    sendResetSmsCode,
    submitResetPwd,
    handlePostLoginRedirect,
    pushAuthRoute
  };
};
