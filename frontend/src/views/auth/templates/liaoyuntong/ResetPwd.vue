<template>
  <!-- 辽运通风格重置密码：三步流程（验证手机号 → 重置密码 → 完成） -->
  <div class="lyt-reset" :style="pageStyle">
    <h1 class="lyt-reset-title">{{ pageTitle }}</h1>

    <div class="lyt-reset-card">
      <div class="lyt-reset-left">
        <img v-if="leftImage" :src="leftImage" alt="" />
        <AuthIllustration v-else class="lyt-reset-left-illustration" />
      </div>

      <div class="lyt-reset-right">
        <h2 class="lyt-reset-subtitle">{{ formTitle }}</h2>

        <!-- 步骤指示器 -->
        <div v-if="currentStep < 2" class="lyt-reset-steps">
          <template v-for="(s, i) in stepLabels" :key="i">
            <div class="lyt-reset-step" :class="{ active: i === currentStep, done: i < currentStep }">
              <span class="lyt-reset-step-dot">
                <el-icon v-if="i < currentStep"><Check /></el-icon>
                <span v-else>{{ i + 1 }}</span>
              </span>
              <span class="lyt-reset-step-label">{{ s }}</span>
            </div>
            <span v-if="i < stepLabels.length - 1" class="lyt-reset-step-line" :class="{ done: i < currentStep }" />
          </template>
        </div>

        <!-- Step 1：验证手机号 -->
        <el-form v-if="currentStep === 0" ref="step1Ref" :model="form" :rules="step1Rules" size="large" @submit.prevent>
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" autocomplete="off" />
          </el-form-item>
          <el-form-item prop="code">
            <div class="lyt-reset-code-row">
              <el-input v-model="form.code" placeholder="请输入短信验证码" maxlength="6" autocomplete="off" />
              <el-button type="primary" :disabled="codeSending" @click="onSendCode">{{ codeBtnText }}</el-button>
            </div>
          </el-form-item>
          <el-button type="primary" class="lyt-reset-submit" size="large" @click="onNext">下一步</el-button>
          <div class="lyt-reset-back">
            <el-icon class="lyt-reset-back-arrow"><ArrowLeft /></el-icon>
            <el-link type="primary" :underline="false" @click="goLogin">返回登录</el-link>
          </div>
        </el-form>

        <!-- Step 2：重置密码 -->
        <el-form v-else-if="currentStep === 1" ref="step2Ref" :model="form" :rules="step2Rules" size="large" @submit.prevent>
          <el-form-item prop="newPassword">
            <el-input v-model="form.newPassword" type="password" placeholder="请输入6-20位密码" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" show-password autocomplete="new-password" />
          </el-form-item>
          <el-button type="primary" class="lyt-reset-submit" :loading="submitting" size="large" @click="onSubmit">确 定</el-button>
        </el-form>

        <!-- Step 3：重置成功 -->
        <div v-else class="lyt-reset-success">
          <span class="lyt-reset-success-icon">
            <el-icon :size="26"><Check /></el-icon>
          </span>
          <p class="lyt-reset-success-title">恭喜您，密码已重新设置！</p>
          <p class="lyt-reset-success-desc">
            为了您的账号安全，请妥善保管密码！<br />页面将在 <span class="lyt-reset-success-count">{{ countdown }}</span> s后自动跳转
          </p>
          <el-button type="primary" class="lyt-reset-submit" size="large" @click="goLogin">立即登录</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, ArrowLeft } from '@element-plus/icons-vue';
import { useAuthFlow, validateForm } from '@/views/auth/composables/useAuthFlow';
import AuthIllustration from './components/AuthIllustration.vue';
import type { ClientLayoutConfig } from '@/views/auth/types';

interface Props {
  config: ClientLayoutConfig;
  clientId: string;
}
const props = defineProps<Props>();

const authFlow = useAuthFlow();

const config = computed<ClientLayoutConfig>(() => props.config || {});
const resetCfg = computed<any>(() => (config.value as any).resetPwd || {});

/** 页面顶部大标题（平台名），config.resetPwd.platformTitle 可覆盖 */
const pageTitle = computed<string>(() => resetCfg.value.platformTitle || '重置密码');
/** 表单区标题（默认"重置密码"） */
const formTitle = computed<string>(() => resetCfg.value.title || '重置密码');
const leftImage = computed<string>(() => resetCfg.value.logoUrl || '');
const stepLabels = computed<string[]>(() => (resetCfg.value.steps?.length ? resetCfg.value.steps : ['验证手机号', '重置密码']));

const pageStyle = computed<Record<string, string>>(() => {
  const bg = resetCfg.value.backgroundImage;
  return bg ? { backgroundImage: `url('${bg}')`, backgroundSize: 'cover', backgroundPosition: 'center' } : {};
});

const { submitResetPwd, sendLoginSmsCodeByPhone, pushAuthRoute } = authFlow;

const currentStep = ref(0);
const submitting = ref(false);
const codeSending = ref(false);
const countdown = ref(10);
let redirectTimer: ReturnType<typeof setInterval> | null = null;
const codeBtnText = ref('获取验证码');
let codeCount = 0;
let codeTimer: ReturnType<typeof setInterval> | null = null;

const form = reactive({
  tenantId: '000000',
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
});

const phoneRule = [
  { required: true, message: '请输入手机号', trigger: 'blur' },
  { pattern: /^1[3456789]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
];

const step1Rules = {
  phone: phoneRule,
  code: [{ required: true, message: '请输入短信验证码', trigger: 'change' }]
};

const step2Rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_: any, v: string, cb: any) => (v === form.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur'
    }
  ]
};

const step1Ref = ref();
const step2Ref = ref();

/** Step1 下一步：仅校验手机号与验证码已填写，验证码有效性在最终提交时由后端校验 */
const onNext = async () => {
  const valid = await validateForm(step1Ref);
  if (!valid) return;
  currentStep.value = 1;
};

const onSubmit = async () => {
  const valid = await validateForm(step2Ref);
  if (!valid) return;
  submitting.value = true;
  try {
    const ok = await submitResetPwd({
      userName: form.phone,
      code: form.code,
      newPassword: form.newPassword,
      tenantId: form.tenantId,
      phonenumber: form.phone
    });
    if (ok) {
      currentStep.value = 2;
      startRedirectCountdown();
    }
  } finally {
    submitting.value = false;
  }
};

/** 重置成功后 10s 自动跳转登录页 */
const startRedirectCountdown = () => {
  if (redirectTimer) {
    clearInterval(redirectTimer);
    redirectTimer = null;
  }
  countdown.value = 10;
  redirectTimer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      if (redirectTimer) {
        clearInterval(redirectTimer);
        redirectTimer = null;
      }
      goLogin();
    }
  }, 1000);
};

const onSendCode = async () => {
  if (!/^1[3456789]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  codeSending.value = true;
  try {
    const ok = await sendLoginSmsCodeByPhone(form.phone, form.tenantId);
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

const goLogin = () => {
  pushAuthRoute('/login');
};

onUnmounted(() => {
  // 清理短信倒计时 / 成功页自动跳转定时器
  if (codeTimer) {
    clearInterval(codeTimer);
    codeTimer = null;
  }
  if (redirectTimer) {
    clearInterval(redirectTimer);
    redirectTimer = null;
  }
});
</script>

<style lang="scss" scoped>
.lyt-reset {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  background-color: #eaf1ff;
}
.lyt-reset-title {
  margin: 0 0 24px 0;
  font-size: 24px;
  font-weight: 600;
  color: #2468f2;
  text-align: center;
}
.lyt-reset-card {
  display: flex;
  width: 720px;
  max-width: 100%;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(36, 104, 242, 0.15);
}
.lyt-reset-left {
  flex: 0 0 50%;
  background: linear-gradient(135deg, #2468f2, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 20px;
  min-height: 420px;
  img {
    max-width: 100%;
    max-height: 360px;
    object-fit: contain;
  }
  &-illustration {
    width: 82%;
    height: auto;
  }
}
.lyt-reset-right {
  flex: 1;
  padding: 32px;
}
.lyt-reset-subtitle {
  margin: 0 0 24px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.lyt-reset-steps {
  display: flex;
  align-items: flex-start;
  margin-bottom: 28px;
}
.lyt-reset-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  &-dot {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    border: 1px solid #c0c8d4;
    color: #909399;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;
  }
  &-label {
    font-size: 13px;
    color: #909399;
    white-space: nowrap;
  }
  &.active {
    .lyt-reset-step-dot {
      border-color: #2468f2;
      background: #2468f2;
      color: #fff;
    }
    .lyt-reset-step-label {
      color: #2468f2;
      font-weight: 600;
    }
  }
  &.done {
    .lyt-reset-step-dot {
      border-color: #2468f2;
      color: #2468f2;
    }
    .lyt-reset-step-label {
      color: #2468f2;
    }
  }
}
.lyt-reset-step-line {
  flex: 1;
  height: 1px;
  background: #dcdfe6;
  margin: 12px 12px 0;
  &.done {
    background: #2468f2;
  }
}
.lyt-reset-code-row {
  display: flex;
  gap: 8px;
  width: 100%;
  .el-input {
    flex: 1;
  }
  .el-button {
    flex: 0 0 auto;
    min-width: 110px;
  }
}
.lyt-reset-submit {
  width: 100%;
  letter-spacing: 8px;
  text-indent: 8px;
}
.lyt-reset-back {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  &-arrow {
    color: #2468f2;
    font-size: 14px;
  }
}
.lyt-reset-success {
  text-align: center;
  &-icon {
    width: 48px;
    height: 48px;
    margin: 8px auto 16px;
    border-radius: 50%;
    border: 1px solid #2468f2;
    color: #2468f2;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  &-title {
    margin: 0 0 10px;
    font-size: 16px;
    font-weight: 600;
    color: #2468f2;
  }
  &-desc {
    margin: 0 0 24px;
    font-size: 13px;
    line-height: 1.8;
    color: #909399;
  }
  &-count {
    color: #2468f2;
    font-weight: 600;
  }
}
</style>
