package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserClientConfigBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserClientConfigVo;

import java.util.List;

/**
 * 用户-客户端配置Service
 */
public interface ISysUserClientConfigService {
    /**
     * 保存/更新用户-客户端配置（存在则更新，不存在则新增）
     */
    boolean saveOrUpdateConfig(SysUserClientConfigBo config);

    /**
     * 根据用户ID+客户端ID查询配置
     */
    SysUserClientConfigVo getConfigByUserIdAndClientId(Long userId, String clientId);

    /**
     * 根据用户ID查询所有客户端配置
     */
    List<SysUserClientConfigVo> listByUserId(Long userId);

    /**
     * 批量保存用户-客户端配置
     */
    boolean batchSaveConfig(List<SysUserClientConfigBo> configList);

    /**
     * 校验配置是否重复（用户+客户端）
     */
    boolean checkConfigUnique(Long userId, String clientId, Long excludeId);

    /**
     * 根据id查询客户端信息
     * @param id 唯一id
     * @return SysUserClientConfigVo
     */
    SysUserClientConfigVo getConfigById(Long id);

    int delete(SysUserClientConfigBo bo);

}
