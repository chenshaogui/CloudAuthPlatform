<template>
  <AuthLayout :config="config" mode="resetPwd" :show-header="true" :show-footer="false">
    <ResetPwdForm
      :mode="resetMode"
      :steps="resetSteps"
      :submit-text="resetSubmitText"
      :show-back-to-login="resetShowBack"
      :default-tenant-id="defaultTenantId"
    />
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import AuthLayout from '@/views/auth/AuthLayout.vue';
import ResetPwdForm from '@/views/auth/ResetPwdForm.vue';
import type { ClientLayoutConfig } from '@/views/auth/types';

interface Props {
  config: ClientLayoutConfig;
  clientId: string;
}
const props = defineProps<Props>();

const config = computed<ClientLayoutConfig>(() => props.config || {});
const resetCfg = computed<any>(() => (config.value as any).resetPwd || {});

const resetMode = computed<'steps' | 'form'>(() => (resetCfg.value.mode === 'steps' ? 'steps' : 'form'));
const resetSteps = computed<string[]>(() => resetCfg.value.steps || ['验证手机号', '重置密码']);
const resetSubmitText = computed<string>(() => resetCfg.value.submitText || '');
const resetShowBack = computed<boolean>(() => resetCfg.value.showBackToLogin !== false);
const defaultTenantId = '000000';
</script>
