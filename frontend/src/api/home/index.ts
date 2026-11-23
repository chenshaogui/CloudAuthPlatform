import request from '@/utils/request';

// 首页工作台：已绑定客户端的应用入口
export function listBoundApplicationsApi() {
  return request({
    url: '/system/menu/listBoundApplications',
    method: 'get'
  });
}
