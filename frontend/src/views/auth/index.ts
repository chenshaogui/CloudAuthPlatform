import type { Component } from 'vue';
import DefaultLogin from '@/views/auth/templates/default/Login.vue';
import DefaultRegister from '@/views/auth/templates/default/Register.vue';
import DefaultResetPwd from '@/views/auth/templates/default/ResetPwd.vue';
import LiaoyuntongLogin from '@/views/auth/templates/liaoyuntong/Login.vue';
import LiaoyuntongRegister from '@/views/auth/templates/liaoyuntong/Register.vue';
import LiaoyuntongResetPwd from '@/views/auth/templates/liaoyuntong/ResetPwd.vue';

/**
 * 模板注册表
 *
 * <p>每套模板由 3 个组件组成（登录/注册/重置密码），存放在
 * {@code src/views/auth/templates/<name>/} 下。</p>
 *
 * <p>后端 {@code sys_client.layout_config.template} 字段决定使用哪套模板。
 * 若 template 名未在本表中注册，自动回退到 {@code default} 模板。</p>
 */
export interface AuthTemplate {
  login: Component;
  register: Component;
  resetPwd: Component;
}

export const templates: Record<string, AuthTemplate> = {
  default: {
    login: DefaultLogin,
    register: DefaultRegister,
    resetPwd: DefaultResetPwd
  },
  liaoyuntong: {
    login: LiaoyuntongLogin,
    register: LiaoyuntongRegister,
    resetPwd: LiaoyuntongResetPwd
  }
};

/**
 * 根据 template 名获取对应模板（找不到则回退 default）
 */
export const getTemplate = (name?: string | null): AuthTemplate => {
  if (name && templates[name]) return templates[name];
  return templates.default;
};

/**
 * 已注册的模板名列表（供管理端"布局配置"下拉使用）
 */
export const TEMPLATE_NAMES: string[] = Object.keys(templates);
