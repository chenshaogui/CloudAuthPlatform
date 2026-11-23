import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { KeyForm, KeyQuery, KeyVo, RotationKeyQuery, keyInfoData } from './types';

export const listKeyApi = (query?: KeyQuery): AxiosPromise<KeyVo[]> => {
  return request({
    url: '/system/keyInfo/list',
    method: 'get',
    params: query
  });
};

export const getKeyInfoApi = (id: string): AxiosPromise<KeyVo> => {
  return request({
    url: `/system/keyInfo/${id}`,
    method: 'get'
  });
};

export const addKeyInfoApi = (data: KeyForm) => {
  return request({
    url: '/system/keyInfo/applyKey',
    method: 'post',
    data: data
  });
};

export const listKeyInfoApi = (data: keyInfoData): AxiosPromise<KeyVo[]> => {
  return request({
    url: '/system/keyInfo/queryList',
    method: 'post',
    data
  });
};

/**
 * 密钥轮换
 * @param id
 */
export const rotationKeyApi = (id: string) => {
  return request({
    url: '/system/keyInfo/rotationKey',
    method: 'post',
    data: {
      id
    }
  });
};

/**
 * 密钥轮换列表
 * @param query
 */
export const listRotationKeyApi = (query?: RotationKeyQuery) => {
  return request({
    url: '/system/keyRotation/list',
    method: 'get',
    params: query
  });
};

/**
 * 密钥销毁
 * @param id
 */
export const distroyKeyApi = (id: string) => {
  return request({
    url: '/system/keyInfo/distroyKey',
    method: 'post',
    data: {
      id
    }
  });
};
