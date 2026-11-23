package io.gitee.chenshaogui.cloudauth.casm.service;

import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysClientVo;

/**
 * 客户端管理Service接口
 *
 * @author Michelle.Chung
 */
public interface ISysClientService {

    /**
     * 查询客户端管理
     */
    SysClientVo queryById(Long id);

    /**
     * 查询客户端信息基于客户端id
     */
    SysClientVo queryByClientId(String clientId);
}
