<template>
  <div class="sso-login-page">
    <!-- 模板加载中：占位 -->
    <div v-if="!ready" class="sso-login-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>正在加载登录页…</p>
    </div>

    <!-- 老界面（后台未配置 template 时回退）：views/ssologin.vue 作为纯登录表单使用 -->
    <!-- 已登录检测时：保留登录页背景，隐藏表单卡片（含记住的账号密码），"已登录授权"弹窗浮在其上 -->
    <div v-if="useLegacy" :class="{ 'is-checking-legacy': authChecking }">
      <component :is="templateComponent" />
    </div>

    <!-- 新模板：渲染模板的 Login 组件（已登录检测时整体模糊兜底，模板内部结构不可预知） -->
    <div v-else :class="{ 'is-checking-blur': authChecking }">
      <component :is="templateComponent" :config="layoutConfig" :client-id="clientId" />
    </div>

    <!-- 已登录提示对话框：确认授权 / 切换账号 / 进入授权中心（SSO 特有） -->
    <el-dialog
      v-model="authDialog.visible"
      title="系统提示"
      width="500px"
      append-to-body
      :show-close="false"
      :close-on-click-modal="false"
    >
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
/**
 * /ssologin 路由的页面：模板分发 + SSO 弹窗
 *
 * <p>职责：</p>
 * <ol>
 *   <li>按 URL ?client=xxx 加载 layout_config，选取模板渲染登录表单</li>
 *   <li>若用户已登录且当前是第三方授权请求（带 client/redirect），弹"已登录"对话框，
 *       由用户选择确认授权 / 切换账号 / 进入授权中心</li>
 *   <li>URL 带 auth_mode=center/silent 时跳过弹窗：center 进授权中心，silent 免确认直接授权回跳客户端；
 *       未登录时两种模式都停留登录页；不带该参数时上述弹窗逻辑完全不变</li>
 *   <li>无 client/redirect 时视为直接访问授权中心，permission.ts 会处理（已登录 → /index，未登录 → /login），
 *       本组件直接渲染模板登录表单即可</li>
 * </ol>
 *
 * <p>登录表单提交、redirect 跳转等业务逻辑都交给模板的 Login 组件处理
 * （useAuthFlow.loginByPassword 内已做 redirect 跳转），本组件只负责 SSO 弹窗。</p>
 */
import { ref, computed, watch, onMounted, onUnmounted, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getToken, removeToken } from '@/utils/auth';
import { isRelogin } from '@/utils/request';
import { getRedirectUrl } from '@/api/login';
import { useUserStore } from '@/store/modules/user';
import { useClientConfigStore } from '@/store/modules/clientConfig';
import { resolveLoginClientId, resolveRedirectTarget, persistClientId } from '@/utils/client';
import { getTemplate } from '@/views/auth';
import LegacySsoLogin from '@/views/ssologin.vue';
import type { ClientLayoutConfig } from '@/views/auth/types';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const configStore = useClientConfigStore();

// 当前 clientId（只认 URL：query/hash 里的 ?client=，无则用默认客户端）
// 不回退 localStorage——避免上次第三方授权残留的 client 劫持模板选择，
// 导致 /ssologin（无 client 参数）不显示系统默认登录页
const clientId = ref<string>(resolveLoginClientId(route.query as any));
// 模板加载完成
const ready = ref<boolean>(false);
// 是否使用老界面（后台未配置 template 时回退到原 ssologin.vue）
const useLegacy = ref<boolean>(false);
// 解析后的 layoutConfig
const layoutConfig = ref<ClientLayoutConfig>({});
// 当前模板的 Login 组件
const templateComponent = ref<any>(null);

// 已登录检测中（隐藏登录表单，避免弹窗背后是登录框）
const authChecking = ref<boolean>(false);
// 已登录提示对话框
const authDialog = ref<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });

/**
 * SSO 授权模式（SDK 通过 auth_mode 传入）：
 * - 不传或其它值：保留原有"已登录"弹窗逻辑
 * - center：不弹窗，已登录直接进入授权中心，未登录停留登录页
 * - silent：免确认，已登录直接完成授权回跳客户端，未登录停留登录页
 */
const AUTH_MODE_CENTER = 'center';
const AUTH_MODE_SILENT = 'silent';
const authMode = computed(() => String(route.query?.auth_mode || '').toLowerCase());
const hasSsoRequest = computed(() => !!(route.query?.client || route.query?.redirect));

// 解析 redirect 跳转目标（处理嵌套 ssologin 链）
const resolveTarget = () => {
  const redirectRaw = (route.query?.redirect as string) || '';
  return resolveRedirectTarget(redirectRaw, clientId.value);
};

/**
 * 加载 client 配置 + 选择模板
 */
const loadAndRender = async () => {
  ready.value = false;
  const cfg = await configStore.ensureLoaded(clientId.value);
  layoutConfig.value = cfg?.layoutConfigParsed || {};
  const tplName = (layoutConfig.value as any)?.template;
  if (!tplName) {
    // 后台未配置模板 → 回退到原有老界面（views/ssologin.vue 作为纯登录表单使用）
    useLegacy.value = true;
    templateComponent.value = markRaw(LegacySsoLogin);
  } else {
    useLegacy.value = false;
    // markRaw 避免组件对象被 reactive 包装
    const tpl = getTemplate(tplName);
    templateComponent.value = markRaw(tpl.login);
  }
  // 若已是登录态且是 SDK 授权请求（带 client/redirect），
  // 渲染前先置 authChecking=true，避免模板组件先挂载再被 spinner 替换造成的闪烁
  if (getToken() && (route.query?.client || route.query?.redirect)) {
    authChecking.value = true;
  }
  ready.value = true;
};

// 监听 clientId 变化
watch(
  () => route.query?.client,
  (v) => {
    if (v) {
      clientId.value = String(v);
      loadAndRender();
    }
  }
);

onMounted(async () => {
  await loadAndRender();

  // 持久化 clientId，供后续 401 兜底跳转使用
  persistClientId(clientId.value);

  // SDK 授权请求 + 已登录：按 auth_mode 分流
  // （loadAndRender 已置 authChecking=true 避免表单先挂载的闪烁）
  // 其他场景由 permission.ts 处理：
  //   - 已登录 + !hasSso → permission.ts 调 getInfo 验证后跳 /index（或清理后渲染本页）
  //   - 未登录 → 渲染登录表单（两种新模式同样停留登录页）
  if (getToken() && hasSsoRequest.value) {
    if (authMode.value === AUTH_MODE_SILENT) {
      // 免确认：直接完成授权回跳客户端（自动触发，不做全局单点注销）
      await handleSilentAuthorize();
      return;
    }
    if (authMode.value === AUTH_MODE_CENTER) {
      // 直接进入授权中心（复用弹窗"进入授权中心"按钮的行为）
      handleGoAuthCenter();
      return;
    }
    // 不带 auth_mode（或非法值）：保留原有弹窗逻辑，不做任何改动
    authDialog.value.visible = true;
  }
});

onUnmounted(() => {
  // 清理 isRelogin 标志位（防御性：避免本组件卸载后全局 401 弹窗被卡住）
  isRelogin.show = false;
});

/**
 * 已登录弹窗 - 确认授权：调 getRedirectUrl 拿到目标地址 + 目标 client 的 token
 */
const handleAuthConfirm = async () => {
  const { target, targetClient } = resolveTarget();
  authDialog.value.loading = true;
  // 抑制 request.ts 全局 401 弹窗，由本页自行处理
  isRelogin.show = true;
  try {
    const { data } = await getRedirectUrl({
      redirect: target === '/' ? '' : encodeURIComponent(target),
      client: targetClient || clientId.value || import.meta.env.VITE_APP_CLIENT_ID
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
 * silent 模式自动授权（已登录时由 onMounted 触发）
 *
 * <p>与手动"确认授权"的差别：</p>
 * <ol>
 *   <li>一次页面生命周期内只发起一次：服务端 buildRedirectUrl 每次会删除同一 client 的旧 ticket，
 *       重复调用会把刚下发、还没被客户端消费的 ticket 作废；</li>
 *   <li>失败时只清理本地登录态并回落登录表单，绝不调 ssoLogout()——那会触发认证中心
 *       全局单点注销，把其它已登录的系统一起踢下线。</li>
 * </ol>
 */
let silentAuthorizeTriggered = false;
const handleSilentAuthorize = async () => {
  if (silentAuthorizeTriggered) return;
  silentAuthorizeTriggered = true;
  const { target, targetClient } = resolveTarget();
  isRelogin.show = true;
  let redirectUrl = '';
  try {
    const { data } = await getRedirectUrl({
      redirect: target === '/' ? '' : encodeURIComponent(target),
      client: targetClient || clientId.value || import.meta.env.VITE_APP_CLIENT_ID
    });
    redirectUrl = data || '';
  } catch {
    // 本地 token 已失效：静默清理，不触发全局注销
  } finally {
    isRelogin.show = false;
  }
  if (redirectUrl) {
    location.href = redirectUrl;
    return;
  }
  userStore.token = '';
  removeToken();
  authChecking.value = false;
  authDialog.value.visible = false;
  authDialog.value.loading = false;
};

/**
 * 已登录弹窗 - 切换账号：注销当前登录态，显示登录表单让用户输入新账号
 */
const handleAuthSwitchAccount = async () => {
  authDialog.value.loading = true;
  isRelogin.show = true;
  try {
    try {
      await userStore.ssoLogout();
    } catch (ignored) {
      // 即使服务端失败也继续，让用户在本地重新登录
    }
    // 本地兜底清理
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
 *
 * <p>注意：不要把 authChecking 置 false，否则 useLegacy 分支会先把老表单渲染出来
 * 再被 router.push 替换，造成一帧表单闪烁。保持 spinner 直到 SsoLoginPage 卸载。</p>
 */
const handleGoAuthCenter = () => {
  authDialog.value.visible = false;
  authDialog.value.loading = false;
  router.push('/index');
};
</script>

<style lang="scss" scoped>
.sso-login-page {
  position: relative;
  min-height: 100vh;
  width: 100%;

  // 老界面（views/ssologin.vue）的 .login 用 height:100% 撑高，
  // 在 min-height 容器里不生效，这里兜底保证背景铺满全屏
  :deep(.login) {
    min-height: 100vh;
  }
}

.sso-login-loading {
  position: fixed;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1;
}

.sso-login-loading p {
  margin: 0;
  color: #547bd6;
  font-size: 15px;
  font-weight: 500;
}

// 已登录检测中：老界面隐藏表单卡片（.login 背景图保留）
.is-checking-legacy {
  :deep(.login-container) {
    visibility: hidden;
  }
}

// 已登录检测中：新模板结构不可预知，整体模糊兜底（文字不可辨认，防止泄露记住的账号）
.is-checking-blur {
  filter: blur(8px);
  pointer-events: none;
  user-select: none;
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
</style>
