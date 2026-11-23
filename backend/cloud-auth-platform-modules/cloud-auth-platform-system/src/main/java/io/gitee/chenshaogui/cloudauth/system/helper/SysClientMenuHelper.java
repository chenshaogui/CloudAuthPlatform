package io.gitee.chenshaogui.cloudauth.system.helper;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserClientConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端只读查询助手（Component）
 *
 * <p>将 {@code ISysClientService} 中"被其他域服务引用"的部分下沉到本类，
 * 切断 {@code SysMenuServiceImpl → ISysClientService} 的循环依赖链路。
 * 写操作（增删改）和缓存仍由 {@code SysClientServiceImpl} 承担。</p>
 *
 * <p>字段命名与原 {@code ISysClientService} 保持一致，便于平移替换。</p>
 */
@Component
@RequiredArgsConstructor
public class SysClientMenuHelper {

    private final SysClientMapper baseMapper;
    private final SysClientRoleMapper sysClientRoleMapper;
    private final SysUserClientConfigMapper sysUserClientConfigMapper;

    /**
     * 按 clientId 查询客户端信息
     */
    public SysClientVo queryByClientId(String clientId) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<SysClient>().eq(SysClient::getClientId, clientId));
    }

    /**
     * 按 menuId 列表查询绑定的客户端列表
     */
    public List<SysClientVo> queryByMenuIds(List<Long> menuIdList) {
        if (CollUtil.isEmpty(menuIdList)) {
            return List.of();
        }
        return baseMapper.selectVoList(new LambdaQueryWrapper<SysClient>().in(SysClient::getMenuId, menuIdList));
    }

    /**
     * 查询当前用户可访问的客户端列表（角色授权 + 用户-客户端直接授权）
     */
    public List<SysClientVo> queryListByUserId(Long userId) {
        LambdaQueryWrapper<SysClient> lqw = Wrappers.lambdaQuery();
        Set<String> clientIdSet = new HashSet<>();
        clientIdSet.addAll(sysClientRoleMapper.selectClientIdsByUserId(userId));
        clientIdSet.addAll(sysUserClientConfigMapper.selectClientIdsByUserId(userId));
        if (CollUtil.isEmpty(clientIdSet)) {
            lqw.apply("1=0");
        } else {
            lqw.in(SysClient::getClientId, clientIdSet);
        }
        lqw.eq(SysClient::getDelFlag, SystemConstants.NORMAL);
        lqw.orderByAsc(SysClient::getId);
        return baseMapper.selectVoList(lqw);
    }
}
