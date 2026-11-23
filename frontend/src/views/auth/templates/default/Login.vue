<template>
  <AuthLayout :config="config" mode="login" :show-header="false" :show-footer="false" :show-left-image="true">
    <h3 class="default-login-title">{{ loginTitle }}</h3>

    <!-- Tab 切换：账号/短信（按 config.login.showSmsTab 控制） -->
    <div v-if="showSmsTab" class="default-login-tabs">
      <div
        :class="['default-login-tab', { active: activeTab === 'account' }]"
        @click="activeTab = 'account'"
      >
        账号密码
      </div>
      <div
        :class="['default-login-tab', { active: activeTab === 'sms' }]"
        @click="activeTab = 'sms'"
      >
        短信登录
      </div>
    </div>

    <!-- 账号密码登录 -->
    <el-form
      v-if="activeTab === 'account'"
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      @submit.prevent
    >
      <el-form-item v-if="showTenant" prop="tenantId">
        <el-select v-model="form.tenantId" placeholder="选择租户" style="width: 100%">
          <el-option
            v-for="t in tenantList"
            :key="t.tenantId"
            :label="t.companyName"
            :value="t.tenantId"
          />
        </el-select>
      </el-form-item>
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="off">
          <template #prefix><el-icon><User /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          show-password
          autocomplete="off"
          @keyup.enter="handleLogin"
        >
          <template #prefix><el-icon><Lock /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item v-if="captchaEnabled" prop="code">
        <div class="default-login-captcha">
          <el-input v-model="form.code" placeholder="请输入验证码" maxlength="6" autocomplete="off">
            <template #prefix><el-icon><Picture /></el-icon></template>
          </el-input>
          <img v-if="captchaImg" :src="captchaImg" class="default-login-captcha-img" @click="refreshCaptcha" />
        </div>
      </el-form-item>
      <div v-if="showRememberMe" class="default-login-extra">
        <el-checkbox v-model="form.rememberMe">记住密码</el-checkbox>
        <el-link v-if="showForgetPwd" type="primary" :underline="false" @click="goResetPwd">忘记密码？</el-link>
      </div>
      <el-button type="primary" :loading="loading" style="width: 100%" size="large" @click="handleLogin">
        登录
      </el-button>
      <div v-if="showRegister" class="default-login-bottom">
        <el-link type="primary" :underline="false" @click="goRegister">立即注册</el-link>
      </div>
    </el-form>

    <!-- 短信登录 -->
    <el-form v-else ref="smsFormRef" :model="smsForm" :rules="smsRules" size="large" @submit.prevent>
      <el-form-item v-if="showTenant" prop="tenantId">
        <el-select v-model="smsForm.tenantId" placeholder="选择租户" style="width: 100%">
          <el-option
            v-for="t in tenantList"
            :key="t.tenantId"
            :label="t.companyName"
            :value="t.tenantId"
          />
        </el-select>
      </el-form-item>
      <el-form-item prop="username">
        <el-input v-model="smsForm.username" placeholder="请输入手机号" maxlength="11" autocomplete="off">
          <template #prefix><el-icon><Iphone /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code">
        <div class="default-login-captcha">
          <el-input v-model="smsForm.code" placeholder="请输入验证码" maxlength="6" autocomplete="off">
            <template #prefix><el-icon><Picture /></el-icon></template>
          </el-input>
          <el-button type="primary" plain :disabled="codeSending" @click="onSendSmsCode">{{ codeBtnText }}</el-button>
        </div>
      </el-form-item>
      <el-button type="primary" :loading="loading" style="width: 100%" size="large" @click="handleSmsLogin">
        登录
      </el-button>
    </el-form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { User, Lock, Picture, Iphone } from '@element-plus/icons-vue';
import AuthLayout from '@/views/auth/AuthLayout.vue';
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
const showSmsTab = computed<boolean>(() => loginCfg.value.showSmsTab !== false);
const showTenant = computed<boolean>(() => loginCfg.value.showTenant !== false);
const showRememberMe = computed<boolean>(() => loginCfg.value.showRememberMe !== false);
const showForgetPwd = computed<boolean>(() => loginCfg.value.showForgetPwd !== false);
const showRegister = computed<boolean>(() => loginCfg.value.showRegister !== false);
const loginTitle = computed<string>(() => loginCfg.value.title || '欢迎登录');

const activeTab = ref<'account' | 'sms'>(
  loginCfg.value.defaultTab === 'sms' ? 'sms' : 'account'
);

const {
  loading,
  captchaEnabled,
  captchaImg,
  captchaUuid,
  tenantList,
  loadTenantList,
  loadCaptcha,
  restoreRemembered,
  loginByPassword,
  sendLoginSmsCodeByPhone,
  loginByPhoneCode,
  handlePostLoginRedirect,
  pushAuthRoute
} = authFlow;

const codeSending = ref(false);
const codeBtnText = ref('获取验证码');
let codeCount = 0;
let codeTimer: ReturnType<typeof setInterval> | null = null;

const form = reactive({
  tenantId: '000000',
  username: '',
  password: '',
  rememberMe: false,
  code: '',
  uuid: ''
});
const smsForm = reactive({
  tenantId: '000000',
  username: '',
  code: ''
});

const rules = {
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'change' }]
};
const smsRules = {
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3456789]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'change' }]
};

const formRef = ref();
const smsFormRef = ref();

const handleLogin = async () => {
  const valid = await validateForm(formRef);
  if (!valid) return;
  form.uuid = captchaUuid.value || '';
  persistClientId(props.clientId || resolveClientId());
  await loginByPassword({
    tenantId: form.tenantId,
    username: form.username,
    password: form.password,
    rememberMe: form.rememberMe,
    code: form.code,
    uuid: form.uuid
  });
};

const handleSmsLogin = async () => {
  const valid = await validateForm(smsFormRef);
  if (!valid) return;
  // 手机号 + 验证码登录（免密码，/auth/loginByPhone）
  await loginByPhoneCode(smsForm.username, smsForm.code, smsForm.tenantId);
};

const onSendSmsCode = async () => {
  if (!smsForm.username) {
    ElMessage.warning('请输入手机号');
    return;
  }
  if (!/^1[3456789]\d{9}$/.test(smsForm.username)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  codeSending.value = true;
  try {
    const ok = await sendLoginSmsCodeByPhone(smsForm.username, smsForm.tenantId);
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

const refreshCaptcha = async () => {
  await loadCaptcha();
};

const goRegister = () => {
  pushAuthRoute('/register');
};

const goResetPwd = () => {
  pushAuthRoute('/resetPwd');
};

onMounted(async () => {
  await loadTenantList();
  if (tenantList.value?.length) form.tenantId = tenantList.value[0].tenantId;
  smsForm.tenantId = form.tenantId;
  restoreRemembered(form as any);
  if (captchaEnabled.value) await loadCaptcha();
});

onUnmounted(() => {
  // 清理短信倒计时定时器，避免组件卸载后仍在跑
  if (codeTimer) {
    clearInterval(codeTimer);
    codeTimer = null;
  }
});
</script>

<style lang="scss" scoped>
.default-login-title {
  text-align: center;
  margin: 0 0 24px 0;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}
.default-login-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 20px;
  .default-login-tab {
    padding: 8px 0;
    cursor: pointer;
    color: #606266;
    font-size: 14px;
    border-bottom: 2px solid transparent;
    &.active {
      color: var(--el-color-primary);
      border-bottom-color: var(--el-color-primary);
      font-weight: 500;
    }
  }
}
.default-login-captcha {
  display: flex;
  gap: 8px;
  width: 100%;
  .el-input { flex: 1; }
  .el-button { flex: 0 0 auto; min-width: 110px; }
  &-img {
    height: 40px;
    width: 110px;
    cursor: pointer;
    border-radius: 4px;
  }
}
.default-login-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 0 16px 0;
}
.default-login-bottom {
  margin-top: 12px;
  text-align: center;
}
</style>
