package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserExportVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserVo;

import java.util.List;

/**
 * 用户 业务层
 *
 * @author Lion Li
 */
public interface ISysUserService {

    /**
     * 根据条件分页查询用户列表
     *
     * @param user      用户信息
     * @param pageQuery 发呢也
     * @return 用户信息
     */
    TableDataInfo<SysUserVo> selectPageUserList(SysUserBo user, PageQuery pageQuery);

    /**
     * 导出用户列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    List<SysUserExportVo> selectUserExportList(SysUserBo user);

    /**
     * 根据条件分页查询已分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    TableDataInfo<SysUserVo> selectAllocatedList(SysUserBo user, PageQuery pageQuery);

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    TableDataInfo<SysUserVo> selectUnallocatedList(SysUserBo user, PageQuery pageQuery);

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    SysUserVo selectUserByUserName(String userName);

    /**
     * 通过手机号查询用户
     *
     * @param phonenumber 手机号
     * @return 用户对象信息
     */
    SysUserVo selectUserByPhonenumber(String phonenumber);

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    SysUserVo selectUserById(Long userId);

    /**
     * 通过用户ID串查询用户
     *
     * @param userIds 用户ID串
     * @param deptId  部门id
     * @return 用户列表信息
     */
    List<SysUserVo> selectUserByIds(List<Long> userIds, Long deptId);

    /**
     * 根据用户ID查询用户所属角色组
     *
     * @param userId 用户ID
     * @return 结果
     */
    String selectUserRoleGroup(Long userId);

    /**
     * 根据用户ID查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 结果
     */
    String selectUserPostGroup(Long userId);

    /**
     * 校验用户名称是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkUserNameUnique(SysUserBo user);

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkPhoneUnique(SysUserBo user);

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkEmailUnique(SysUserBo user);

    /**
     * 校验用户是否允许操作
     *
     * @param userId 用户ID
     */
    void checkUserAllowed(Long userId);

    /**
     * 校验用户是否有数据权限
     *
     * @param userId 用户id
     */
    void checkUserDataScope(Long userId);

    /**
     * 新增用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    int insertUser(SysUserBo user);

    /**
     * 注册用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean registerUser(SysUserBo user, String tenantId);

    /**
     * addUser 幂等补绑：按账号查询已存在用户，存在则按 roleKeys 追加绑定角色并返回既有 userId。
     * <p>不覆盖、不删除已有的用户角色绑定，只补绑缺失的角色；账号不存在返回 null，
     * 由调用方继续走新建用户流程（见 RemoteUserService#bindRolesIfUserExists）。</p>
     *
     * @param userName 用户账号
     * @param roleKeys 角色标识列表（sys_role.role_key，支持短标识或完整 {clientId}-标识）
     * @param tenantId 租户ID
     * @param clientId 客户端ID（短标识补全用）
     * @return 已存在用户的 userId；账号不存在时返回 null
     */
    Long bindRolesIfUserExists(String userName, List<String> roleKeys, String tenantId, String clientId);

    /**
     * 修改用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    int updateUser(SysUserBo user);

    /**
     * 用户授权角色
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     */
    void insertUserAuth(Long userId, Long[] roleIds);

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 帐号状态
     * @return 结果
     */
    int updateUserStatus(Long userId, String status);

    /**
     * 修改用户状态（按租户隔离，供总线 updateUserStatus 调用）
     *
     * @param userId   用户ID
     * @param status   帐号状态（0正常 / 1停用）
     * @param tenantId 操作人租户ID（用于租户隔离校验，传 null 不限定）
     * @return 结果
     */
    int updateUserStatus(Long userId, String status, String tenantId);

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    int updateUserProfile(SysUserBo user);

    /**
     * 修改用户头像
     *
     * @param userId 用户ID
     * @param avatar 头像地址
     * @return 结果
     */
    boolean updateUserAvatar(Long userId, Long avatar);

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 密码
     * @return 结果
     */
    int resetUserPwd(Long userId, String password);

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     * @return 结果
     */
    int deleteUserById(Long userId);

    /**
     * 批量删除用户信息
     *
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    int deleteUserByIds(Long[] userIds);

    /**
     * 解除单个用户与单个角色的绑定（仅删 sys_user_role 关联，保留用户），并同步权限（踢下线 + 通知刷新）。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 解除的关联行数
     */
    int unbindUserRole(Long userId, Long roleId);

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    String selectUserNameById(Long userId);

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    String selectNicknameById(Long userId);

    /**
     * 通过用户ID查询用户账户
     *
     * @param userIds 用户ID
     * @return 用户账户
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
     * 通过部门id查询当前部门所有用户
     *
     * @param deptId 部门id
     * @return 结果
     */
    List<SysUserVo> selectUserListByDept(Long deptId);

    /**
     * 通过角色ID查询用户ID
     *
     * @param roleIds 角色ids
     * @return 用户ids
     */
    List<Long> selectUserIdsByRoleIds(List<Long> roleIds);

    /**
     * 搜索数据
     * @param user 用户
     * @param pageQuery 请求
     * @return page
     */
    TableDataInfo<SysUserVo> searchPageUserList(SysUserBo user, PageQuery pageQuery);
}
