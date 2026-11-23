package io.gitee.chenshaogui.cloudauth.system.dubbo;

import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteClientService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteClientVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientService;
import org.springframework.stereotype.Service;

/**
 * 客户端服务
 *
 * @author Michelle.Chung
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteClientServiceImpl implements RemoteClientService {

    private final ISysClientService sysClientService;

    /**
     * 根据客户端id获取客户端详情
     */
    @Override
    public RemoteClientVo queryByClientId(String clientId) {
        SysClientVo vo = sysClientService.queryByClientId(clientId);
        if(vo == null){
            throw new ServiceException("不支持客户端id:"+clientId);
        }
        return MapstructUtils.convert(vo, RemoteClientVo.class);
    }

}
