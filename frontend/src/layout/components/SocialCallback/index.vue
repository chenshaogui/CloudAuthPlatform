<template>
  <div v-loading="loading" class="social-callback"></div>
</template>

<script setup lang="ts">
import { login, callback, getRedirectUrl } from '@/api/login';
import { setToken, getToken } from '@/utils/auth';
import { LoginData } from '@/api/types';
import { getQueryObject } from '@/utils';

const route = useRoute();
const loading = ref(true);

/**
 * 接收Route传递的参数
 * @param {Object} route.query.
 */
let code = route.query.code as string;
let state = route.query.state as string;
const source = (route.query.source as string) || 'wechat_enterprise';
let stateJson: any = {};
let tenantId = '000000';
let domain = '';
if (state) {
  stateJson = JSON.parse(atob(state));
  tenantId = stateJson.tenantId;
  domain = stateJson.domain;
}

const processResponse = async (res: any) => {
  if (res.code !== 200) {
    throw new Error(res.msg);
  }
  if (res.data !== null) {
    setToken(res.data.access_token);
  }
  ElMessage.success(res.msg);
  if (domain.includes('redirect') && domain.includes('client')) {
    const domainJson = JSON.parse(domain);
    const response: any = await getRedirectUrl({ redirect: domainJson.redirect, client: domainJson.client });
    setTimeout(() => {
      location.href = response.data;
    }, 2000);
  } else {
    setTimeout(() => {
      location.href = import.meta.env.VITE_APP_CONTEXT_PATH + 'index.html#/index';
    }, 2000);
  }
};

const handleError = (error: any) => {
  ElMessage.error(error.message);
  setTimeout(() => {
    location.href = import.meta.env.VITE_APP_CONTEXT_PATH + 'index.html#/index';
  }, 2000);
};

const callbackByCode = async (data: LoginData) => {
  try {
    const res = await callback(data);
    await processResponse(res);
    loading.value = false;
  } catch (error) {
    handleError(error);
  }
};

const loginByCode = async (data: LoginData) => {
  try {
    const res = await login(data);
    await processResponse(res);
    loading.value = false;
  } catch (error) {
    handleError(error);
  }
};

const init = async () => {
  // 如果域名不相等 则重定向处理
  // const host = window.location.host;
  // if (domain !== host) {
  //   const urlFull = new URL(window.location.href);
  //   urlFull.host = domain;
  //   window.location.href = urlFull.toString();
  //   return;
  // }
  const qwData = getQueryObject(location.search);
  if (qwData.code && qwData.state) {
    code = qwData.code;
    const stateJson = JSON.parse(atob(qwData.state));
    tenantId = (stateJson.tenantId as string) ? (stateJson.tenantId as string) : '000000';
    state = qwData.state;
    if (stateJson.domain) {
      domain = stateJson.domain;
      console.log('domain', domain);
    }
  }
  console.log('qwData', qwData);
  const data: LoginData = {
    socialCode: code,
    socialState: state,
    tenantId: tenantId,
    source: source,
    clientId: import.meta.env.VITE_APP_CLIENT_ID,
    grantType: 'social'
  };
  console.log('loginData', data);
  if (getToken()) {
    await callbackByCode(data);
  } else {
    await loginByCode(data);
  }
};

onMounted(() => {
  nextTick(() => {
    init();
  });
});
</script>
