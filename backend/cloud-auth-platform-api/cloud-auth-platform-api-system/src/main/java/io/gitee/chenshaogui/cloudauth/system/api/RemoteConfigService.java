package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteConfigVo;

import java.util.List;

/**
 * 配置服务
 *
 * @author Michelle.Chung
 */
public interface RemoteConfigService {

    /**
     * 获取注册开关
     * @param tenantId 租户id
     * @return true开启，false关闭
     */
    boolean selectRegisterEnabled(String tenantId);

    /**
     * 根据configKey获取配置值
     * @param tenantId 租户id
     * @param configKey 配置key
     * @return str
     */
    String selectConfigByKey(String tenantId, String configKey);

    /**
     * 查询全部参数配置
     *
     * @param tenantId 租户id
     * @return 参数配置集合
     */
    List<RemoteConfigVo> selectConfigAll(String tenantId);

    /**
     * 根据参数ID查询参数配置
     *
     * @param tenantId 租户id
     * @param configId 参数配置ID
     * @return 参数配置
     */
    RemoteConfigVo selectConfigById(String tenantId, Long configId);

    /**
     * 根据参数键名查询参数配置信息
     *
     * @param tenantId  租户id
     * @param configKey 参数键名
     * @return 参数配置
     */
    RemoteConfigVo selectConfigInfoByKey(String tenantId, String configKey);

}
