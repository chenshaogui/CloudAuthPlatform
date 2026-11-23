package io.gitee.chenshaogui.cloudauth.common.core.service.impl;

import org.apache.dubbo.config.annotation.DubboReference;
import io.gitee.chenshaogui.cloudauth.common.core.service.PermissionService;
import io.gitee.chenshaogui.cloudauth.system.api.RemotePermissionService;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 权限服务
 *
 * @author Lion Li
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    @DubboReference
    private RemotePermissionService remotePermissionService;

    @Override
    public Set<String> getRolePermission(Long userId) {
        return remotePermissionService.getRolePermission(userId);
    }

    @Override
    public Set<String> getMenuPermission(Long userId) {
        return remotePermissionService.getMenuPermission(userId);
    }

}
