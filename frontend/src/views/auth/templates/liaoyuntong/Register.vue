<template>
  <div class="lyt-register" :style="pageStyle">
    <h1 class="lyt-register-title">{{ title }}</h1>

    <div class="lyt-register-card">
      <!-- 左侧大插画 -->
      <div class="lyt-register-left">
        <img v-if="leftImage" :src="leftImage" alt="" />
        <AuthIllustration v-else class="lyt-register-left-illustration" />
      </div>

      <div class="lyt-register-right">
        <h2 class="lyt-register-subtitle">{{ formTitle }}</h2>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="off" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入6-20位登录密码" show-password autocomplete="off" />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入登录密码" show-password autocomplete="off" />
          </el-form-item>
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" autocomplete="off" />
          </el-form-item>
          <el-form-item prop="code">
            <div class="lyt-register-code-row">
              <el-input v-model="form.code" placeholder="请输入短信验证码" maxlength="6" autocomplete="off" />
              <el-button type="primary" :disabled="codeSending" @click="onSendCode">{{ codeBtnText }}</el-button>
            </div>
          </el-form-item>

          <el-checkbox v-model="agreed" class="lyt-register-agreement">
            <span>勾选同意</span>
            <el-link type="primary" :href="agreementUrl || undefined" target="_blank" :underline="false">《用户服务协议》</el-link>
            <span>、</span>
            <el-link type="primary" :href="agreementUrl || undefined" target="_blank" :underline="false">《隐私政策》</el-link>
          </el-checkbox>

          <el-button type="primary" :loading="loading" class="lyt-register-submit" size="large" @click="handleRegister">
            {{ submitText || '注册' }}
          </el-button>

          <div class="lyt-register-bottom">
            <el-link type="primary" :underline="false" @click="goLogin">密码登录/手机号登录</el-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { useAuthFlow, validateForm } from '@/views/auth/composables/useAuthFlow';
import { getCodeApi } from '@/api/login';
import AuthIllustration from './components/AuthIllustration.vue';
import type { ClientLayoutConfig } from '@/views/auth/types';

interface Props {
  config: ClientLayoutConfig;
  clientId: string;
}
const props = defineProps<Props>();

const authFlow = useAuthFlow();

const config = computed<ClientLayoutConfig>(() => props.config || {});
const regCfg = computed<any>(() => (config.value as any).register || {});

/** 页面顶部大标题（平台名），config.register.platformTitle 可覆盖 */
const title = computed<string>(() => regCfg.value.platformTitle || '欢迎注册');
/** 表单区标题（默认"用户注册"） */
const formTitle = computed<string>(() => regCfg.value.title || '用户注册');
const leftImage = computed<string>(() => regCfg.value.logoUrl || '');
const agreementUrl = computed<string>(() => regCfg.value.agreementUrl || '');
const submitText = computed<string>(() => regCfg.value.submitText || '');

const pageStyle = computed<Record<string, string>>(() => {
  const bg = regCfg.value.backgroundImage;
  return bg ? { backgroundImage: `url('${bg}')`, backgroundSize: 'cover', backgroundPosition: 'center' } : {};
});

const { loading, register, pushAuthRoute } = authFlow;

const agreed = ref(false);
const codeSending = ref(false);
const codeBtnText = ref('获取验证码');
let codeCount = 0;
let codeTimer: ReturnType<typeof setInterval> | null = null;

const form = reactive({
  tenantId: '000000',
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  code: '',
  // 兼容后端 RegisterBody.userType
  userType: 'sys_user'
});

const rules = computed(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度 2-20', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_: any, v: string, cb: any) => (v === form.password ? cb() : cb(new Error('两次输入密码不一致'))),
      trigger: 'blur'
    }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3456789]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入短信验证码', trigger: 'change' }]
}));

const formRef = ref();

const handleRegister = async () => {
  if (!agreed.value) {
    ElMessage.warning('请先勾选同意《用户服务协议》和《隐私政策》');
    return;
  }
  const valid = await validateForm(formRef);
  if (!valid) return;
  // 手机号写入后端 phonenumber
  const submitData: any = {
    tenantId: form.tenantId,
    username: form.username,
    password: form.password,
    confirmPassword: form.confirmPassword,
    code: form.code,
    userType: form.userType,
    phonenumber: form.phone
  };
  await register(submitData);
};

const onSendCode = async () => {
  if (!form.phone) {
    ElMessage.warning('请输入手机号');
    return;
  }
  if (!/^1[3456789]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  codeSending.value = true;
  try {
    const res: any = await getCodeApi(form.phone);
    if (res.code === 200) {
      ElMessage.success('验证码已发送');
      startCodeCountdown();
    } else {
      ElMessage.error(res.msg || '验证码发送失败');
    }
  } catch (e) {
    ElMessage.error('验证码发送失败');
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
  // 清理短信倒计时定时器
  if (codeTimer) {
    clearInterval(codeTimer);
    codeTimer = null;
  }
});
</script>

<style lang="scss" scoped>
.lyt-register {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px;
  background-color: #eaf1ff;
}
.lyt-register-title {
  margin: 0 0 16px 0;
  font-size: 22px;
  font-weight: 600;
  color: #2468f2;
  text-align: center;
}
.lyt-register-card {
  display: flex;
  width: 880px;
  max-width: 100%;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(36, 104, 242, 0.15);
}
.lyt-register-left {
  flex: 0 0 50%;
  background: linear-gradient(135deg, #2468f2, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 20px;
  min-height: 480px;
  img {
    max-width: 100%;
    max-height: 400px;
    object-fit: contain;
  }
  &-illustration {
    width: 82%;
    height: auto;
  }
}
.lyt-register-right {
  flex: 1;
  padding: 30px 32px;
}
.lyt-register-subtitle {
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.lyt-register-submit {
  width: 100%;
  letter-spacing: 8px;
}
.lyt-register-code-row {
  display: flex;
  gap: 8px;
  .el-input { flex: 1; }
  .el-button { flex: 0 0 auto; min-width: 110px; }
}
.lyt-register-agreement {
  margin: 8px 0 16px 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.lyt-register-bottom {
  margin-top: 12px;
  text-align: center;
}
</style>
