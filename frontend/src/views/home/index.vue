<template>
  <div class="app-container">
    <div>
      <el-card shadow="never">
        <el-skeleton :loading="loading" animated>
          <el-row :gutter="16" justify="space-between">
            <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24">
              <div class="flex items-center">
                <el-avatar :src="avatar" :size="70" class="mr-16px">
                  <img src="@/assets/images/profile.jpg" alt="" />
                </el-avatar>
                <div>
                  <div class="text-20px">你好 {{ username }} 祝你开心每一天!</div>
                  <div class="mt-10px text-14px text-gray-500">{{ deptName }}</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-skeleton>
      </el-card>
    </div>

    <el-row class="mt-8px" :gutter="8" justify="space-between">
      <el-col :xl="16" :lg="16" :md="24" :sm="24" :xs="24" class="mb-8px">
        <el-card shadow="never" class="mt-8px" v-if="shortcut.length">
          <template #header>
            <div class="h-3 flex justify-between">
              <span>快捷入口</span>
            </div>
          </template>
          <el-skeleton :loading="loading" animated>
            <el-row>
              <el-col v-for="item in shortcut" :key="`team-${item.name}`" :span="3" class="mb-8px text-center">
                <el-link type="default" :underline="false" @click="handleShortcutClick(item)">
                  <div class="shortcut">
                    <div class="shortcut-icon text-center">
                      <!-- icon 可能为 null（应用未配图标），直接 includes 会抛错导致列表渲染中断 -->
                      <template v-if="(item.icon || '').includes('http')">
                        <img :src="item.icon" alt="" />
                      </template>
                      <template v-else>
                        <svg-icon v-if="item.icon && item.icon !== '#'" :icon-class="item.icon" class="shortcut-svg-icon"></svg-icon>
                        <img v-else src="@/assets/images/menu_icon/default.png" alt="" />
                      </template>
                    </div>
                    <div class="text-center">
                      {{ item.name }}
                    </div>
                  </div>
                </el-link>
              </el-col>
            </el-row>
          </el-skeleton>
        </el-card>
        <el-card shadow="never" class="mt-8px" v-if="menuList.length">
          <template #header>
            <div class="h-3 flex justify-between">
              <span>系统功能</span>
            </div>
          </template>
          <el-skeleton :loading="loading" animated>
            <el-row>
              <el-col v-for="item in menuList" :key="`team-${item.name}`" :span="3" class="mb-20px text-center">
                <el-link type="default" :underline="false" @click="handleMenuClick(item)">
                  <div class="shortcut">
                    <div class="shortcut-icon text-center">
                      <img :src="item.meta.iconImage || '@/assets/images/menu_icon/default.png'" alt="" />
                    </div>
                    <div class="text-center">
                      {{ item.meta.title }}
                    </div>
                  </div>
                </el-link>
              </el-col>
            </el-row>
          </el-skeleton>
        </el-card>
      </el-col>
      <el-col
        :xl="8"
        :lg="8"
        :md="24"
        :sm="24"
        :xs="24"
        class="mb-8px"
        v-if="userStore.permissions.includes('system:notice:list') || userStore.permissions.includes('*:*:*')"
      >
        <el-card shadow="never" class="mt-8px">
          <template #header>
            <div class="h-3 flex justify-between">
              <span>通知公告</span>
              <el-link type="primary" :underline="false" @click="handleNoticeClick">更多</el-link>
            </div>
          </template>
          <el-skeleton :loading="loading" animated>
            <div v-for="(item, index) in notice" :key="`dynamics-${index}`">
              <div class="flex items-center">
                <div>
                  <div class="text-14px notice-title">
                    <dict-tag :options="sys_notice_type" :value="item.type" />
                    <div class="notice-title-text">{{ item.title }}</div>
                  </div>
                  <div class="mt-16px text-12px text-gray-400">
                    {{ item.date }}
                  </div>
                </div>
              </div>
              <el-divider />
            </div>
          </el-skeleton>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script lang="ts" setup>

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
.shortcut {
  &-icon {
    width: 60px;
    height: 60px;
    border-radius: 10px;
    line-height: 60px;
    margin: 0 auto 10px;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;

    img {
      width: 100%;
      height: 100%;
    }

    .shortcut-svg-icon {
      font-size: 32px;
    }
  }
}
.notice-title {
  display: flex;
  align-items: center;

  &-text {
    margin-left: 8px;
  }
}

.mb-20px {
  margin-bottom: 20px;
}
</style>
