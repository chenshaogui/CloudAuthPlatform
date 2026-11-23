package io.gitee.chenshaogui.cloudauth.system.dubbo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteRoleService;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteUserService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteRoleVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteUserVo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUser;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserRole;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysRoleVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色服务
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteRoleServiceImpl implements RemoteRoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    // 同应用内的 RemoteUserServiceImpl 同时标注 @Service，作为本地 Spring bean 注入（不走 Dubbo 自引用）
    private final RemoteUserService remoteUserService;

    /**
     * 根据角色 ID 列表查询角色名称映射关系
     *
     * @param roleIds 角色 ID 列表
     * @return Map，其中 key 为角色 ID，value 为对应的角色名称
     */
    @Override
    public Map<Long, String> selectRoleNamesByIds(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return Collections.emptyMap();
        }
        List<SysRole> list = roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>()
                .select(SysRole::getRoleId, SysRole::getRoleName)
                .in(SysRole::getRoleId, roleIds)
        );
        return StreamUtils.toMap(list, SysRole::getRoleId, SysRole::getRoleName);
    }

    /**
     * 按用户 ID 查询其绑定的启用角色列表。
     * sys_user / sys_role 均为租户隔离表，先忽略租户过滤按主键取用户归属租户，再切上下文查角色。
     */
    @Override
    public List<RemoteRoleVo> selectRolesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        SysUser user = TenantHelper.ignore(() -> userMapper.selectById(userId));
        if (user == null) {
            return Collections.emptyList();
        }
        return TenantHelper.dynamic(user.getTenantId(), () -> toRemoteRoleVos(roleMapper.selectRolesByUserId(userId)));
    }

    /**
     * 按登录账号 + 租户查询其绑定的启用角色列表。未上报租户时回退默认租户。
     */
    @Override
    public List<RemoteRoleVo> selectRolesByUsername(String username, String tenantId) {
        if (StringUtils.isBlank(username)) {
            return Collections.emptyList();
        }
        String tid = StringUtils.isNotBlank(tenantId) ? tenantId : TenantConstants.DEFAULT_TENANT_ID;
        return TenantHelper.dynamic(tid, () -> {
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, username));
            if (user == null) {
                return Collections.<RemoteRoleVo>emptyList();
            }
            return toRemoteRoleVos(roleMapper.selectRolesByUserId(user.getUserId()));
        });
    }

    /**
     * 按 roleKey 查询单个启用角色。roleKey 双格式兼容：原值 + {clientId}-原值 一并精确匹配，
     * 设计同注册自动绑定角色 autoBindRolesByRoleKeys。
     */
    @Override
    public RemoteRoleVo selectRoleByRoleKey(String roleKey, String clientId, String tenantId) {
        if (StringUtils.isBlank(roleKey)) {
            return null;
        }
        List<String> candidates = new ArrayList<>(2);
        candidates.add(roleKey.trim());
        if (StringUtils.isNotBlank(clientId)) {
            String full = clientId + "-" + roleKey.trim();
            if (!candidates.contains(full)) {
                candidates.add(full);
            }
        }
        return TenantHelper.dynamic(tenantId, () -> {
            SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                    .in(SysRole::getRoleKey, candidates)
                    .eq(SysRole::getStatus, SystemConstants.NORMAL)
                    .orderByAsc(SysRole::getRoleId)
                    .last("LIMIT 1"));
            return role == null ? null : toRemoteRoleVo(role);
        });
    }

    /**
     * 分页查询角色下绑定的正常状态用户列表。
     * <p>先定位角色（roleId 优先，否则按 roleKey），再查 sys_user_role 取全量 userId 内存切片，
     * 最后按用户详情切租户查询（selectListByIds 底层已过滤 status='0'）。若角色下用户量极大可后续优化为 DB 级分页。</p>
     */
    @Override
    public Map<String, Object> selectRoleUserPage(Long roleId, String roleKey, String clientId,
                                                  String tenantId, int pageNum, int pageSize) {
        // 1. 定位角色：roleId 优先，否则按 roleKey 查
        Long actualRoleId = roleId;
        if (actualRoleId == null) {
            RemoteRoleVo role = selectRoleByRoleKey(roleKey, clientId, tenantId);
            if (role == null) {
                return buildEmptyPage();
            }
            actualRoleId = role.getRoleId();
        }
        // 2. 查 sys_user_role 获取该角色下所有 userId（sys_user_role 非租户表，不切租户）
        final Long rid = actualRoleId;
        List<SysUserRole> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, rid));
        if (CollUtil.isEmpty(userRoles)) {
            return buildEmptyPage();
        }
        List<Long> allUserIds = userRoles.stream().map(SysUserRole::getUserId).distinct().toList();
        long total = allUserIds.size();
        // 3. 分页切片
        int fromIndex = (pageNum - 1) * pageSize;
        if (fromIndex < 0 || fromIndex >= total) {
            return buildPage(Collections.emptyList(), total);
        }
        int toIndex = (int) Math.min(fromIndex + (long) pageSize, total);
        List<Long> pageUserIds = new ArrayList<>(allUserIds.subList(fromIndex, toIndex));
        // 4. 查用户详情（sys_user 为租户表，切到目标租户；底层已过滤仅正常状态）
        List<RemoteUserVo> rows = TenantHelper.dynamic(tenantId, () ->
            remoteUserService.selectListByIds(pageUserIds));
        return buildPage(rows, total);
    }

    private Map<String, Object> buildPage(List<RemoteUserVo> rows, long total) {
        Map<String, Object> page = new HashMap<>(2);
        page.put("rows", rows);
        page.put("total", total);
        return page;
    }

    private Map<String, Object> buildEmptyPage() {
        return buildPage(Collections.emptyList(), 0L);
    }

    private RemoteRoleVo toRemoteRoleVo(SysRole role) {
        RemoteRoleVo vo = new RemoteRoleVo();
        vo.setRoleId(role.getRoleId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleKey(role.getRoleKey());
        vo.setDataScope(role.getDataScope());
        return vo;
    }

    private List<RemoteRoleVo> toRemoteRoleVos(List<SysRoleVo> list) {
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        List<RemoteRoleVo> result = new ArrayList<>(list.size());
        for (SysRoleVo r : list) {
            RemoteRoleVo vo = new RemoteRoleVo();
            vo.setRoleId(r.getRoleId());
            vo.setRoleName(r.getRoleName());
            vo.setRoleKey(r.getRoleKey());
            vo.setDataScope(r.getDataScope());
            result.add(vo);
        }
        return result;
    }

}
