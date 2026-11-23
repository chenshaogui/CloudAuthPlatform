<template>
  <!-- 辽运通风格登录页：标题在顶，一体化卡片（左插画 + 右表单），底部链接切换登录方式 -->
  <div class="lyt-login" :style="pageStyle">
    <h1 class="lyt-login-title">{{ title }}</h1>

    <div class="lyt-login-card">
      <!-- 左侧插画 -->
      <div class="lyt-login-left">
        <img v-if="leftImage" :src="leftImage" alt="" />
        <div v-else class="lyt-login-left-placeholder">
          <svg viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="30" y="20" width="140" height="90" rx="8" fill="#ffffff" opacity="0.9" />
            <rect x="42" y="34" width="50" height="8" rx="4" fill="#2468f2" opacity="0.7" />
            <rect x="42" y="50" width="80" height="6" rx="3" fill="#c3d4f7" />
            <rect x="42" y="62" width="64" height="6" rx="3" fill="#c3d4f7" />
            <circle cx="128" cy="88" r="16" fill="#2468f2" opacity="0.25" />
            <rect x="60" y="110" width="34" height="22" rx="5" fill="#ffffff" opacity="0.9" />
            <circle cx="70" cy="134" r="5" fill="#1d4ed8" />
            <circle cx="86" cy="134" r="5" fill="#1d4ed8" />
            <rect x="110" y="116" width="40" height="16" rx="5" fill="#ffffff" opacity="0.9" />
            <circle cx="120" cy="134" r="5" fill="#1d4ed8" />
            <circle cx="138" cy="134" r="5" fill="#1d4ed8" />
          </svg>
        </div>
      </div>

      <!-- 右侧表单 -->
      <div class="lyt-login-right">
        <h2 class="lyt-login-welcome">{{ welcomeText }}</h2>

        <!-- 账号密码登录 -->
        <el-form v-if="tab === 'password'" ref="pwdRef" :model="pwdForm" :rules="pwdRules" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="pwdForm.username" placeholder="请输入用户名" autocomplete="off">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="pwdForm.password" type="password" placeholder="请输入密码" show-password autocomplete="off" @keyup.enter="onPwdLogin">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="captchaEnabled" prop="code">
            <div class="lyt-login-captcha-row">
              <el-input v-model="pwdForm.code" placeholder="请输入验证码" maxlength="6" autocomplete="off">
                <template #prefix><el-icon><Picture /></el-icon></template>
              </el-input>
              <img v-if="captchaImg" :src="captchaImg" class="lyt-login-captcha-img" @click="refreshCaptcha" />
            </div>
          </el-form-item>
          <div v-if="showRememberMe || showForgetPwd" class="lyt-login-extra">
            <el-checkbox v-if="showRememberMe" v-model="pwdForm.rememberMe">记住密码</el-checkbox>
            <el-link v-if="showForgetPwd" type="primary" :underline="false" @click="goResetPwd">忘记密码?</el-link>
          </div>
          <el-button type="primary" :loading="loading" class="lyt-login-submit" size="large" @click="onPwdLogin">登录</el-button>
          <div class="lyt-login-bottom">
            <el-link type="primary" :underline="false" @click="tab = 'phone'">手机号登录</el-link>
            <el-link v-if="showRegister" type="primary" :underline="false" @click="goRegister">注册用户</el-link>
          </div>
        </el-form>

        <!-- 手机号 + 验证码登录（免密码，/auth/loginByPhone） -->
        <el-form v-else ref="phoneRef" :model="phoneForm" :rules="phoneRules" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="phoneForm.username" placeholder="请输入手机号" maxlength="11" autocomplete="off">
              <template #prefix><el-icon><Iphone /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="code">
            <div class="lyt-login-captcha-row">
              <el-input v-model="phoneForm.code" placeholder="请输入验证码" maxlength="6" autocomplete="off">
                <template #prefix><el-icon><Picture /></el-icon></template>
              </el-input>
              <el-button type="primary" :disabled="codeSending" @click="onSendCode">{{ codeBtnText }}</el-button>
            </div>
          </el-form-item>
          <el-button type="primary" :loading="loading" class="lyt-login-submit" size="large" @click="onPhoneLogin">登录</el-button>
          <div class="lyt-login-bottom">
            <el-link type="primary" :underline="false" @click="tab = 'password'">密码登录</el-link>
            <el-link v-if="showRegister" type="primary" :underline="false" @click="goRegister">注册用户</el-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { User, Lock, Picture, Iphone } from '@element-plus/icons-vue';
import { useAuthFlow, validateForm } from '@/views/auth/composables/useAuthFlow';
import { resolveClientId, persistClientId } from '@/utils/client';
import type { ClientLayoutConfig } from '@/views/auth/types';

interface Props {
  config: ClientLayoutConfig;
  clientId: string;
}
const props = defineProps<Props>();

const authFlow = useAuthFlow();

const config = computed<ClientLayoutConfig>(() => props.config || {});
const loginCfg = computed<any>(() => (config.value as any).login || {});
const title = computed<string>(() => loginCfg.value.title || '欢迎登录');
const welcomeText = computed<string>(() => loginCfg.value.welcomeText || '欢迎登录！');
const leftImage = computed<string>(() => loginCfg.value.leftImage || '');
const showRememberMe = computed<boolean>(() => loginCfg.value.showRememberMe !== false);
const showForgetPwd = computed<boolean>(() => loginCfg.value.showForgetPwd !== false);
const showRegister = computed<boolean>(() => loginCfg.value.showRegister !== false);

const pageStyle = computed<Record<string, string>>(() => {
  const bg = loginCfg.value.backgroundImage;
  return bg ? { backgroundImage: `url('${bg}')`, backgroundSize: 'cover', backgroundPosition: 'center' } : {};
});

const tab = ref<'password' | 'phone'>(loginCfg.value.defaultTab === 'sms' ? 'phone' : 'password');

const { loading, captchaEnabled, captchaImg, captchaUuid, loadCaptcha, loginByPassword, sendLoginSmsCodeByPhone, loginByPhoneCode, handlePostLoginRedirect, pushAuthRoute, restoreRemembered } = authFlow;

const pwdForm = reactive({ tenantId: '000000', username: '', password: '', rememberMe: false, code: '', uuid: '' });
const phoneForm = reactive({ tenantId: '000000', username: '', code: '' });

const pwdRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'change' }]
};
const phoneRules = {
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3456789]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'change' }]
};

const pwdRef = ref();
const phoneRef = ref();

const codeSending = ref(false);
const codeBtnText = ref('获取验证码');
let codeCount = 0;
let codeTimer: ReturnType<typeof setInterval> | null = null;

const refreshCaptcha = async () => {
  await loadCaptcha();
};

const onPwdLogin = async () => {
  const valid = await validateForm(pwdRef);
  if (!valid) return;
  // 同步图形验证码 uuid（从 loadCaptcha 获取）
  pwdForm.uuid = captchaUuid.value || '';
  persistClientId(props.clientId || resolveClientId());
  await loginByPassword({
    tenantId: pwdForm.tenantId,
    username: pwdForm.username,
    password: pwdForm.password,
    rememberMe: showRememberMe.value && pwdForm.rememberMe,
    code: pwdForm.code,
    uuid: pwdForm.uuid
  });
};

const onPhoneLogin = async () => {
  const valid = await validateForm(phoneRef);
  if (!valid) return;
  await loginByPhoneCode(phoneForm.username, phoneForm.code, phoneForm.tenantId);
};

const onSendCode = async () => {
  if (!phoneForm.username) {
    ElMessage.warning('请输入手机号');
    return;
  }
  if (!/^1[3456789]\d{9}$/.test(phoneForm.username)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  codeSending.value = true;
  try {
    const ok = await sendLoginSmsCodeByPhone(phoneForm.username, phoneForm.tenantId);
    if (ok) {
      ElMessage.success('验证码已发送');
      startCodeCountdown();
    } else {
      ElMessage.error('验证码发送失败');
    }
  } finally {
    codeSending.value = false;
  }
};

const startCodeCountdown = () => {
  if (codeTimer) {
    clearInterval(codeTimer);
    codeTimer = null;
  }
  codeCount = 60;
  codeBtnText.value = `${codeCount}秒后重发`;
  codeTimer = setInterval(() => {
    codeCount--;
    if (codeCount > 0) {
      codeBtnText.value = `${codeCount}秒后重发`;
    } else {
      codeBtnText.value = '获取验证码';
      if (codeTimer) {
        clearInterval(codeTimer);
        codeTimer = null;
      }
    }
  }, 1000);
};

const goRegister = () => {
  pushAuthRoute('/register');
};
const goResetPwd = () => {
  pushAuthRoute('/resetPwd');
};

onMounted(async () => {
  restoreRemembered(pwdForm as any);
  if (captchaEnabled.value) await loadCaptcha();
});

onUnmounted(() => {
  // 清理短信倒计时定时器
  if (codeTimer) {
    clearInterval(codeTimer);
    codeTimer = null;
  }
});
</script>

<style lang="scss" scoped>
.lyt-login {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background-color: #e8f1fb;
  background-image: radial-gradient(circle at 12% 18%, rgba(36, 104, 242, 0.08) 0, transparent 34%),
    radial-gradient(circle at 88% 82%, rgba(36, 104, 242, 0.08) 0, transparent 34%);
}
.lyt-login-title {
  margin: 0 0 28px 0;
  font-size: 26px;
  font-weight: 700;
  color: #2468f2;
  text-align: center;
  letter-spacing: 1px;
}
.lyt-login-card {
  display: flex;
  width: 780px;
  max-width: 100%;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(36, 104, 242, 0.18);
}
.lyt-login-left {
  flex: 0 0 46%;
  background: linear-gradient(150deg, #2f7bf6 0%, #2468f2 55%, #1d5fe0 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  min-height: 420px;
  img {
    max-width: 100%;
    max-height: 340px;
    object-fit: contain;
  }
  &-placeholder {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    svg {
      width: 78%;
      height: auto;
    }
  }
}
.lyt-login-right {
  flex: 1;
  padding: 44px 40px 32px;
}
.lyt-login-welcome {
  margin: 0 0 28px 0;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  text-align: left;
}
.lyt-login-captcha-row {
  display: flex;
  gap: 10px;
  .el-input { flex: 1; }
  .el-button { flex: 0 0 auto; min-width: 110px; }
}
.lyt-login-captcha-img {
  height: 40px;
  width: 110px;
  cursor: pointer;
  border-radius: 6px;
}
.lyt-login-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 0 18px 0;
  :deep(.el-checkbox__label) {
    font-size: 13px;
    color: #606266;
  }
}
.lyt-login-submit {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 6px;
  letter-spacing: 6px;
}
.lyt-login-bottom {
  margin-top: 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  :deep(.el-link) { font-size: 13px; }
}
</style>
