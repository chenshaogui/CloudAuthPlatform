import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { ClientPublicConfigVo } from '@/views/auth/types';

/**
 * 按 clientId 拉取客户端公共配置（免登录）
 *
 * <p>鉴权中心前端在用户进入登录/注册/重置密码页面时调用，
 * 拿到 layoutConfig 后按 template 字段分发到对应模板渲染。</p>
 *
 * <p>后端对应接口：{@code GET /client/config/public?clientId=xxx}</p>
 *
 * @param clientId 客户端ID（必填）
 */
export function getClientPublicConfig(clientId: string): AxiosPromise<ClientPublicConfigVo> {
  return request({
    url: '/system/client/config/public',
    method: 'get',
    params: { clientId },
    headers: {
      isToken: false
    }
  });
}
