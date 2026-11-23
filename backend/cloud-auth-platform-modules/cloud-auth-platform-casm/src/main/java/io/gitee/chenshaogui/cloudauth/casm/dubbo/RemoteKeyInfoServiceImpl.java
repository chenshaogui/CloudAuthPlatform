package io.gitee.chenshaogui.cloudauth.casm.dubbo;

import io.gitee.chenshaogui.cloudauth.casm.domain.bo.KeyInfoReqBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoRspVo;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysKeyInfoService;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteKeyInfoService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteKeyInfoVo;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 密钥信息服务
 *
 * @author JLL
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteKeyInfoServiceImpl implements RemoteKeyInfoService {

    private final ISysKeyInfoService sysKeyInfoService;

    @Override
    public RemoteKeyInfoVo applyKey(String keyId, String keyType, String keyAlthm, String clientId)  throws ServiceException {
        KeyInfoReqBo reqBo = new KeyInfoReqBo();
        reqBo.setClientId(clientId);
        reqBo.setKeyId(keyId);
        reqBo.setKeyType(keyType);
        reqBo.setKeyAlthm(keyAlthm);
        KeyInfoRspVo keyInfoRspVo = sysKeyInfoService.applyKey(reqBo);
        return MapstructUtils.convert(keyInfoRspVo, RemoteKeyInfoVo.class);
    }

    @Override
    public RemoteKeyInfoVo viewById(String keyId, String keyType, String keyAlthm, String clientId)  throws ServiceException {
        KeyInfoReqBo reqBo = new KeyInfoReqBo();
        reqBo.setClientId(clientId);
        reqBo.setKeyId(keyId);
        reqBo.setKeyType(keyType);
        reqBo.setKeyAlthm(keyAlthm);
        KeyInfoRspVo keyInfoRspVo = sysKeyInfoService.useKey(reqBo);
        return MapstructUtils.convert(keyInfoRspVo, RemoteKeyInfoVo.class);
    }

    @Override
    public RemoteKeyInfoVo rotationKey(String keyId, String keyType, String keyAlthm, String clientId)  throws ServiceException {
        KeyInfoReqBo reqBo = new KeyInfoReqBo();
        reqBo.setClientId(clientId);
        reqBo.setKeyId(keyId);
        reqBo.setKeyType(keyType);
        reqBo.setKeyAlthm(keyAlthm);
        KeyInfoRspVo keyInfoRspVo = null;
        try {
            keyInfoRspVo = sysKeyInfoService.rotationKey(reqBo);
        } catch (Exception e) {
            throw new ServiceException(e.getMessage(), 105);
        }
        if (keyInfoRspVo != null) {
            return MapstructUtils.convert(keyInfoRspVo, RemoteKeyInfoVo.class);
        }
        return null;
    }

    @Override
    public void distroyKey(String keyId, String keyType, String keyAlthm, String clientId)  throws ServiceException {
        KeyInfoReqBo reqBo = new KeyInfoReqBo();
        reqBo.setClientId(clientId);
        reqBo.setKeyId(keyId);
        reqBo.setKeyType(keyType);
        reqBo.setKeyAlthm(keyAlthm);
        try {
            sysKeyInfoService.distroyKey(reqBo);
        } catch (Exception e) {
            throw new ServiceException(e.getMessage(), 105);
        }

    }
}
