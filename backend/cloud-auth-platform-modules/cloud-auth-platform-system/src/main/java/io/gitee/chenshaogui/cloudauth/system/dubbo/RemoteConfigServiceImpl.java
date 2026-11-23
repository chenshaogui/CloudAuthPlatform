package io.gitee.chenshaogui.cloudauth.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteConfigService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteConfigVo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysConfigBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysConfigVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysConfigService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 配置服务
 *
 * @author Michelle.Chung
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteConfigServiceImpl implements RemoteConfigService {

    private final ISysConfigService configService;

    /**
     * 获取注册开关
     */
    @Override
    public boolean selectRegisterEnabled(String tenantId) {
        return configService.selectRegisterEnabled(tenantId);
    }


    /**
     * 根据configKey获取配置值
     * @param tenantId 租户id
     * @param configKey 配置key
     * @return str
     */
    @Override
    public String selectConfigByKey(String tenantId, String configKey) {
        return TenantHelper.dynamic(tenantId, () -> configService.selectConfigByKey(configKey));
    }

    /**
     * 查询全部参数配置
     *
     * @param tenantId 租户id
     * @return 参数配置集合
     */
    @Override
    public List<RemoteConfigVo> selectConfigAll(String tenantId) {
        return TenantHelper.dynamic(tenantId, () -> {
            List<SysConfigVo> list = configService.selectConfigList(new SysConfigBo());
            return MapstructUtils.convert(list, RemoteConfigVo.class);
        });
    }

    /**
     * 根据参数ID查询参数配置
     *
     * @param tenantId 租户id
     * @param configId 参数配置ID
     * @return 参数配置
     */
    @Override
    public RemoteConfigVo selectConfigById(String tenantId, Long configId) {
        return TenantHelper.dynamic(tenantId, () -> {
            SysConfigVo vo = configService.selectConfigById(configId);
            return MapstructUtils.convert(vo, RemoteConfigVo.class);
        });
    }

    /**
     * 根据参数键名查询参数配置信息
     *
     * @param tenantId  租户id
     * @param configKey 参数键名
     * @return 参数配置
     */
    @Override
    public RemoteConfigVo selectConfigInfoByKey(String tenantId, String configKey) {
        return TenantHelper.dynamic(tenantId, () -> {
            SysConfigVo vo = configService.selectConfigInfoByKey(configKey);
            return MapstructUtils.convert(vo, RemoteConfigVo.class);
        });
    }

}
