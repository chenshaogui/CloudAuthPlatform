package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteRoleVo;

import java.util.List;
import java.util.Map;

/**
 * 角色服务
 *
 * @author Lion Li
 */
public interface RemoteRoleService {

    /**
     * 根据角色 ID 列表查询角色名称映射关系
     *
     * @param roleIds 角色 ID 列表
     * @return Map，其中 key 为角色 ID，value 为对应的角色名称
     */
    Map<Long, String> selectRoleNamesByIds(List<Long> roleIds);

    /**
     * 按用户 ID 查询其绑定的启用角色列表（不含逻辑删除角色）。
     * 用户不存在或无角色时返回空列表。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    List<RemoteRoleVo> selectRolesByUserId(Long userId);

    /**
     * 按用户登录账号（userName）+ 租户查询其绑定的启用角色列表。
     * 账号不存在或无角色时返回空列表。
     *
     * @param username 用户登录账号
     * @param tenantId 租户 ID
     * @return 角色列表
     */
    List<RemoteRoleVo> selectRolesByUsername(String username, String tenantId);

    /**
     * 按角色权限标识（roleKey）查询单个启用角色。
     * 支持短标识自动补全 clientId 匹配。角色不存在时返回 null。
     *
     * @param roleKey  角色权限标识（短标识或完整 {clientId}-标识）
     * @param clientId 客户端 ID（用于补全短标识）
     * @param tenantId 租户 ID
     * @return 单个角色；不存在时 null
     */
    RemoteRoleVo selectRoleByRoleKey(String roleKey, String clientId, String tenantId);

    /**
     * 分页查询角色下绑定的正常状态用户列表。
     * 按 roleId 或 roleKey（二选一，roleId 优先）定位角色，返回分页结果。
     *
     * @param roleId   角色 ID（可选）
     * @param roleKey  角色权限标识（可选）
     * @param clientId 客户端 ID（用于补全短标识）
     * @param tenantId 租户 ID
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return {@code {rows: List<RemoteUserVo>, total: Long}}
     */
    Map<String, Object> selectRoleUserPage(Long roleId, String roleKey, String clientId, String tenantId, int pageNum, int pageSize);

}
