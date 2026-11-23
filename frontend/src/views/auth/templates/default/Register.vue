<template>
  <AuthLayout :config="config" mode="register" :show-header="true" :show-footer="false">
    <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
      <el-form-item v-if="showTenant && tenantEnabled" prop="tenantId">
        <el-select v-model="form.tenantId" placeholder="选择租户" style="width: 100%">
          <el-option
            v-for="t in tenantList"
            :key="t.tenantId"
            :label="t.companyName"
            :value="t.tenantId"
          />
        </el-select>
      </el-form-item>

      <!-- 角色单选（按 registerCfg.roles 渲染） -->
      <el-form-item v-if="hasRoles" prop="userSourceType">
        <el-radio-group v-model="form.userSourceType">
          <el-radio v-for="r in roles" :key="r.value" :value="r.value">{{ r.label }}</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="off">
          <template #prefix><el-icon><User /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="请输入6-20位密码" show-password autocomplete="off">
          <template #prefix><el-icon><Lock /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" show-password autocomplete="off">
          <template #prefix><el-icon><Lock /></el-icon></template>
        </el-input>
      </el-form-item>

      <el-form-item v-if="captchaEnabled" prop="code">
        <div class="default-reg-captcha">
          <el-input v-model="form.code" placeholder="请输入验证码" maxlength="6" autocomplete="off">
            <template #prefix><el-icon><Picture /></el-icon></template>
          </el-input>
          <img v-if="captchaImg" :src="captchaImg" class="default-reg-captcha-img" @click="refreshCaptcha" />
        </div>
      </el-form-item>

      <el-button type="primary" :loading="loading" style="width: 100%" size="large" @click="handleRegister">
        {{ submitText || '立即注册' }}
      </el-button>

      <div class="default-reg-bottom">
        <el-link type="primary" :underline="false" @click="goLogin">已有账号？去登录</el-link>
      </div>
    </el-form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { User, Lock, Picture } from '@element-plus/icons-vue';
import AuthLayout from '@/views/auth/AuthLayout.vue';
import { useAuthFlow, validateForm } from '@/views/auth/composables/useAuthFlow';
import type { ClientLayoutConfig } from '@/views/auth/types';

interface Props {
  config: ClientLayoutConfig;
  clientId: string;
}
const props = defineProps<Props>();

const authFlow = useAuthFlow();

const config = computed<ClientLayoutConfig>(() => props.config || {});
const regCfg = computed<any>(() => (config.value as any).register || {});
const roles = computed<Array<{ label: string; value: string; default?: boolean }>>(() => regCfg.value.roles || []);
const hasRoles = computed<boolean>(() => roles.value.length > 0);
const showTenant = computed<boolean>(() => true);
const submitText = computed<string>(() => regCfg.value.submitText || '');

const {
  loading,
  captchaEnabled,
  captchaImg,
  captchaUuid,
  tenantEnabled,
  tenantList,
  loadTenantList,
  loadCaptcha,
  register,
  pushAuthRoute
} = authFlow;

const form = reactive({
  tenantId: '000000',
  username: '',
  password: '',
  confirmPassword: '',
  code: '',
  uuid: '',
  // 兼容后端 RegisterBody.userType（原 views/register.vue 用 'sys_user'）
  userType: 'sys_user',
  // 角色类型：当 config.register.roles 配置后由用户选择，未配置时不提交
  userSourceType: ''
});

const rules = computed(() => ({
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
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
  code: [{ required: true, message: '请输入验证码', trigger: 'change' }],
  userSourceType: hasRoles.value ? [{ required: true, message: '请选择用户类型', trigger: 'change' }] : []
}));

const formRef = ref();

const handleRegister = async () => {
  const valid = await validateForm(formRef);
  if (!valid) return;
  form.uuid = captchaUuid.value || '';
  // 提交数据：未配置角色时不带 userSourceType，避免后端写入空串
  const submitData: any = { ...form };
  if (!submitData.userSourceType) delete submitData.userSourceType;
  await register(submitData);
};

const refreshCaptcha = async () => {
  await loadCaptcha();
};

const goLogin = () => {
  pushAuthRoute('/login');
};

onMounted(async () => {
  await loadTenantList();
  if (tenantList.value?.length) form.tenantId = tenantList.value[0].tenantId;
  if (hasRoles.value) {
    const def = roles.value.find((r) => r.default) || roles.value[0];
    form.userSourceType = def.value;
  }
  if (captchaEnabled.value) await loadCaptcha();
});
</script>

<style lang="scss" scoped>
.default-reg-captcha {
  display: flex;
  gap: 8px;
  width: 100%;
  .el-input { flex: 1; }
  &-img {
    height: 40px;
    width: 110px;
    cursor: pointer;
    border-radius: 4px;
  }
}
.default-reg-bottom {
  margin-top: 12px;
  text-align: center;
}
</style>
