<template>
  <div class="reset-pwd-form">
    <!-- 步骤条模式（mode=steps） -->
    <template v-if="mode === 'steps'">
      <el-steps :active="currentStep" align-center finish-status="success" class="reset-pwd-steps">
        <el-step v-for="(s, i) in stepLabels" :key="i" :title="s" />
      </el-steps>
      <div class="reset-pwd-step-content">
        <!-- Step 1: 验证手机号 + 旧密码 -->
        <template v-if="currentStep === 0">
          <el-form ref="step1Ref" :model="form" :rules="step1Rules" label-width="100px" label-position="top">
            <el-form-item prop="username" label="验证手机号">
              <el-input v-model="form.username" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
            <el-form-item prop="oldPassword" label="输入账号密码">
              <el-input v-model="form.oldPassword" type="password" placeholder="请输入当前账号密码" show-password />
            </el-form-item>
            <el-form-item prop="newPassword" label="设置新密码">
              <el-input v-model="form.newPassword" type="password" placeholder="请输入新密码" show-password />
            </el-form-item>
            <el-form-item prop="code" label="短信验证码">
              <div class="reset-pwd-code-row">
                <el-input v-model="form.code" placeholder="请输入验证码" maxlength="6" />
                <el-button type="primary" plain :disabled="codeSending" @click="onSendSmsCode">
                  {{ codeBtnText }}
                </el-button>
              </div>
            </el-form-item>
            <el-button type="primary" :loading="submitting" style="width: 100%" @click="onSubmit">
              {{ submitText || '下一步' }}
            </el-button>
          </el-form>
        </template>
        <!-- Step 2: 重置完成 -->
        <template v-else-if="currentStep === 1">
          <el-result icon="success" title="密码重置成功" sub-title="请使用新密码重新登录">
            <template #extra>
              <el-button type="primary" @click="onGoLogin">返回登录</el-button>
            </template>
          </el-result>
        </template>
      </div>
    </template>

    <!-- 单表单模式（mode=form） -->
    <el-form v-else ref="formRef" :model="form" :rules="formRules" label-width="100px" label-position="top">
      <el-form-item prop="username" label="手机号">
        <el-input v-model="form.username" placeholder="请输入手机号" maxlength="11" />
      </el-form-item>
      <el-form-item prop="oldPassword" label="旧密码">
        <el-input v-model="form.oldPassword" type="password" placeholder="请输入旧密码" show-password />
      </el-form-item>
      <el-form-item prop="newPassword" label="新密码">
        <el-input v-model="form.newPassword" type="password" placeholder="请输入新密码" show-password />
      </el-form-item>
      <el-form-item prop="confirmPassword" label="确认新密码">
        <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
      </el-form-item>
      <el-form-item prop="code" label="短信验证码">
        <div class="reset-pwd-code-row">
          <el-input v-model="form.code" placeholder="请输入验证码" maxlength="6" />
          <el-button type="primary" plain :disabled="codeSending" @click="onSendSmsCode">
            {{ codeBtnText }}
          </el-button>
        </div>
      </el-form-item>
      <el-button type="primary" :loading="submitting" style="width: 100%" @click="onSubmit">
        {{ submitText || '确认重置' }}
      </el-button>
    </el-form>

    <!-- 返回登录 -->
    <div v-if="showBackToLogin" class="reset-pwd-back">
      <el-link type="primary" :underline="false" @click="onGoLogin">返回登录</el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { useAuthFlow, validateForm } from '@/views/auth/composables/useAuthFlow';

interface Props {
  /** 模式：steps（步骤条） / form（单表单） */
  mode?: 'steps' | 'form';
  /** 步骤文案（仅 mode=steps） */
  steps?: string[];
  /** 提交按钮文案 */
  submitText?: string;
  /** 是否显示"返回登录"链接 */
  showBackToLogin?: boolean;
  /** 默认租户ID */
  defaultTenantId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'form',
  steps: () => ['验证手机号', '重置密码'],
  submitText: '',
  showBackToLogin: true,
  defaultTenantId: '000000'
});

const authFlow = useAuthFlow();

const currentStep = ref(0);
const submitting = ref(false);
const codeSending = ref(false);
const codeBtnText = ref('获取验证码');
const codeCount = ref(0);
let codeTimer: ReturnType<typeof setInterval> | null = null;

const stepLabels = computed<string[]>(() => props.steps && props.steps.length ? props.steps : ['验证手机号', '重置密码']);

const form = reactive({
  tenantId: props.defaultTenantId,
  username: '',
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
  code: ''
});

const step1Rules = {
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3456789]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'change' }]
};

const formRules = {
  ...step1Rules,
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_: any, value: string, cb: any) => {
        if (value !== form.newPassword) cb(new Error('两次输入的新密码不一致'));
        else cb();
      },
      trigger: 'blur'
    }
  ]
};

const step1Ref = ref();
const formRef = ref();

const onSendSmsCode = async () => {
  if (!form.username) {
    ElMessage.warning('请输入手机号');
    return;
  }
  if (!form.oldPassword) {
    ElMessage.warning('请输入旧密码');
    return;
  }
  if (!form.newPassword) {
    ElMessage.warning('请输入新密码');
    return;
  }
  if (form.newPassword === form.oldPassword) {
    ElMessage.warning('新密码不能与旧密码相同');
    return;
  }
  codeSending.value = true;
  try {
    const ok = await authFlow.sendResetSmsCode({
      username: form.username,
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
      tenantId: form.tenantId
    });
    if (ok) {
      ElMessage.success('验证码发送成功');
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
  codeCount.value = 60;
  codeBtnText.value = `${codeCount.value}秒后重发`;
  codeTimer = setInterval(() => {
    codeCount.value--;
    if (codeCount.value > 0) {
      codeBtnText.value = `${codeCount.value}秒后重发`;
    } else {
      codeBtnText.value = '获取验证码';
      if (codeTimer) {
        clearInterval(codeTimer);
        codeTimer = null;
      }
    }
  }, 1000);
};

const onSubmit = async () => {
  const targetRef = props.mode === 'steps' ? step1Ref : formRef;
  const valid = await validateForm(targetRef);
  if (!valid) return;
  submitting.value = true;
  try {
    const ok = await authFlow.submitResetPwd({
      userName: form.username,
      code: form.code,
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
      tenantId: form.tenantId
    });
    if (ok) {
      if (props.mode === 'steps') {
        currentStep.value = 1;
      } else {
        ElMessage.success('重置成功，请使用新密码登录');
        onGoLogin();
      }
    } else {
      ElMessage.error('重置失败');
    }
  } finally {
    submitting.value = false;
  }
};

const onGoLogin = () => {
  authFlow.pushAuthRoute('/login');
};

onUnmounted(() => {
  // 清理短信倒计时定时器，避免组件卸载后仍在跑
  if (codeTimer) {
    clearInterval(codeTimer);
    codeTimer = null;
  }
});
</script>

<style lang="scss" scoped>
.reset-pwd-form {
  width: 100%;

  &-steps {
    margin-bottom: 24px;
  }

  &-step-content {
    margin-top: 8px;
  }

  &-code-row {
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

  &-back {
    margin-top: 16px;
    text-align: center;
  }
}
</style>
