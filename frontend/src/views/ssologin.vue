<template>
  <div class="login">
    <!-- 已登录时隐藏登录表单，显示过渡状态，避免弹窗背后还是登录框 -->
    <div v-if="authChecking" class="login-checking">
      <div class="login-checking-card">
        <el-icon class="is-loading" :size="36"><Loading /></el-icon>
        <p>正在检测登录状态…</p>
      </div>
    </div>
    <el-row v-else class="login-container">
      <el-col :xs="24" :sm="24" :md="14" :lg="14" :xl="14">
        <div class="login-left">
          <img src="@/assets/images/login-left-icon.png" alt="" />
        </div>
      </el-col>
      <el-col :xs="24" :sm="24" :md="10" :lg="10" :xl="10" class="login-right">
        <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" label-position="top">
          <div class="title-box">
            <h3 class="title">{{ title }}</h3>
            <div class="title-box-qw">
              <el-button class="title-box-qw-btn" type="primary" @click="doSocialLogin('wechat_enterprise')">企业微信登录</el-button>
            </div>
          </div>
          <el-form-item prop="username" label="账户名称" class="bottom-input">
            <el-input v-model="loginForm.username" type="text" size="large" auto-complete="off" placeholder="请输入登录系统的账户名称"> </el-input>
          </el-form-item>
          <el-form-item prop="password" label="输入密码" class="bottom-input">
            <el-input
              v-model="loginForm.password"
              type="password"
              size="large"
              auto-complete="off"
              placeholder="请输入登录系统的密码"
              @keyup.enter="handleLogin"
            >
            </el-input>
          </el-form-item>
          <el-form-item prop="code" label="输入验证码" class="flex-content-between bottom-input">
            <el-input
              v-model="loginForm.code"
              size="large"
              auto-complete="off"
              placeholder="请输入验证码"
              style="width: 63%"
              maxlength="6"
              @keyup.enter="handleLogin"
            >
            </el-input>
            <div class="login-code-btn">
              <el-button type="primary" size="large" plain style="width: 100%" :disabled="isSend" @click="getPhoneCode">{{ codeMsg }}</el-button>
            </div>
          </el-form-item>
          <el-checkbox v-model="loginForm.rememberMe" style="margin: 0 0 25px 0">记住密码</el-checkbox>
          <el-form-item style="float: right">
            <el-button type="primary" link @click="handleEditPwd"> 修改密码 </el-button>
          </el-form-item>
          <el-form-item style="width: 100%">
            <el-button :loading="loading" size="large" type="primary" style="width: 100%" @click.prevent="handleLogin">
              <span v-if="!loading">{{ proxy.$t('login.login') }}</span>
              <span v-else>{{ proxy.$t('login.logging') }}</span>
            </el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>
    <!--  底部  -->
    <div class="el-login-footer">
      <!--      <span>Copyright © 2018-2025 疯狂的狮子Li All Rights Reserved.</span>-->
    </div>
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="500px" append-to-body>
      <el-form ref="formRef" :model="forgetForm" :rules="forgetFormRules" label-width="100px" style="min-height: 315px">
        <el-form-item prop="username" label="手机号：">
          <el-input v-model="forgetForm.username" type="text" size="large" auto-complete="off" placeholder="请输入用户名" maxlength="11" />
        </el-form-item>
        <el-form-item prop="oldPassword" label="旧密码：">
          <el-input v-model="forgetForm.oldPassword" type="password" size="large" auto-complete="off" placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item prop="newPassword" label="新密码：">
          <el-input v-model="forgetForm.newPassword" type="password" size="large" auto-complete="off" placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item prop="confirmPassword" label="确认密码：">
          <el-input v-model="forgetForm.confirmPassword" type="password" size="large" auto-complete="off" placeholder="请输入确认密码" />
        </el-form-item>
        <el-form-item prop="code" label="验证码：" style="margin-bottom: 0">
          <el-input
            v-model="forgetForm.code"
            size="large"
            auto-complete="off"
            placeholder="请输入验证码"
            style="width: 63%; height: 38px"
            maxlength="6"
            @keyup.enter="submitForm"
          >
            <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
          </el-input>
          <div class="login-code-btn">
            <el-button type="primary" size="large" plain style="width: 100%" :disabled="isSend" @click="getPwdPhoneCode">{{ codeMsg }}</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="loading" type="primary" :disabled="!forgetForm.code" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 已登录提示对话框：确认授权 / 切换账号 / 进入授权中心 -->
    <el-dialog v-model="authDialog.visible" title="系统提示" width="500px" append-to-body :show-close="false" :close-on-click-modal="false">
      <div class="auth-dialog-body">
        <el-icon class="auth-dialog-icon" :size="24" color="#E6A23C"><WarningFilled /></el-icon>
        <span>检测到当前账号已登录，是否使用当前账号直接进入系统？</span>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleAuthSwitchAccount" :loading="authDialog.loading">切换账号</el-button>
          <el-button @click="handleGoAuthCenter">进入授权中心</el-button>
          <el-button type="primary" :loading="authDialog.loading" @click="handleAuthConfirm">确认授权</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getTenantList, getRedirectUrl, getCodeByPwdApi, changePwdApi, getCodeByResetPwdApi } from '@/api/login';
import { authBinding } from '@/api/system/social/auth';
import { useUserStore } from '@/store/modules/user';
import { LoginData, TenantVO } from '@/api/types';
import { to } from 'await-to-js';
import { HttpStatus } from '@/enums/RespEnum';
import { useI18n } from 'vue-i18n';
import { getQueryObject } from '@/utils';
import { parseInnerSsoQuery } from '@/utils/ssoQuery';
import { getToken, removeToken } from '@/utils/auth';
import { isRelogin } from '@/utils/request';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const title = import.meta.env.VITE_APP_TITLE;
const userStore = useUserStore();
const router = useRouter();
const { t } = useI18n();
const codeMsg = ref('获取验证码');
const isSend = ref(false);
const formRef = ref<ElFormInstance>();

const loginForm = ref<LoginData>({
  tenantId: '000000',
  username: '',
  password: '',
  rememberMe: false,
  code: '',
  uuid: '',
  clientId: import.meta.env.VITE_APP_CLIENT_ID
} as LoginData);

const loginRules: ElFormRules = {
  tenantId: [{ required: true, trigger: 'blur', message: t('login.rule.tenantId.required') }],
  username: [{ required: true, trigger: 'blur', message: t('login.rule.username.required') }],
  password: [{ required: true, trigger: 'blur', message: t('login.rule.password.required') }],
  code: [{ required: true, trigger: 'change', message: t('login.rule.code.required') }]
};

const dialog = ref<DialogOption>({
  visible: false,
  title: '修改密码'
});

const forgetForm = ref({
  tenantId: '000000',
  username: '',
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
  code: ''
});
const phoneReg = /^1[3456789]\d{9}$/;

const forgetFormRules = {
  username: [
    { required: true, trigger: 'blur', message: '请输入手机号' },
    {
      validator: (rule: any, value: string, callback: any) => {
        if (phoneReg.test(value)) {
          callback();
        } else {
          callback(new Error('请输入正确的手机号'));
        }
      },
      trigger: 'blur'
    }
  ],
  oldPassword: [{ required: true, trigger: 'blur', message: '请输入旧密码' }],
  newPassword: [{ required: true, trigger: 'blur', message: '请输入新密码' }],
  confirmPassword: [
    { required: true, trigger: 'blur', message: '请输入确认密码' },
    {
      validator: (rule: any, value: string, callback: any) => {
        if (value === forgetForm.value.newPassword) {
          callback();
        } else {
          callback(new Error('新密码和确认密码不一致'));
        }
      },
      trigger: 'blur'
    }
  ],
  code: [{ required: true, trigger: 'change', message: '请输入验证码' }]
};

const loading = ref(false);
const redirect = ref('/');
const client = ref(import.meta.env.VITE_APP_CLIENT_ID);
const loginRef = ref<ElFormInstance>();
// 已登录检测中：隐藏登录表单，避免弹窗背后仍是登录框
const authChecking = ref(false);
// 已登录提示对话框
const authDialog = ref<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });
// 租户开关
const tenantEnabled = ref(true);
// 租户列表
const tenantList = ref<TenantVO[]>([]);
const codeCount = ref(0);
const timer = ref<ReturnType<typeof setInterval> | null>(null);

watch(
  () => router.currentRoute.value,
  (newRoute: any) => {
    redirect.value = newRoute.query && newRoute.query.redirect && decodeURIComponent(newRoute.query.redirect);
    client.value = newRoute.query && newRoute.query.client;
    const queryJson = getQueryObject(location.href);
    if (queryJson.client && !client.value) {
      client.value = queryJson.client;
    }
  },
  { immediate: true }
);

/**
 * 解析跳转目标：redirect 可能是嵌套的 /ssologin 跳转链（如 /ssologin?client=xxx&redirect=https://xxx），
 * 需要解出真正的目标地址和目标 client，否则会 router.push 回本页导致无反应
 */
const resolveRedirectTarget = () => {
  let target = redirect.value || '/';
  let targetClient = client.value;
  if (target.startsWith('/') && target.includes('/ssologin')) {
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
    } catch (ignored) {
      // 解析失败则按原样处理
    }
  }
  return { target, targetClient };
};

const handleLogin = () => {
  loginRef.value?.validate(async (valid: boolean, fields: any) => {
    if (valid) {
      loading.value = true;
      // 勾选了需要记住密码设置在 localStorage 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        localStorage.setItem('tenantId', String(loginForm.value.tenantId));
        localStorage.setItem('username', String(loginForm.value.username));
        localStorage.setItem('password', String(loginForm.value.password));
        localStorage.setItem('rememberMe', String(loginForm.value.rememberMe));
      } else {
        // 否则移除
        localStorage.removeItem('tenantId');
        localStorage.removeItem('username');
        localStorage.removeItem('password');
        localStorage.removeItem('rememberMe');
      }
      loginForm.value.clientId = import.meta.env.VITE_APP_CLIENT_ID;
      loginForm.value.uuid = loginForm.value.username;
      // 先解析出真正的目标 client，写到 localStorage 用于后续 401 兜底跳转
      const { target, targetClient } = resolveRedirectTarget();
      localStorage.setItem('client', targetClient || client.value || import.meta.env.VITE_APP_CLIENT_ID);
      const [err] = await to(userStore.ssologin(loginForm.value));
      if (!err) {
        // auth_mode=center：登录成功后进入授权中心，不生成 ticket 回跳 SDK 客户端
        if (String(router.currentRoute.value.query?.auth_mode || '').toLowerCase() === 'center') {
          await router.push('/index');
          loading.value = false;
          return;
        }
        if (target.startsWith('http')) {
          try {
            const { data } = await getRedirectUrl({ redirect: encodeURIComponent(target), client: targetClient || import.meta.env.VITE_APP_CLIENT_ID });
            location.href = data;
          } finally {
            loading.value = false;
          }
        } else {
          await router.push(target);
          loading.value = false;
        }
      } else {
        loading.value = false;
      }
    } else {
      console.log('error submit!', fields);
    }
  });
};

const getLoginData = () => {
  const tenantId = localStorage.getItem('tenantId');
  const username = localStorage.getItem('username');
  const password = localStorage.getItem('password');
  const rememberMe = localStorage.getItem('rememberMe');
  loginForm.value = {
    tenantId: tenantId === null ? String(loginForm.value.tenantId) : tenantId,
    username: username === null ? String(loginForm.value.username) : username,
    password: password === null ? String(loginForm.value.password) : String(password),
    rememberMe: rememberMe === null ? false : Boolean(rememberMe)
  } as LoginData;
};

/**
 * 获取租户列表
 */
const initTenantList = async () => {
  const { data } = await getTenantList(false);
  tenantEnabled.value = data.tenantEnabled === undefined ? true : data.tenantEnabled;
  if (tenantEnabled.value) {
    tenantList.value = data.voList;
    if (tenantList.value != null && tenantList.value.length !== 0) {
      loginForm.value.tenantId = tenantList.value[0].tenantId;
    }
  }
};

/**
 * 第三方登录
 * @param type
 */
const doSocialLogin = (type: string) => {
  let domain: string = '';
  const { target, targetClient } = resolveRedirectTarget();
  const clientForAuth = targetClient || import.meta.env.VITE_APP_CLIENT_ID;
  if (target.includes('http')) {
    const domainJson = {
      redirect: target,
      client: clientForAuth
    };
    domain = JSON.stringify(domainJson);
  }
  authBinding(type, loginForm.value.tenantId, domain).then((res: any) => {
    if (res.code === HttpStatus.SUCCESS) {
      // 获取授权地址跳转
      window.location.href = res.data;
    } else {
      ElMessage.error(res.msg);
    }
  });
};

const getPhoneCode = async () => {
  if (!loginForm.value.username) {
    ElMessage.warning('请输入手机号');
    return;
  }
  if (!loginForm.value.password) {
    ElMessage.warning('请输入密码');
    return;
  }
  const res = await getCodeByPwdApi({ username: loginForm.value.username, password: loginForm.value.password, tenantId: loginForm.value.tenantId });
  if (res.code === 200) {
    ElMessage.success('验证码发送成功！');
    codeCountdown();
  } else {
    ElMessage.error(res.msg || '验证码发送失败');
  }
};

// 验证码倒计时
const codeCountdown = () => {
  codeCount.value = 60;
  isSend.value = true;
  timer.value = setInterval(() => {
    codeMsg.value = `${codeCount.value}秒后重发`;
    if (codeCount.value > 0 && codeCount.value <= 60) {
      codeCount.value--;
    } else {
      codeMsg.value = '获取验证码';
      isSend.value = false;
      clearInterval(timer.value);
    }
  }, 1000);
};

const handleEditPwd = () => {
  dialog.value.visible = true;
};

const cancel = () => {
  codeMsg.value = '获取验证码';
  isSend.value = false;
  clearInterval(timer.value);
  forgetForm.value = {
    tenantId: '000000',
    username: '',
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    code: ''
  };
  dialog.value.visible = false;
};

const getPwdPhoneCode = async () => {
  if (!forgetForm.value.username) {
    ElMessage.warning('请输入手机号');
    return;
  }
  // 验证手机号是否正确
  if (!phoneReg.test(forgetForm.value.username)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (!forgetForm.value.oldPassword) {
    ElMessage.warning('请输入旧密码');
    return;
  }
  if (!forgetForm.value.newPassword) {
    ElMessage.warning('请输入新密码');
    return;
  }
  if (!forgetForm.value.confirmPassword) {
    ElMessage.warning('请输入确认密码');
    return;
  }
  if (forgetForm.value.newPassword !== forgetForm.value.confirmPassword) {
    ElMessage.warning('新密码和确认密码不一致');
    return;
  }
  const params = {
    username: forgetForm.value.username,
    oldPassword: forgetForm.value.oldPassword,
    newPassword: forgetForm.value.newPassword,
    tenantId: forgetForm.value.tenantId
  };
  const res = await getCodeByResetPwdApi(params);
  if (res.code === 200) {
    ElMessage.success('验证码发送成功！');
    codeCountdown();
  } else {
    ElMessage.error(res.msg || '验证码发送失败');
  }
};

const submitForm = () => {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      const data = {
        userName: forgetForm.value.username,
        oldPassword: forgetForm.value.oldPassword,
        newPassword: forgetForm.value.newPassword,
        tenantId: forgetForm.value.tenantId,
        code: forgetForm.value.code
      };
      const res = await changePwdApi(data);
      if (res.code === 200) {
        ElMessage.success('修改成功！');
        cancel();
      } else {
        ElMessage.error(res.msg || '修改失败');
      }
    } else {
      return false;
    }
  });
};

onMounted(async () => {
  initTenantList();
  getLoginData();

  // 注：已登录检测与"已登录授权"弹窗已统一由 SsoLoginPage 处理，
  // 本组件作为纯登录表单使用，不再触发 authDialog（避免双弹窗/闪烁）。
});

onUnmounted(() => {
  // 清理验证码倒计时定时器，防止组件卸载后仍执行
  if (timer.value) {
    clearInterval(timer.value);
    timer.value = null;
  }
});

/**
 * 已登录弹窗 - 确认授权
 */
const handleAuthConfirm = async () => {
  const { target, targetClient } = resolveRedirectTarget();
  authDialog.value.loading = true;
  // 阻止 request.ts 对 401 弹出全局"登录状态已过期"弹窗，由本页自行处理
  isRelogin.show = true;
  try {
    // 后端 /auth/sso/getRedirectUrl 会为目标 client 自动补登 token，无需重新登录
    const { data } = await getRedirectUrl({
      redirect: target === '/' ? '' : encodeURIComponent(target),
      client: targetClient || import.meta.env.VITE_APP_CLIENT_ID
    });
    if (data) {
      location.href = data;
      return;
    }
    authDialog.value.loading = false;
  } catch {
    // 本地 token 已失效（401 等）：注销并回落到登录表单，避免死循环
    await handleAuthSwitchAccount();
  } finally {
    isRelogin.show = false;
  }
};

/**
 * 已登录弹窗 - 切换账号：注销当前登录态，显示登录表单让用户输入新账号
 */
const handleAuthSwitchAccount = async () => {
  authDialog.value.loading = true;
  // 抑制 request.ts 全局 401 弹窗，避免 token 失效时把过期提示叠到本弹窗上
  isRelogin.show = true;
  try {
    try {
      await userStore.ssoLogout();
    } catch (ignored) {
      // 即使服务端失败也继续，让用户在本地重新登录
    }
    // 本地兜底清理：服务端注销失败（如 token 已失效 401）时也要清掉过期 token
    userStore.token = '';
    removeToken();
    // 重新生成 browserId，避免新用户继承旧 ID 共享 session
    localStorage.removeItem('__sdk_browser_id__');
  } finally {
    isRelogin.show = false;
  }
  authDialog.value.visible = false;
  authDialog.value.loading = false;
  authChecking.value = false;
};

/**
 * 已登录弹窗 - 进入授权中心：跳转到项目主页
 */
const handleGoAuthCenter = () => {
  authDialog.value.visible = false;
  authDialog.value.loading = false;
  authChecking.value = false;
  router.push('/index');
};
</script>

<style lang="scss" scoped>
$primaryColor: #547bd6;
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-image: url('../assets/images/login-bg.jpg');
  background-size: 100% 100%;
  background-repeat: no-repeat;

  &-container {
    max-width: 60%;
    background: linear-gradient(to bottom, rgb(30, 120, 216), rgb(15, 95, 198), rgb(2, 72, 181));
    border-radius: 10px;
  }

  &-left {
    padding: 60px;
    height: 100%;

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  &-checking {
    position: fixed;
    inset: 0;
    display: flex;
    justify-content: center;
    align-items: center;

    &-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      padding: 40px 60px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.95);
      box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);

      p {
        margin: 0;
        color: $primaryColor;
        font-size: 15px;
        font-weight: 500;
      }
    }
  }
}

.auth-dialog-body {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: #606266;
  line-height: 1.6;
  padding: 4px 2px;

  .auth-dialog-icon {
    flex-shrink: 0;
  }
}

.title-box {
  display: flex;
  margin: 20px 0 0;
  position: relative;

  .title {
    margin: 20px auto 30px auto;
    text-align: center;
    font-weight: 600;
    color: $primaryColor;
  }

  :deep(.lang-select--style) {
    line-height: 0;
    color: #7483a3;
  }
  &-qw {
    position: absolute;
    right: -49px;
    top: -44px;

    &-btn {
      border-radius: 0 10px 0 0;
    }
  }
}

.login-form {
  border-radius: 10px;
  background: #ffffff;
  width: 100%;
  height: 100%;
  min-width: 400px;
  min-height: 530px;
  padding: 25px 50px 25px;
  z-index: 1;
  .el-input {
    height: 40px;
    input {
      height: 40px;
    }
  }

  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0px;
  }
}

.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}

.login-code {
  width: 33%;
  height: 40px;
  float: right;

  img {
    cursor: pointer;
    vertical-align: middle;
  }
}

.login-code-btn {
  width: 32%;
  height: 40px;
  margin-left: 5%;
}

.el-login-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial, serif;
  font-size: 12px;
  letter-spacing: 1px;
}

.login-code-img {
  height: 40px;
  padding-left: 12px;
}
</style>

<style lang="scss">
.bottom-input .el-input__wrapper {
  box-shadow: none;
  border-bottom: solid 1px var(--el-border-color);

  &:hover {
    box-shadow: none;
    border-bottom: solid 1px var(--el-color-primary);
  }

  &:focus {
    box-shadow: none;
    border-bottom: solid 1px var(--el-color-primary);
  }
}
.bottom-input .el-input__inner {
  color: var(--el-color-primary);
  font-weight: 500;
}
</style>
