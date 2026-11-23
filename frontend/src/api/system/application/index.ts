import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MenuQuery, MenuVO } from './types';

// 查询菜单列表
export const listByMenuIdApi = (query?: MenuQuery): AxiosPromise<MenuVO[]> => {
  return request({
    url: '/system/menu/listByMenuId',
    method: 'get',
    params: query
  });
};

// 查询应用菜单完整子树（按 parent_id 递归，供级联删除勾选）
export const listSubtreeByMenuIdApi = (menuId: string | number): AxiosPromise<MenuVO[]> => {
  return request({
    url: '/system/menu/subtreeByMenuId',
    method: 'get',
    params: { menuId }
  });
};
