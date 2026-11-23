<template>
  <div class="auth-page" :style="pageStyle">
    <!-- 顶部 Logo / 系统名 -->
    <div v-if="showHeader" class="auth-header">
      <img v-if="resolvedLogoUrl" :src="resolvedLogoUrl" class="auth-header-logo" :alt="resolvedLogoText" />
      <span v-else-if="resolvedLogoText" class="auth-header-text">{{ resolvedLogoText }}</span>
    </div>

    <!-- 主体：左侧插画 + 右侧表单 -->
    <div class="auth-main" :class="`form-pos-${formPosition}`">
      <div v-if="showLeftImage" class="auth-left">
        <img v-if="config?.login?.leftImage" :src="config.login.leftImage" alt="" />
      </div>
      <div class="auth-right">
        <div class="auth-form-card">
          <h2 v-if="pageTitle" class="auth-form-title">{{ pageTitle }}</h2>
          <p v-if="pageSubtitle" class="auth-form-subtitle">{{ pageSubtitle }}</p>
          <slot />
        </div>
      </div>
    </div>

    <!-- 底部版权 -->
    <div v-if="showFooter" class="auth-footer">
      <slot name="footer">
        <span>Copyright © 2018-2025 授权中心</span>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watchEffect } from 'vue';
import defaultLogoUrl from '@/assets/logo/logo.svg';
import type { ClientLayoutConfig } from '@/views/auth/types';

interface Props {
  /** layout_config 解析后的对象 */
  config: ClientLayoutConfig;
  /** 当前页面：login / register / resetPwd */
  mode: 'login' | 'register' | 'resetPwd';
  /** 表单位置（仅 login/register） */
  formPosition?: 'left' | 'center' | 'right';
  /** 是否显示顶部 Logo + 系统名（默认 true） */
  showHeader?: boolean;
  /** 是否显示底部（默认 true） */
  showFooter?: boolean;
  /** 是否显示左侧插画（默认 false） */
  showLeftImage?: boolean;
  /** 系统名/标题（用于顶部） */
  systemName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  formPosition: 'right',
  showHeader: true,
  showFooter: true,
  showLeftImage: false,
  systemName: ''
});

/** 整页背景图（按 mode 取 login/register/resetPwd 的 backgroundImage） */
const pageStyle = computed<Record<string, string>>(() => {
  const cfg = props.config || {};
  const bg =
    (cfg as any).login?.backgroundImage ||
    (cfg as any).register?.backgroundImage ||
    (cfg as any).resetPwd?.backgroundImage ||
    '';
  return bg
    ? { backgroundImage: `url('${bg}')`, backgroundSize: 'cover', backgroundPosition: 'center' }
    : {};
});

/** 顶部 Logo URL 解析 */
const resolvedLogoUrl = computed<string>(() => {
  const cfg = props.config || {};
  return (
    (cfg as any).register?.logoUrl ||
    (cfg as any).resetPwd?.logoUrl ||
    (cfg as any).theme?.logoUrl ||
    defaultLogoUrl
  );
});

/** 顶部 Logo 文字 */
const resolvedLogoText = computed<string>(() => {
  const cfg = props.config || {};
  return (
    props.systemName ||
    (cfg as any).theme?.logoText ||
    (cfg as any).login?.title ||
    ''
  );
});

/** 页面标题（按 mode 取对应配置） */
const pageTitle = computed<string>(() => {
  const cfg = props.config || {};
  if (props.mode === 'login') return (cfg as any).login?.title || '';
  if (props.mode === 'register') return (cfg as any).register?.title || '';
  return (cfg as any).resetPwd?.title || '';
});

/** 页面副标题 */
const pageSubtitle = computed<string>(() => {
  if (props.mode === 'login') return (props.config as any)?.login?.subtitle || '';
  return '';
});

/** 主题主色覆盖 Element Plus 变量 */
watchEffect(() => {
  const color = (props.config as any)?.theme?.primaryColor;
  if (color) {
    document.documentElement.style.setProperty('--el-color-primary', color);
  }
});
</script>

<style lang="scss" scoped>
.auth-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  width: 100%;
  background-color: #f5f7fa;
}

.auth-header {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
  gap: 12px;
  z-index: 2;

  &-logo {
    height: 36px;
    object-fit: contain;
  }

  &-text {
    font-size: 20px;
    font-weight: 600;
    color: #2468f2;
  }
}

.auth-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  gap: 0;
  width: 100%;

  &.form-pos-center {
    justify-content: center;
  }

  &.form-pos-left {
    justify-content: flex-start;
  }

  &.form-pos-right {
    justify-content: flex-end;
  }
}

.auth-left {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 40px;

  img {
    max-height: 400px;
    object-fit: contain;
  }
}

.auth-right {
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-form-card {
  background: #ffffff;
  border-radius: 10px;
  padding: 32px 40px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  min-width: 380px;
  max-width: 480px;
  width: 100%;
}

.auth-form-title {
  margin: 0 0 8px 0;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  text-align: center;
}

.auth-form-subtitle {
  margin: 0 0 20px 0;
  font-size: 13px;
  color: #909399;
  text-align: center;
}

.auth-footer {
  height: 40px;
  line-height: 40px;
  text-align: center;
  color: #fff;
  font-family: Arial, serif;
  font-size: 12px;
  letter-spacing: 1px;
  background: transparent;
}
</style>
