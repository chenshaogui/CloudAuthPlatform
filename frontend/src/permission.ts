import { to as tos } from 'await-to-js';
import router from './router';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import { getToken, removeToken } from '@/utils/auth';
import { isHttp, isPathMatch } from '@/utils/validate';
import { isRelogin } from '@/utils/request';
import { buildSsoLoginQuery } from '@/utils/ssoQuery';
import { useUserStore } from '@/store/modules/user';
import { useSettingsStore } from '@/store/modules/settings';
import { usePermissionStore } from '@/store/modules/permission';
import { useClientConfigStore } from '@/store/modules/clientConfig';
import { ElMessage } from 'element-plus/es';

NProgress.configure({ showSpinner: false });
const whiteList = ['/login', '/ssologin', '/register', '/resetPwd', '/social-callback', '/register*', '/register/*'];
/** 鉴权相关路由：进入时需预拉 client 配置（按 clientId 走模板） */
const authPaths = ['/ssologin', '/resetPwd', '/login', '/register'];

/**
 * 进入鉴权页面时预热客户端配置（按 URL ?client=xxx）
 * 失败不影响放行，模板走默认样式
 */
const preloadClientConfig = (toQuery: any) => {
  const clientId = (toQuery?.client as string) || localStorage.getItem('client');
  if (clientId) {
    useClientConfigStore().ensureLoaded(clientId).catch(() => {
      // store 内部已兜底，这里吃掉异常
    });
  }
};

const isWhiteList = (path: string) => {
  return whiteList.some((pattern) => isPathMatch(pattern, path));
};

router.beforeEach(async (to, from, next) => {
  NProgress.start();
  const redirect = to.query.redirect as string | undefined;
  let client = to.query.client as string | undefined;
  // 进入鉴权相关页面时预热客户端配置（按 clientId 走模板）
  if (authPaths.includes(to.path)) {
    preloadClientConfig(to.query);
  }
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title as string);
    /* has token*/
    if (to.path === '/login') {
      next({ path: '/' });
      NProgress.done();
    } else if (to.path === '/ssologin') {
      // 当 client 和 redirect 参数都没有时：说明不是第三方客户端授权请求，
      // 而是直接访问授权中心页面。
      if (!client && !redirect) {
        // 先调 getInfo 验证 token 有效性：
        // - 有效：生成动态路由后跳 /index
        // - 无效：静默清理登录态，直接渲染登录页
        // （避免盲跳 /index 后 getInfo 401 → logout → /auth/logout 又 401 → reload 回登录页的体验割裂）
        isRelogin.show = true;
        const [err] = await tos(useUserStore().getInfo());
        isRelogin.show = false;
        if (!err) {
          const accessRoutes = await usePermissionStore().generateRoutes();
          accessRoutes.forEach((route) => {
            if (!isHttp(route.path)) router.addRoute(route);
          });
          next({ path: '/index', replace: true });
        } else {
          // token 失效：静默清理（request.ts 在 auth 路径上已清 token，这里兜底角色信息）
          useUserStore().token = '';
          useUserStore().roles = [];
          useUserStore().permissions = [];
          removeToken();
          next();
        }
        NProgress.done();
        return;
      }
      if (client) {
        localStorage.setItem('client', client);
      }
      localStorage.setItem('redirect', redirect);
      // 已登录状态下的处理（切换账号 / 跨系统重新登录 / 直接跳转）统一交给
      // SsoLoginPage 的 authDialog 决定，避免在此处抢先注销 token 导致弹窗无法弹出
      next();
      NProgress.done();
    } else if (isWhiteList(to.path)) {
      next();
    } else {
      if (useUserStore().roles.length === 0) {
        isRelogin.show = true;
        // 判断当前用户是否已拉取完user_info信息
        const [err] = await tos(useUserStore().getInfo());
        if (err) {
          // token 失效（多为 401）：登录/授权页上 request.ts 已静默清理本地登录态，
          // 此时不能再调 /auth/logout（对已失效会话调用会再次 401 → location.reload 循环）
          if (getToken()) {
            await useUserStore().logout().catch(() => {});
          } else {
            useUserStore().token = '';
            useUserStore().roles = [];
            useUserStore().permissions = [];
            removeToken();
          }
          ElMessage.error(err);
          next({ path: '/' });
        } else {
          isRelogin.show = false;
          const accessRoutes = await usePermissionStore().generateRoutes();
          // 根据roles权限生成可访问的路由表
          accessRoutes.forEach((route) => {
            if (!isHttp(route.path)) {
              router.addRoute(route); // 动态添加可访问路由表
            }
          });
          // @ts-expect-error hack方法 确保addRoutes已完成
          next({ path: to.path, replace: true, params: to.params, query: to.query, hash: to.hash, name: to.name as string }); // hack方法 确保addRoutes已完成
        }
      } else {
        next();
      }
    }
  } else {
    // 没有token
    if (isWhiteList(to.path)) {
      // 在免登录白名单，直接进入
      // 注：/ssologin 即使不带 client/redirect 也直接渲染（页面内已统一处理 SDK 请求与直接访问两种场景）
      next();
    } else {
      // 复用 SDK 下发的原始 auth_request（若存在），避免重建后密文串变化、auth_mode 丢失
      const query = buildSsoLoginQuery({
        redirect: encodeURIComponent(to.fullPath || '/'),
        client,
        auth_mode: typeof to.query.auth_mode === 'string' ? to.query.auth_mode : ''
      });
      // 用对象式导航：敏感参数由 router 的 stringifyQuery 收进密文，地址栏不出现明文
      next({ path: '/ssologin', query }); // 否则全部重定向到登录页
      NProgress.done();
    }
  }
});

router.afterEach(() => {
  NProgress.done();
});
