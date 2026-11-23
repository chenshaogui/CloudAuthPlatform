<template>
  <div v-if="!ready" class="auth-page-loading">
    <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    <p>正在加载页面…</p>
  </div>

  <!-- 老界面（后台未配置 template 时回退）：原 login.vue / register.vue，不传 props -->
  <component v-else-if="useLegacy" :is="templateComponent" />

  <component
    v-else
    :is="templateComponent"
    :config="layoutConfig"
    :client-id="clientId"
  />
</template>

<script setup lang="ts">
/**
 * 鉴权页面外壳（模板分发器）
 *
 * <p>由路由 /login、/register、/resetPwd 直接指向本组件，
 * 本组件根据 clientId 加载 layout_config 并分发到对应模板渲染。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * { path: '/login', component: () => import('@/views/auth/AuthPage.vue'), meta: { mode: 'login' } }
 * }</pre>
 *
 * <p>mode 通过 query 参数 ?mode=login/register/resetPwd 控制，
 * 缺省值由路由 meta.mode 指定；都没设置则用 'login'。</p>
 */
import { ref, computed, watch, markRaw } from 'vue';
import { useRoute } from 'vue-router';
import { Loading } from '@element-plus/icons-vue';
import { useClientConfigStore } from '@/store/modules/clientConfig';
import { getTemplate } from '@/views/auth';
import LegacyLogin from '@/views/login.vue';
import LegacyRegister from '@/views/register.vue';
import { resolveLoginClientId } from '@/utils/client';
import type { ClientLayoutConfig } from '@/views/auth/types';

const route = useRoute();
const store = useClientConfigStore();

// 只认 URL 里的 ?client=，不回退 localStorage（避免残留 client 劫持模板选择）
const clientId = ref<string>(resolveLoginClientId(route.query as any));

// 模式：login / register / resetPwd
const mode = computed<'login' | 'register' | 'resetPwd'>(() => {
  const m = (route.query?.mode as string) || (route.meta?.mode as string) || 'login';
  if (m === 'register' || m === 'resetPwd') return m;
  return 'login';
});

// 模板组件（先按 default 占位，等 layoutConfig 加载完再切到正确模板）
const ready = ref<boolean>(false);
// 是否使用老界面（后台未配置 template 时回退到原 login.vue / register.vue）
const useLegacy = ref<boolean>(false);
const layoutConfig = ref<ClientLayoutConfig>({});
const templateComponent = ref<any>(null);

const loadAndRender = async () => {
  // 1) 加载 client 配置
  const cfg = await store.ensureLoaded(clientId.value);
  layoutConfig.value = cfg?.layoutConfigParsed || {};
  const tplName = (layoutConfig.value as any)?.template;
  // 2) 后台未配置模板且非重置密码页 → 回退老界面（老页面自带完整样式与逻辑）
  if (!tplName && mode.value !== 'resetPwd') {
    useLegacy.value = true;
    templateComponent.value = markRaw(mode.value === 'register' ? LegacyRegister : LegacyLogin);
    ready.value = true;
    return;
  }
  // 3) 选择模板（markRaw 避免组件对象被 reactive 包装，Vue 3 渲染组件的推荐做法）
  useLegacy.value = false;
  const tpl = getTemplate(tplName);
  templateComponent.value = markRaw(tpl[mode.value]);
  ready.value = true;
};

// 监听 clientId / mode 变化重新加载
watch(
  [clientId, mode],
  () => {
    ready.value = false;
    loadAndRender();
  },
  { immediate: true }
);

// 监听 route.query.client 变化
watch(
  () => route.query?.client,
  (v) => {
    if (v) clientId.value = String(v);
  }
);
</script>

<style lang="scss" scoped>
.auth-page-loading {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #606266;
  font-size: 14px;

  p {
    margin: 0;
  }
}
</style>
