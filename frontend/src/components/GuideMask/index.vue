<template>
  <Teleport to="body">
    <div v-if="visible" class="guide-root">
      <div class="guide-backdrop"></div>
      <div class="guide-card" :class="{ 'has-shot': !!currentImage }">
        <div class="guide-card__head">
          <span class="guide-card__index">第 {{ index + 1 }} / {{ steps.length }} 步</span>
          <span class="guide-card__progress"><i :style="{ width: progress + '%' }"></i></span>
          <button class="guide-card__close" type="button" title="关闭说明" @click="close">×</button>
        </div>

        <h3 class="guide-card__title">{{ step.title }}</h3>

        <div v-if="currentImage" class="guide-shot-wrap">
          <div class="guide-shot">
            <img :src="currentImage" :alt="step.title" />
            <span v-for="(mark, i) in step.marks || []" :key="i" class="guide-shot__mark" :style="{ left: mark.x + '%', top: mark.y + '%' }">{{
              mark.text ?? i + 1
            }}</span>
          </div>
        </div>

        <ul class="guide-card__lines">
          <li v-for="(line, i) in step.lines" :key="i">{{ line }}</li>
        </ul>
        <p v-if="step.tip" class="guide-card__tip">{{ step.tip }}</p>

        <div class="guide-card__foot">
          <button class="guide-btn" type="button" :disabled="index === 0" @click="prev">上一步</button>
          <span class="guide-card__skip" @click="close">跳过说明</span>
          <button class="guide-btn guide-btn--primary" type="button" @click="next">
            {{ index === steps.length - 1 ? '完成' : '下一步' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import cache from '@/plugins/cache';
import { useUserStore } from '@/store/modules/user';
import { guideSteps, GUIDE_VERSION } from './steps';

const steps = guideSteps;
const userStore = useUserStore();

/** 静态引入 src/assets/guide/ 下的截图，文件不存在时该步只显示文字 */
const shotFiles = import.meta.glob('../../assets/guide/*.{png,jpg,jpeg,webp,svg}', {
  eager: true,
  query: '?url',
  import: 'default'
}) as Record<string, string>;

const visible = ref(false);
const index = ref(0);
const step = computed(() => steps[index.value]);
const progress = computed(() => Math.round(((index.value + 1) / steps.length) * 100));

const currentImage = computed(() => {
  const name = step.value.image;
  if (!name) return undefined;
  return Object.entries(shotFiles).find(([path]) => path.endsWith(`/${name}`))?.[1];
});

const storageKey = () => `guide-usage-${GUIDE_VERSION}:${userStore.userId || 'anonymous'}`;
const isDone = () => cache.local.get(storageKey()) === '1';
const markDone = () => cache.local.set(storageKey(), '1');

/** 角色编码包含 admin 才在登录时自动弹出；其它角色仍可点顶栏 ? 手动查看 */
const hasAdminRole = () => {
  const roles = userStore.roles ?? [];
  return roles.some((role) => role.includes('admin'));
};

let startTimer = 0;
let retryTimes = 0;

const next = () => {
  if (index.value >= steps.length - 1) {
    close();
    return;
  }
  index.value += 1;
};

const prev = () => {
  if (index.value > 0) index.value -= 1;
};

const start = (force = false) => {
  if (visible.value || (!force && isDone())) return;
  index.value = 0;
  visible.value = true;
  window.addEventListener('keydown', onKey);
};

const close = () => {
  if (!visible.value) return;
  visible.value = false;
  markDone();
  releaseEvents();
};

const onKey = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    close();
  } else if (event.key === 'ArrowRight') {
    next();
  } else if (event.key === 'ArrowLeft') {
    prev();
  }
};

const releaseEvents = () => {
  window.removeEventListener('keydown', onKey);
  window.clearTimeout(startTimer);
};

const tryAutoStart = () => {
  if (isDone() || visible.value) return;
  // 角色信息可能尚未由 getInfo 填充，稍后重试，避免管理员漏弹
  if (!userStore.roles?.length && retryTimes < 3) {
    retryTimes += 1;
    startTimer = window.setTimeout(tryAutoStart, 800);
    return;
  }
  if (hasAdminRole()) start();
};

onMounted(() => {
  // 等用户信息与角色就绪后再判断是否首次展示
  startTimer = window.setTimeout(tryAutoStart, 900);
});

onUnmounted(releaseEvents);

defineExpose({ start });
</script>

<style lang="scss" scoped>
.guide-root {
  position: fixed;
  inset: 0;
  z-index: 2000000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  font-size: 14px;
}

.guide-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.62);
}

.guide-card {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
  width: 460px;
  max-width: 100%;
  max-height: 100%;
  padding: 18px 20px 16px;
  overflow-y: auto;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.22);

  &.has-shot {
    width: 780px;
  }
}

.guide-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.guide-card__index {
  flex: none;
  font-size: 12px;
  color: var(--el-color-primary, #409eff);
}

.guide-card__progress {
  flex: 1;
  height: 4px;
  overflow: hidden;
  background: var(--el-border-color-lighter, #ebeef5);
  border-radius: 4px;

  i {
    display: block;
    height: 100%;
    background: var(--el-color-primary, #409eff);
    border-radius: 4px;
    transition: width 0.25s ease;
  }
}

.guide-card__close {
  flex: none;
  padding: 0;
  font-size: 20px;
  line-height: 1;
  color: var(--el-text-color-secondary, #909399);
  cursor: pointer;
  background: none;
  border: none;

  &:hover {
    color: var(--el-color-primary, #409eff);
  }
}

.guide-card__title {
  margin: 0 0 12px;
  font-size: 17px;
  font-weight: 600;
}

.guide-shot-wrap {
  margin-bottom: 14px;
  text-align: center;
}

.guide-shot {
  position: relative;
  display: inline-block;
  max-width: 100%;
  line-height: 0;

  img {
    display: block;
    width: auto;
    height: auto;
    max-width: 100%;
    max-height: 340px;
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 6px;
  }
}

.guide-shot__mark {
  position: absolute;
  box-sizing: content-box;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 22px;
  color: #fff;
  text-align: center;
  background: var(--el-color-primary, #409eff);
  border: 2px solid #fff;
  border-radius: 999px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  transform: translate(-50%, -50%);
}

.guide-card__lines {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;

  li + li {
    margin-top: 4px;
  }
}

.guide-card__tip {
  margin: 12px 0 0;
  padding: 9px 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular, #606266);
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 6px;
}

.guide-card__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
}

.guide-card__skip {
  margin-right: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary, #409eff);
  }
}

.guide-btn {
  padding: 7px 16px;
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  background: var(--el-fill-color-blank, #fff);
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 5px;

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }

  &--primary {
    color: #fff;
    background: var(--el-color-primary, #409eff);
    border-color: var(--el-color-primary, #409eff);

    &:hover {
      opacity: 0.88;
    }
  }
}
</style>
