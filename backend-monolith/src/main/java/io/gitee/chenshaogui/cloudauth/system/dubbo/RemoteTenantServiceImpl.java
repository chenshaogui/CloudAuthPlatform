package io.gitee.chenshaogui.cloudauth.system.dubbo;

import lombok.RequiredArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteTenantService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteTenantVo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysTenantBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysTenantVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysTenantService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author zhujie
 */
@RequiredArgsConstructor
@Service

public class RemoteTenantServiceImpl implements RemoteTenantService {

    private final ISysTenantService tenantService;

    /**
     * 根据租户id获取租户详情
     */
    @Override
    public RemoteTenantVo queryByTenantId(String tenantId) {
        SysTenantVo vo = tenantService.queryByTenantId(tenantId);
        return MapstructUtils.convert(vo, RemoteTenantVo.class);
    }

    /**
     * 获取租户列表
     */
    @Override
    public List<RemoteTenantVo> queryList() {
        List<SysTenantVo> list = tenantService.queryList(new SysTenantBo());
        return MapstructUtils.convert(list, RemoteTenantVo.class);
    }

}
