package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.UserException;
import io.gitee.chenshaogui.cloudauth.system.api.domain.bo.RemoteUserBo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteUserInfoVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteUserVo;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.api.model.XcxLoginUser;

import java.util.List;
import java.util.Map;

/**
 * 用户服务
 *
 * @author Lion Li
 */
public interface RemoteUserService {

    /**
     * 通过用户名查询用户信息
     *
     * @param username 用户名
     * @param tenantId 租户id
     * @return 结果
     */
    LoginUser getUserInfo(String username, String tenantId) throws UserException;

    /**
     * 通过用户id查询用户信息
     *
     * @param userId   用户id
     * @param tenantId 租户id
     * @return 结果
     */
    LoginUser getUserInfo(Long userId, String tenantId) throws UserException;

    /**
     * 通过用户id查询用户信息
     *
     * @param loginId   登录id
     * @param tenantId 租户id
     * @return 结果
     */
    RemoteUserInfoVo getUserInfoByLoginToken(String loginId, String tenantId) throws UserException;

    /**
     * 通过手机号查询用户信息
     *
     * @param phonenumber 手机号
     * @param tenantId    租户id
     * @return 结果
     */
    LoginUser getUserInfoByPhonenumber(String phonenumber, String tenantId) throws UserException;

    /**
     * 通过邮箱查询用户信息
     *
     * @param email    邮箱
     * @param tenantId 租户id
     * @return 结果
     */
    LoginUser getUserInfoByEmail(String email, String tenantId) throws UserException;

    /**
     * 通过openid查询用户信息
     *
     * @param openid openid
     * @return 结果
     */
    XcxLoginUser getUserInfoByOpenid(String openid) throws UserException;

    /**
     * 注册用户信息
     *
     * @param remoteUserBo 用户信息
     * @return 新建/同步用户的 userId（失败抛异常，不会返回 null）
     */
    Long registerUserInfo(RemoteUserBo remoteUserBo) throws UserException, ServiceException;

    /**
     * addUser 幂等补绑：按账号 + 租户查找已存在用户，存在则按 roleKeys 追加绑定角色（不覆盖已有绑定），
     * 返回该用户 userId；账号不存在时返回 null（调用方继续走新建用户流程）。
     * <p>仅供认证中心 {@code addUser} 总线 handler 在携带角色参数时使用；roleKeys 为空时
     * 只返回 userId，由 handler 保持原有的"账号已存在"报错语义。</p>
     *
     * @param remoteUserBo 需包含 userName、tenantId、clientId、roleKeys
     * @return 已存在用户的 userId；账号不存在返回 null
     */
    Long bindRolesIfUserExists(RemoteUserBo remoteUserBo);

    /**
     * 修改用户状态（启用/停用）——供认证中心 SDK 总线“updateUserStatus”调用，操作其他用户。
     * <p>需传入目标 userId 与 status（0正常 / 1停用）；租户隔离由服务端 handler 按操作人会话
     * 强制约束（忽略 body 的 tenantId），避免跨租户误操作。返回是否更新成功。</p>
     *
     * @param remoteUserBo 需包含 userId、status
     * @return 是否更新成功
     */
    Boolean updateUserStatus(RemoteUserBo remoteUserBo);

    /**
     * 通过用户账号解析用户ID——供认证中心 SDK 总线“updateUserStatus”按 userName 操作前定位目标用户。
     * <p>在 {@code tenantId} 对应租户内按账号精确匹配（仅命中未删除用户），跨租户 / 不存在 / 已删除
     * 的账号返回 {@code null}，由调用方统一按“用户不存在或无权限”处理，避免越权定位。</p>
     *
     * @param userName 用户账号
     * @param tenantId 租户ID（强制约束，跨租户解析不到）
     * @return 用户ID；未命中时返回 null
     */
    Long selectUserIdByUserName(String userName, String tenantId);

    /**
     * 删除用户（角色解绑 / 删用户）——供认证中心 SDK 总线“deleteUser”调用，操作其他用户。
     * <p>规则：用户仅绑定入参 {@code roleId} 这一个角色时，解除后无角色，<b>删除用户</b>；
     * 用户绑定多个角色时，<b>仅解除该角色绑定</b>；用户不属于该角色时抛 {@link ServiceException}（无需解除）。
     * 租户隔离由服务端 handler 按操作人会话强制约束（忽略 body 的 tenantId），避免跨租户误操作。</p>
     *
     * @param remoteUserBo 需包含 userId（或 userName）、roleId
     * @return 是否处理成功（true=解绑或删除成功）
     * @throws ServiceException 用户不属于该角色 / 删除用户失败等业务异常
     */
    Boolean deleteUser(RemoteUserBo remoteUserBo) throws ServiceException;

    /**
     * 通过userId查询用户账户
     *
     * @param userId 用户id
     * @return 结果
     */
    String selectUserNameById(Long userId);

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userId 用户id
     * @return 结果
     */
    String selectNicknameById(Long userId);

    /**
     * 通过用户ID查询用户账户
     *
     * @param userIds 用户ID 多个用逗号隔开
     * @return 用户名称
     */
    String selectNicknameByIds(String userIds);

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    String selectPhonenumberById(Long userId);

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    String selectEmailById(Long userId);

    /**
     * 更新用户信息
     *
     * @param userId 用户ID
     * @param ip     IP地址
     */
    void recordLoginInfo(Long userId, String ip);

    /**
     * 通过用户ID查询用户列表
     *
     * @param userIds 用户ids
     * @return 用户列表
     */
    List<RemoteUserVo> selectListByIds(List<Long> userIds);

    /**
     * 通过角色ID查询用户ID
     *
     * @param roleIds 角色ids
     * @return 用户ids
     */
    List<Long> selectUserIdsByRoleIds(List<Long> roleIds);

    /**
     * 通过角色ID查询用户
     *
     * @param roleIds 角色ids
     * @return 用户
     */
    List<RemoteUserVo> selectUsersByRoleIds(List<Long> roleIds);

    /**
     * 通过部门ID查询用户
     *
     * @param deptIds 部门ids
     * @return 用户
     */
    List<RemoteUserVo> selectUsersByDeptIds(List<Long> deptIds);

    /**
     * 通过岗位ID查询用户
     *
     * @param postIds 岗位ids
     * @return 用户
     */
    List<RemoteUserVo> selectUsersByPostIds(List<Long> postIds);

    boolean isSimplePassword(String password, String tenantId);

    String getSimpleReason(String password, String tenantId);

    /**
     * 重置用户密码（认证中心免登改密链路调用）
     *
     * @param userId   用户ID
     * @param password 已加密(BCrypt)的新密码
     * @param tenantId 租户ID（sys_user 为租户隔离表）
     * @return 是否更新成功
     */
    boolean resetUserPwd(Long userId, String password, String tenantId);

    /**
     * 修改用户个人资料（昵称/手机号/邮箱/性别）——供认证中心 SDK 总线“修改当前登录人信息”调用。
     * <p>仅更新资料字段，不涉及角色/状态/账号；手机号、邮箱做唯一性校验（按 userId 排除自身）。
     * 采用按需更新：只改传入的字段，未传（null/空）的字段保持原值不动。</p>
     *
     * @param remoteUserBo 需包含 userId、tenantId 及待更新资料字段
     * @return 是否更新成功
     */
    boolean updateUserProfile(RemoteUserBo remoteUserBo);

    /**
     * 根据用户 ID 列表查询用户名称映射关系
     *
     * @param userIds 用户 ID 列表
     * @return Map，其中 key 为用户 ID，value 为对应的用户名称
     */
    Map<Long, String> selectUserNamesByIds(List<Long> userIds);

}
