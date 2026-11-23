package io.gitee.chenshaogui.cloudauth.casm.service;

import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoRspVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyAlthmEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyTypeEnum;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;

/**
 * 密钥管理Service接口
 *
 * @author JLL
 */
public interface IHSMService {

    /**
     * 初始化根密钥
     */
    void initRootKey() throws Exception;

    /**
     * 创建密钥
     */
    KeyInfoVo createKey(KeyTypeEnum keyTypeEnum, KeyAlthmEnum typeEnum, String keyId) throws ServiceException;

    /**
     * 申请密钥
     */
    KeyInfoVo applyKey(KeyTypeEnum keyTypeEnum, KeyAlthmEnum typeEnum, String clientId, Integer poolSize) throws ServiceException;


    /**
     * 使用密钥
     */
    KeyInfoVo useKey(KeyTypeEnum keyTypeEnum, String keyId, String clientId) throws ServiceException;

    /**
     * 获取根密钥
     */
    KeyInfoVo getRootKey() throws ServiceException;

    /**
     * 分发密钥
     */
    void disputeKey(KeyInfoVo keyInfo, String clientId) throws ServiceException;

    /**
     * 密钥轮换
     */
    KeyInfoVo rotationKey(KeyTypeEnum keyTypeEnum, String keyId, String clientId) throws ServiceException;

    /**
     * 密钥销毁
     */
    Boolean distroyKey(KeyTypeEnum keyTypeEnum, String keyId, String clientId) throws ServiceException;

    /**
     * 验证签名
     */
    Boolean verify(String publicKey, String text, String sign) throws ServiceException;

    /**
     * 重置根密钥
     */
    void resetRootKey() throws ServiceException;


    /**
     * 清理过期密钥
     */
    void expireKeys() throws ServiceException;

    /**
     * 转密钥信息
     */
    KeyInfoRspVo transKeyInfo(KeyInfoVo keyInfo, String transKeyId) throws ServiceException;

}
