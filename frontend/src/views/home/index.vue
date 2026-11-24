<template>
  <div class="app-container dashboard">
    <section class="welcome-card">
      <el-skeleton :loading="loading" animated>
        <div class="welcome-content">
          <el-avatar :src="avatar" :size="64" class="welcome-avatar">
            <img src="@/assets/images/profile.jpg" alt="用户头像" />
          </el-avatar>
          <div class="welcome-copy">
            <div class="welcome-eyebrow">统一认证管理平台 · 工作台</div>
            <h1>你好，{{ username || '管理员' }}</h1>
            <p>{{ deptName || '欢迎回来，祝你今天工作顺利！' }}</p>
          </div>
        </div>
      </el-skeleton>
      <div class="welcome-decoration" aria-hidden="true"></div>
      <div class="welcome-brand">
        <img :src="platformLogo" alt="统一认证平台" />
        <span>统一认证平台</span>
      </div>
    </section>

    <div class="dashboard-grid">
      <main class="dashboard-main">
        <el-card v-if="shortcut.length" shadow="never" class="dashboard-card">
          <template #header>
            <div class="section-heading">
              <div>
                <h2>快捷入口</h2>
                <p>快速访问已绑定的应用</p>
              </div>
            </div>
          </template>
          <el-skeleton :loading="loading" animated>
            <div class="shortcut-grid app-shortcut-grid">
              <el-link
                v-for="item in shortcut"
                :key="`shortcut-${item.clientId || item.url || item.name}`"
                class="launch-link"
                type="default"
                :underline="false"
                @click="handleShortcutClick(item)"
              >
                <div class="launch-card">
                  <div class="launch-icon">
                    <template v-if="(item.icon || '').includes('http')">
                      <img :src="item.icon" :alt="`${item.name}图标`" />
                    </template>
                    <template v-else>
                      <svg-icon v-if="item.icon && item.icon !== '#'" :icon-class="item.icon" class="shortcut-svg-icon"></svg-icon>
                      <img v-else :src="defaultMenuIcon" :alt="`${item.name}图标`" />
                    </template>
                  </div>
                  <span class="launch-label" :title="item.name">{{ item.name }}</span>
                </div>
              </el-link>
            </div>
          </el-skeleton>
        </el-card>

        <el-card v-if="menuList.length" shadow="never" class="dashboard-card">
          <template #header>
            <div class="section-heading">
              <div>
                <h2>系统功能</h2>
                <p>常用管理与运维工具</p>
              </div>
            </div>
          </template>
          <el-skeleton :loading="loading" animated>
            <div class="shortcut-grid">
              <el-link
                v-for="item in menuList"
                :key="`menu-${item.path}`"
                class="launch-link"
                type="default"
                :underline="false"
                @click="handleMenuClick(item)"
              >
                <div class="launch-card">
                  <div class="launch-icon">
                    <img :src="item.meta.iconImage || defaultMenuIcon" :alt="`${item.meta.title}图标`" />
                  </div>
                  <span class="launch-label" :title="item.meta.title">{{ item.meta.title }}</span>
                </div>
              </el-link>
            </div>
          </el-skeleton>
        </el-card>
      </main>

      <aside v-if="userStore.permissions.includes('system:notice:list') || userStore.permissions.includes('*:*:*')" class="dashboard-aside">
        <el-card shadow="never" class="dashboard-card notice-card">
          <template #header>
            <div class="section-heading section-heading-action">
              <div>
                <h2>通知公告</h2>
                <p>及时了解平台动态</p>
              </div>
              <el-link type="primary" :underline="false" @click="handleNoticeClick">查看全部</el-link>
            </div>
          </template>
          <el-skeleton :loading="loading" animated>
            <div v-if="notice.length" class="notice-list">
              <article v-for="(item, index) in notice" :key="`notice-${index}`" class="notice-item">
                <div class="notice-title">
                  <dict-tag :options="sys_notice_type" :value="item.type" />
                  <span class="notice-title-text" :title="item.title">{{ item.title }}</span>
                </div>
                <time class="notice-date">{{ item.date }}</time>
              </article>
            </div>
            <el-empty v-else description="暂无通知公告" :image-size="88" />
          </el-skeleton>
        </el-card>
      </aside>
    </div>
  </div>
</template>
<script lang="ts" setup>
import defaultMenuIcon from '@/assets/images/menu_icon/default.png';
import platformLogo from '@/assets/logo/logo.svg';
import { useUserStore } from '@/store/modules/user';
import { usePermissionStore } from '@/store/modules/permission';
import type { Notice, Shortcut } from './types';
import { useRouter } from 'vue-router';
import { listBoundApplicationsApi } from '@/api/home';
import { getRedirectUrl } from '@/api/login';
import { listNotice } from '@/api/system/notice';
import { NoticeQuery } from '@/api/system/notice/types';

defineOptions({ name: 'Index' });

const router = useRouter();
const userStore = useUserStore();
const loading = ref(true);
const avatar = userStore.avatar;
const username = userStore.nickname;
const deptName = userStore.deptName;
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_notice_type } = toRefs<any>(proxy?.useDict('sys_notice_type'));
const permissionStore = usePermissionStore();
const routes = permissionStore.getTopbarRoutes();

const menuList = ref<any>([]);

// 获取通知公告
const notice = reactive<Notice[]>([]);
const noticeQuery = {
  pageNum: 1,
  pageSize: 10
};
const getNotice = async () => {
  if (userStore.permissions.includes('system:notice:list') || userStore.permissions.includes('*:*:*')) {
    const response = await listNotice(<NoticeQuery>noticeQuery);
    // 使用 splice 正确更新响应式数组
    notice.splice(
      0,
      notice.length,
      ...response.rows.map(
        (item): Notice => ({
          title: item.noticeTitle,
          type: item.noticeType,
          date: item.createTime
        })
      )
    );
  }
};

// 获取快捷入口
const shortcut = ref<Shortcut[]>([]);

// 获取应用
const getApplications = async () => {
  const applications: any = await listBoundApplicationsApi();
  // data 可能为 null（无应用数据），icon 可能为 null（未配图标），均需兜底
  shortcut.value = (applications.data || []).map((v) => {
    return {
      name: v.menuName,
      icon: v.icon || '',
      url: v.path,
      clientId: v.clientId
    };
  });
};

const handleShortcutClick = async (item: any) => {
  const urlData = await getRedirectUrl({ client: item.clientId, redirect: item.url });
  window.open(urlData.data);
};

const handleNoticeClick = () => {
  router.push('/system/notice');
};

const handleMenuClick = (item: any) => {
  router.push(`${item.parentPath}/${item.path}`);
};

const handleMenuList = () => {
  // 初始化菜单列表（避免直接操作响应式数据多次触发更新）
  let tempMenuList = [];
  const childrenMenus = [];

  // 第一步：处理一级菜单的直接子项（二级菜单）
  for (const parentItem of routes) {
    // 安全校验：确保children存在且是数组
    if (Array.isArray(parentItem.children) && parentItem.children.length) {
      const processedChildren = parentItem.children.map((child) => {
        // 继承父路径
        child.parentPath = parentItem.path;
        // 有三级子项的二级菜单标记为隐藏
        if (Array.isArray(child.children) && child.children.length) {
          child.hidden = true;
          // 提前收集三级菜单（避免二次遍历）
          child.children.forEach((grandChild) => {
            grandChild.parentPath = `${parentItem.path}/${child.path}`;
            childrenMenus.push(grandChild);
          });
        }
        return child;
      });
      tempMenuList.push(...processedChildren);
    }
  }

  // 第二步：合并三级菜单并过滤隐藏项（一次完成，减少数组操作）
  tempMenuList = [...tempMenuList, ...childrenMenus].filter((item) => !item.hidden);

  // 第三步：处理菜单图标（优化查找逻辑，缓存路径映射）
  const images = import.meta.glob('@/assets/images/menu_icon/*.png', { eager: true });
  // 预构建 图标名 -> 图片路径 的映射（优化查找性能）
  const iconPathMap = Object.fromEntries(
    Object.entries(images)
      .map(([key, module]) => {
        // 提取图标文件名（不含后缀）作为key
        const iconName = key.match(/menu_icon\/(.+?)\.png$/)?.[1];
        return [iconName, module.default];
      })
      .filter(([key]) => key) // 过滤无效映射
  );

  // 批量处理图标（避免多次查找Object.keys）
  tempMenuList = tempMenuList.map((item) => {
    if (item.meta?.icon && iconPathMap[item.meta.icon]) {
      item.meta.iconImage = iconPathMap[item.meta.icon];
    }
    return item;
  });

  // 最后一次性更新响应式数据（减少页面重渲染）
  menuList.value = tempMenuList;
};

const getAllApi = async () => {
  await Promise.all([getApplications(), getNotice()]);
  loading.value = false;
};

onMounted(async () => {
  getAllApi();
  handleMenuList();
});
</script>

<style lang="scss" scoped>
.dashboard {
  --dashboard-text: var(--el-text-color-primary);
  --dashboard-muted: var(--el-text-color-secondary);
  --dashboard-border: var(--el-border-color-lighter);
  --dashboard-accent: var(--el-color-primary);
  padding: 20px;
}

.welcome-card {
  position: relative;
  display: flex;
  min-height: 142px;
  align-items: center;
  overflow: hidden;
  padding: 28px 32px;
  border: 1px solid #dce8fb;
  border-radius: 12px;
  background: linear-gradient(115deg, #f1f6ff 0%, #f8fbff 54%, #edf5ff 100%);
}

.welcome-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 20px;
}

.welcome-avatar {
  flex: 0 0 auto;
  border: 3px solid rgb(255 255 255 / 90%);
  box-shadow: 0 4px 14px rgb(46 91 158 / 14%);
}

.welcome-copy {
  h1 {
    margin: 6px 0 4px;
    color: #1d2b45;
    font-size: 26px;
    font-weight: 600;
    line-height: 1.4;
  }

  p {
    margin: 0;
    color: #71809a;
    font-size: 15px;
  }
}

.welcome-eyebrow {
  color: #5b7eaf;
  font-size: 14px;
  letter-spacing: 0.04em;
}

.welcome-decoration {
  position: absolute;
  z-index: 0;
  top: -105px;
  right: 7%;
  width: 260px;
  height: 260px;
  border: 1px solid rgb(81 132 207 / 12%);
  border-radius: 50%;
  background: rgb(255 255 255 / 24%);

  &::before,
  &::after {
    position: absolute;
    border: 1px solid rgb(81 132 207 / 12%);
    border-radius: 50%;
    content: '';
  }

  &::before {
    inset: 20px;
  }

  &::after {
    inset: 42px;
  }
}

.welcome-brand {
  position: absolute;
  z-index: 1;
  top: 50%;
  right: 9%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  transform: translateY(-50%);
  border: 1px solid rgb(255 255 255 / 80%);
  border-radius: 14px;
  background: rgb(255 255 255 / 58%);
  color: #42669d;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.04em;
  box-shadow: 0 5px 18px rgb(46 91 158 / 6%);
  backdrop-filter: blur(6px);

  img {
    display: block;
    width: 44px;
    height: 44px;
  }
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  align-items: start;
  gap: 16px;
  margin-top: 16px;
}

.dashboard-main,
.dashboard-aside {
  display: grid;
  min-width: 0;
  gap: 16px;
}

.dashboard-aside {
  padding: 0;
  background: transparent;
}

.dashboard-card {
  min-width: 0;
  border-radius: 10px;
  background: var(--el-bg-color);
  transition: box-shadow 0.2s ease;

  &:hover {
    box-shadow: 0 5px 18px rgb(31 45 61 / 5%);
  }

  :deep(.el-card__header) {
    padding: 18px 20px 14px;
    border-bottom-color: var(--dashboard-border);
  }

  :deep(.el-card__body) {
    padding: 16px;
  }
}

.section-heading {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  h2 {
    margin: 0;
    color: var(--dashboard-text);
    font-size: 17px;
    font-weight: 600;
    line-height: 1.5;
  }

  p {
    margin: 3px 0 0;
    color: var(--dashboard-muted);
    font-size: 14px;
  }
}

.section-heading-action {
  align-items: center;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 8px;
}

.launch-link {
  width: 100%;
  min-width: 0;
  border-radius: 9px;

  &:hover {
    color: var(--dashboard-accent);
  }

  &:focus-visible {
    outline: 2px solid var(--dashboard-accent);
    outline-offset: 2px;
  }
}

.launch-card {
  display: flex;
  width: 100%;
  min-height: 120px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 6px;
  border-radius: 9px;
  transition:
    background-color 0.18s ease,
    transform 0.18s ease;

  .launch-link:hover & {
    transform: translateY(-2px);
    background: var(--el-fill-color-light);
  }
}

.launch-icon {
  display: flex;
  width: 60px;
  height: 60px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 10px;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }

  .shortcut-svg-icon {
    font-size: 48px;
  }
}

.app-shortcut-grid {
  .launch-icon {
    width: 72px;
    height: 72px;

    img {
      transform: scale(1.2);
    }

    .shortcut-svg-icon {
      font-size: 56px;
    }
  }
}

.launch-label {
  max-width: 100%;
  overflow: hidden;
  color: var(--dashboard-text);
  font-size: 14px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-card {
  :deep(.el-card__body) {
    padding: 4px 20px;
  }

  :deep(.el-empty) {
    padding: 16px 0;
  }
}

.notice-item {
  padding: 17px 0;
  border-bottom: 1px solid var(--dashboard-border);

  &:last-child {
    border-bottom: 0;
  }
}

.notice-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}

.notice-title-text {
  overflow: hidden;
  color: var(--dashboard-text);
  font-size: 15px;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-date {
  display: block;
  margin-top: 9px;
  color: var(--dashboard-muted);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .shortcut-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .dashboard-aside {
    grid-row: 2;
  }
}

@media (max-width: 640px) {
  .dashboard {
    padding: 12px;
  }

  .welcome-card {
    min-height: 120px;
    padding: 22px 18px;
  }

  .welcome-content {
    gap: 14px;
  }

  .welcome-copy h1 {
    font-size: 21px;
  }

  .welcome-copy p {
    font-size: 14px;
  }

  .welcome-eyebrow {
    font-size: 12px;
  }

  .welcome-brand {
    right: 14px;
    padding: 8px;
    border-radius: 12px;

    img {
      width: 36px;
      height: 36px;
    }

    span {
      display: none;
    }
  }

  .welcome-decoration {
    right: -130px;
  }

  .dashboard-grid,
  .dashboard-main,
  .dashboard-aside {
    gap: 12px;
  }

  .shortcut-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .launch-card {
    min-height: 104px;
    padding: 8px 4px;
  }

  .launch-icon {
    width: 52px;
    height: 52px;
  }

  .app-shortcut-grid .launch-icon {
    width: 60px;
    height: 60px;
  }

  .dashboard-card {
    :deep(.el-card__header) {
      padding: 15px 16px 12px;
    }

    :deep(.el-card__body) {
      padding: 12px;
    }
  }

  .notice-card {
    :deep(.el-card__body) {
      padding: 4px 16px;
    }
  }
}

@media (max-width: 420px) {
  .shortcut-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
