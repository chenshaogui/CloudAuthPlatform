package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteKeyInfoVo;

/**
 * 密钥信息服务
 *
 * @author JLL
 */
public interface RemoteKeyInfoService {


    /**
     * 申请密钥
     * @param keyId
     * @param keyType
     * @param keyAlthm
     * @param clientId
     * @return
     */
    RemoteKeyInfoVo applyKey(String keyId, String keyType, String keyAlthm, String clientId) throws ServiceException;

    /**
     * 查看密钥
     * @param keyId
     * @param keyType
     * @param keyAlthm
     * @param clientId
     * @return
     */
    RemoteKeyInfoVo viewById(String keyId, String keyType, String keyAlthm, String clientId) throws ServiceException;

    /**
     * 密钥轮换
     * @param keyId
     * @param keyType
     * @param keyAlthm
     * @param clientId
     */
    RemoteKeyInfoVo rotationKey(String keyId, String keyType, String keyAlthm, String clientId) throws ServiceException;


    /**
     * 销毁密钥
     * @param keyId
     * @param keyType
     * @param keyAlthm
     * @param clientId
     */
    void distroyKey(String keyId, String keyType, String keyAlthm, String clientId) throws ServiceException;

}
