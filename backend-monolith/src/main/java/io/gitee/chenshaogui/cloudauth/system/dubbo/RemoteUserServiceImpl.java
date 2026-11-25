package io.gitee.chenshaogui.cloudauth.system.dubbo;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteUserInfoVo;
import io.gitee.chenshaogui.cloudauth.system.checker.PasswordStrengthChecker;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.*;
import lombok.RequiredArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.enums.UserStatus;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.UserException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.DateUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.helper.DataPermissionHelper;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteUserService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.bo.RemoteUserBo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteUserVo;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.api.model.PostDTO;
import io.gitee.chenshaogui.cloudauth.system.api.model.RoleDTO;
import io.gitee.chenshaogui.cloudauth.system.api.model.XcxLoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUser;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserPost;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserRole;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserBo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserPostMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.service.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 用户服务
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service

public class RemoteUserServiceImpl implements RemoteUserService {

    private final ISysUserService userService;
    private final ISysPermissionService permissionService;
    private final ISysConfigService configService;
    private final ISysRoleService roleService;
    private final ISysDeptService deptService;
    private final ISysPostService postService;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final PasswordStrengthChecker passwordStrengthChecker;

    /**
     * 通过用户名查询用户信息
     *
     * @param username 用户名
     * @param tenantId 租户id
     * @return 结果
     */
    @Override
    public LoginUser getUserInfo(String username, String tenantId) throws UserException {
        return TenantHelper.dynamic(tenantId, () -> {
            SysUserVo sysUser = userMapper.selectVoOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, username));
            if (ObjectUtil.isNull(sysUser)) {
                throw new UserException("user.not.exists", username);
            }
            if (UserStatus.DISABLE.getCode().equals(sysUser.getStatus())) {
                throw new UserException("user.blocked", username);
            }
            // 框架登录不限制从什么表查询 只要最终构建出 LoginUser 即可
            // 此处可根据登录用户的数据不同 自行创建 loginUser 属性不够用继承扩展就行了
            return buildLoginUser(sysUser);
        });
    }

    /**
     * 通过用户id查询用户信息
     *
     * @param userId   用户id
     * @param tenantId 租户id
     * @return 结果
     */
    @Override
    public LoginUser getUserInfo(Long userId, String tenantId) throws UserException {
        return TenantHelper.dynamic(tenantId, () -> {
            SysUserVo sysUser = userMapper.selectVoById(userId);
            if (ObjectUtil.isNull(sysUser)) {
                throw new UserException("user.not.exists", "");
            }
            if (UserStatus.DISABLE.getCode().equals(sysUser.getStatus())) {
                throw new UserException("user.blocked", sysUser.getUserName());
            }
            // 框架登录不限制从什么表查询 只要最终构建出 LoginUser 即可
            // 此处可根据登录用户的数据不同 自行创建 loginUser 属性不够用继承扩展就行了
            return buildLoginUser(sysUser);
        });
    }

    /**
     * 通过用户id查询用户信息
     *
     * @param loginToken   登录id
     * @param tenantId 租户id
     * @return 结果
     */
    @Override
    public RemoteUserInfoVo getUserInfoByLoginToken(String loginToken, String tenantId) throws UserException {
        return TenantHelper.dynamic(tenantId, () -> {
            UserInfoVo userInfoVo = new UserInfoVo();
            LoginUser loginUser = LoginHelper.getLoginUser(loginToken);
            // 注意：必须使用带 LoginUser 参数的重载。
            // 本方法由 SSO pushS 消息（服务间签名调用，无用户 token）经 Dubbo 调入，
            // 当前线程上下文没有用户 token，无参版 isSuperAdmin() 会从上下文取 token，
            // 空 token 走 StpUtil.getTokenSession() 时 checkActiveTimeout 查不到
            // last-active key，抛出误导性的 NotLoginException: token 已被冻结。
            if (TenantHelper.isEnable() && LoginHelper.isSuperAdmin(loginUser)) {
                // 超级管理员 如果重新加载用户信息需清除动态租户
                TenantHelper.clearDynamic();
            }
            SysUserVo user = DataPermissionHelper.ignore(() -> userService.selectUserById(loginUser.getUserId()));
            if (ObjectUtil.isNull(user)) {
                throw new NotPermissionException("没有权限访问用户数据!");
            }
            user.setRoles(roleService.selectRolesByUserId(user.getUserId()));
            userInfoVo.setUser(user);
            userInfoVo.setPermissions(loginUser.getMenuPermission());
            userInfoVo.setRoles(loginUser.getRolePermission());
            RemoteUserInfoVo remoteUserInfoVo = MapstructUtils.convert(userInfoVo, RemoteUserInfoVo.class);
            // 补充部门名称与岗位名称列表（SysUserVo.deptName 为 @Translation，仅序列化惰性生效，此处显式填充）
            RemoteUserVo remoteUser = remoteUserInfoVo == null ? null : remoteUserInfoVo.getUser();
            if (remoteUser != null) {
                List<RemoteUserVo> single = new ArrayList<>();
                single.add(remoteUser);
                fillDeptNames(single);
                fillPostNames(single);
            }
            return remoteUserInfoVo;
        });
    }

    /**
     * 通过手机号查询用户信息
     *
     * @param phonenumber 手机号
     * @param tenantId    租户id
     * @return 结果
     */
    @Override
    public LoginUser getUserInfoByPhonenumber(String phonenumber, String tenantId) throws UserException {
        return TenantHelper.dynamic(tenantId, () -> {
            // 用 selectVoList 而非 selectVoOne：手机号被多个账号占用时 selectOne 会抛 TooManyResultsException，
            // 被上层 catch 后误报成"未注册或不可用"，这里显式区分"不存在"与"重复绑定"
            List<SysUserVo> users = userMapper.selectVoList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhonenumber, phonenumber));
            if (CollUtil.isEmpty(users)) {
                throw new UserException("user.not.exists", phonenumber);
            }
            if (users.size() > 1) {
                throw new ServiceException("该手机号已绑定多个账号，请使用账号密码登录或联系管理员处理");
            }
            SysUserVo sysUser = users.get(0);
            if (UserStatus.DISABLE.getCode().equals(sysUser.getStatus())) {
                throw new UserException("user.blocked", phonenumber);
            }
            // 框架登录不限制从什么表查询 只要最终构建出 LoginUser 即可
            // 此处可根据登录用户的数据不同 自行创建 loginUser 属性不够用继承扩展就行了
            return buildLoginUser(sysUser);
        });
    }

    /**
     * 重置用户密码：认证中心免登改密链路经 Dubbo 调入，按 tenantId 动态切换租户，
     * 忽略数据权限（改密入口已过业务校验），落库后由 ISysUserService 负责踢下线与权限同步通知。
     */
    @Override
    public boolean resetUserPwd(Long userId, String password, String tenantId) {
        return TenantHelper.dynamic(tenantId, () -> {
            int rows = DataPermissionHelper.ignore(() -> userService.resetUserPwd(userId, password));
            return rows > 0;
        });
    }

    /**
     * 通过邮箱查询用户信息
     *
     * @param email    邮箱
     * @param tenantId 租户id
     * @return 结果
     */
    @Override
    public LoginUser getUserInfoByEmail(String email, String tenantId) throws UserException {
        return TenantHelper.dynamic(tenantId, () -> {
            SysUserVo user = userMapper.selectVoOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
            if (ObjectUtil.isNull(user)) {
                throw new UserException("user.not.exists", email);
            }
            if (UserStatus.DISABLE.getCode().equals(user.getStatus())) {
                throw new UserException("user.blocked", email);
            }
            // 框架登录不限制从什么表查询 只要最终构建出 LoginUser 即可
            // 此处可根据登录用户的数据不同 自行创建 loginUser 属性不够用继承扩展就行了
            return buildLoginUser(user);
        });
    }

    /**
     * 通过openid查询用户信息
     *
     * @param openid openid
     * @return 结果
     */
    @Override
    public XcxLoginUser getUserInfoByOpenid(String openid) throws UserException {
        // todo 自行实现 userService.selectUserByOpenid(openid);
        SysUser sysUser = new SysUser();
        if (ObjectUtil.isNull(sysUser)) {
            // todo 用户不存在 业务逻辑自行实现
        }
        if (UserStatus.DISABLE.getCode().equals(sysUser.getStatus())) {
            // todo 用户已被停用 业务逻辑自行实现
        }
        // 框架登录不限制从什么表查询 只要最终构建出 LoginUser 即可
        // 此处可根据登录用户的数据不同 自行创建 loginUser 属性不够用继承扩展就行了
        XcxLoginUser loginUser = new XcxLoginUser();
        loginUser.setUserId(sysUser.getUserId());
        loginUser.setUsername(sysUser.getUserName());
        loginUser.setNickname(sysUser.getNickName());
        loginUser.setUserType(sysUser.getUserType());
        loginUser.setOpenid(openid);
        return loginUser;
    }

    /**
     * 注册用户信息
     *
     * @param remoteUserBo 用户信息
     * @return 结果
     */
    @Override
    public Long registerUserInfo(RemoteUserBo remoteUserBo) throws UserException, ServiceException {
        SysUserBo sysUserBo = MapstructUtils.convert(remoteUserBo, SysUserBo.class);
        sysUserBo.setClientId(remoteUserBo.getClientId());
        // 显式透传 roleKeys：防 MapStruct 生成映射类未重新生成时丢字段
        sysUserBo.setRoleKeys(remoteUserBo.getRoleKeys());
        String username = sysUserBo.getUserName();
        boolean exist = TenantHelper.dynamic(remoteUserBo.getTenantId(), () -> {
            return userMapper.exists(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserName, sysUserBo.getUserName()));
        });
        if (exist) {
            throw new UserException("user.register.save.error", username);
        }
        // 手机号唯一性校验：多个账号共用一个手机号会导致按手机号登录/找回密码无法定位用户
        if (StringUtils.isNotBlank(sysUserBo.getPhonenumber())) {
            Boolean phoneUsed = TenantHelper.dynamic(remoteUserBo.getTenantId(), () ->
                userMapper.exists(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhonenumber, sysUserBo.getPhonenumber())));
            if (phoneUsed) {
                throw new ServiceException("该手机号已被其他账号使用，请更换手机号或直接登录");
            }
        }
        if(StringUtils.isBlank(sysUserBo.getPassword())){
            TenantHelper.dynamic(remoteUserBo.getTenantId(), () -> {
                String initPassword = configService.selectConfigByKey("sys.user.initPassword");
                // 默认密码依赖参数配置 sys.user.initPassword，缺失时直接报错，不做硬编码兜底
                if (StringUtils.isBlank(initPassword)) {
                    throw new ServiceException("未配置默认密码（sys.user.initPassword），请联系管理员");
                }
                String hashpw = BCrypt.hashpw(initPassword);
                sysUserBo.setPassword(hashpw);
            });
        }
        boolean res = userService.registerUser(sysUserBo, remoteUserBo.getTenantId());
        // 回写 newUserId 到入参（仅本 JVM 内有效），并作为返回值显式透出：
        // 本方法经 Dubbo RPC 调用，参数回写无法跨越序列化边界回到调用方，必须靠返回值带回 userId
        remoteUserBo.setUserId(sysUserBo.getUserId());
        if (!res) {
            throw new ServiceException("新增用户失败");
        }
        return remoteUserBo.getUserId();
    }

    /**
     * addUser 幂等补绑：账号已存在时按 roleKeys 追加绑定角色并返回既有 userId，
     * 账号不存在时返回 null，交由 addUser handler 继续走 registerUserInfo 新建用户。
     */
    @Override
    public Long bindRolesIfUserExists(RemoteUserBo remoteUserBo) {
        if (remoteUserBo == null || StringUtils.isBlank(remoteUserBo.getUserName())) {
            return null;
        }
        return userService.bindRolesIfUserExists(
            remoteUserBo.getUserName(),
            remoteUserBo.getRoleKeys(),
            remoteUserBo.getTenantId(),
            remoteUserBo.getClientId());
    }

    /**
     * 启用/停用其他用户：按目标 userId + status 更新。租户隔离由调用方（SsoServerController handler）
     * 以操作人会话租户为准 set 进 bo.tenantId，并在 {@code userService.updateUserStatus} 内做 eq 校验，
     * 避免跨租户误操作。
     * <p>本方法经总线由 SSO pushS 调用（服务间签名，无用户 token），数据权限拦截器取不到登录态会抛
     * NotLoginException: token 已被冻结，故用 {@link DataPermissionHelper#ignore} 忽略数据权限。
     */
    @Override
    public Boolean updateUserStatus(RemoteUserBo remoteUserBo) {
        return TenantHelper.dynamic(remoteUserBo.getTenantId(), () ->
            DataPermissionHelper.ignore(() -> userService.updateUserStatus(
                remoteUserBo.getUserId(), remoteUserBo.getStatus(), remoteUserBo.getTenantId())) > 0);
    }

    /**
     * 通过用户账号解析用户ID：在指定租户内按账号精确匹配未删除用户，供“按 userName 启用/停用”定位目标。
     * 跨租户 / 不存在 / 已删除的账号返回 null（统一按“用户不存在或无权限”处理）。
     */
    @Override
    public Long selectUserIdByUserName(String userName, String tenantId) {
        if (StringUtils.isBlank(userName)) {
            return null;
        }
        return TenantHelper.dynamic(tenantId, () -> {
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUserId)
                .eq(SysUser::getUserName, userName)
                .eq(SysUser::getDelFlag, SystemConstants.NORMAL)
                .last("LIMIT 1"));
            return user == null ? null : user.getUserId();
        });
    }

    /**
     * 删除用户（角色解绑 / 删用户）：按入参 roleId 决定“仅解绑角色”还是“删除用户”。
     * 租户隔离由调用方（SsoServerController handler）以操作人会话租户为准 set 进 bo.tenantId，
     * 并在 {@code TenantHelper.dynamic} 内执行，避免跨租户误操作。
     * <p>同 {@link #updateUserStatus}，本方法经总线由 SSO pushS 调用，用
     * {@link DataPermissionHelper#ignore} 忽略数据权限，避免无用户 token 时拦截器抛 NotLoginException。
     */
    @Override
    public Boolean deleteUser(RemoteUserBo remoteUserBo) throws ServiceException {
        return TenantHelper.dynamic(remoteUserBo.getTenantId(), () ->
            DataPermissionHelper.ignore(() -> {
                Long userId = remoteUserBo.getUserId();
                Long roleId = remoteUserBo.getRoleId();
                // 查询用户当前绑定角色
                List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
                List<Long> roleIds = StreamUtils.toList(userRoles, SysUserRole::getRoleId);
                if (!roleIds.contains(roleId)) {
                    throw new ServiceException("用户不属于该角色，无需解除");
                }
                if (roleIds.size() == 1) {
                    // 仅绑定这唯一角色：解除后无角色，直接删除用户（含关联清理、踢下线、权限同步）
                    return userService.deleteUserById(userId) > 0;
                }
                // 绑定多个角色：仅解除该角色绑定
                int rows = userService.unbindUserRole(userId, roleId);
                if (rows < 1) {
                    throw new ServiceException("解除角色绑定失败");
                }
                return true;
            }));
    }

    /**
     * 修改用户个人资料（昵称/手机号/邮箱/性别）。
     * <p>不复用 {@code ISysUserService.updateUserProfile}——那个对 phonenumber/email/sex 是无条件 set，
     * 会把本次未传的字段清空。这里用 {@code LambdaUpdateWrapper} 做条件 set：字段 isNotBlank 才更新，
     * 未传的保持原值。手机号、邮箱的唯一性校验仅在“该字段本次有传值”时才做。</p>
     */
    @Override
    public boolean updateUserProfile(RemoteUserBo remoteUserBo) {
        if (remoteUserBo == null || remoteUserBo.getUserId() == null) {
            throw new ServiceException("用户ID不能为空");
        }
        return TenantHelper.dynamic(remoteUserBo.getTenantId(), () -> {
            // 唯一性校验仅在“该字段本次有传值”时才做（checkXxxUnique 内部按 userId 排除自身）
            if (StringUtils.isNotBlank(remoteUserBo.getPhonenumber())) {
                SysUserBo phoneQ = new SysUserBo();
                phoneQ.setUserId(remoteUserBo.getUserId());
                phoneQ.setPhonenumber(remoteUserBo.getPhonenumber());
                if (!userService.checkPhoneUnique(phoneQ)) {
                    throw new ServiceException("手机号码已存在");
                }
            }
            if (StringUtils.isNotBlank(remoteUserBo.getEmail())) {
                SysUserBo emailQ = new SysUserBo();
                emailQ.setUserId(remoteUserBo.getUserId());
                emailQ.setEmail(remoteUserBo.getEmail());
                if (!userService.checkEmailUnique(emailQ)) {
                    throw new ServiceException("邮箱账号已存在");
                }
            }
            // 条件 set：传了才改，没传不动，避免清空未提交字段
            DataPermissionHelper.ignore(() -> userMapper.update(null,
                new LambdaUpdateWrapper<SysUser>()
                    .set(StringUtils.isNotBlank(remoteUserBo.getNickName()), SysUser::getNickName, remoteUserBo.getNickName())
                    .set(StringUtils.isNotBlank(remoteUserBo.getPhonenumber()), SysUser::getPhonenumber, remoteUserBo.getPhonenumber())
                    .set(StringUtils.isNotBlank(remoteUserBo.getEmail()), SysUser::getEmail, remoteUserBo.getEmail())
                    .set(StringUtils.isNotBlank(remoteUserBo.getSex()), SysUser::getSex, remoteUserBo.getSex())
                    .eq(SysUser::getUserId, remoteUserBo.getUserId())));
            // 四个字段全未传时无 set 列，按“无改动”视作成功
            return true;
        });
    }

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    @Override
    public String selectUserNameById(Long userId) {
        return userService.selectUserNameById(userId);
    }

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @Override
    public String selectNicknameById(Long userId) {
        return userService.selectNicknameById(userId);
    }

    /**
     * 通过用户ID查询用户账户
     *
     * @param userIds 用户ID 多个用逗号隔开
     * @return 用户账户
     */
    @Override
    public String selectNicknameByIds(String userIds) {
        return userService.selectNicknameByIds(userIds);
    }

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    @Override
    public String selectPhonenumberById(Long userId) {
        return userService.selectPhonenumberById(userId);
    }

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    @Override
    public String selectEmailById(Long userId) {
        return userService.selectEmailById(userId);
    }

    /**
     * 构建登录用户
     */
    private LoginUser buildLoginUser(SysUserVo userVo) {
        LoginUser loginUser = new LoginUser();
        Long userId = userVo.getUserId();
        loginUser.setTenantId(userVo.getTenantId());
        loginUser.setUserId(userId);
        loginUser.setDeptId(userVo.getDeptId());
        loginUser.setUsername(userVo.getUserName());
        loginUser.setNickname(userVo.getNickName());
        loginUser.setPassword(userVo.getPassword());
        loginUser.setUserType(userVo.getUserType());
        loginUser.setPhonenumber(userVo.getPhonenumber());
        loginUser.setMenuPermission(permissionService.getMenuPermission(userId));
        loginUser.setRolePermission(permissionService.getRolePermission(userId));
        if (ObjectUtil.isNotNull(userVo.getDeptId())) {
            Opt<SysDeptVo> deptOpt = Opt.of(userVo.getDeptId()).map(deptService::selectDeptById);
            loginUser.setDeptName(deptOpt.map(SysDeptVo::getDeptName).orElse(StringUtils.EMPTY));
            loginUser.setDeptCategory(deptOpt.map(SysDeptVo::getDeptCategory).orElse(StringUtils.EMPTY));
        }
        List<SysRoleVo> roles = roleService.selectRolesByUserId(userId);
        List<SysPostVo> posts = postService.selectPostsByUserId(userId);
        loginUser.setRoles(BeanUtil.copyToList(roles, RoleDTO.class));
        loginUser.setPosts(BeanUtil.copyToList(posts, PostDTO.class));
        return loginUser;
    }

    /**
     * 更新用户信息
     *
     * @param userId 用户ID
     * @param ip     IP地址
     */
    @Override
    public void recordLoginInfo(Long userId, String ip) {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setLoginIp(ip);
        sysUser.setLoginDate(DateUtils.getNowDate());
        sysUser.setUpdateBy(userId);
        DataPermissionHelper.ignore(() -> userMapper.updateById(sysUser));
    }

    /**
     * 通过用户ID查询用户列表
     *
     * @param userIds 用户ids
     * @return 用户列表
     */
    @Override
    public List<RemoteUserVo> selectListByIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return new ArrayList<>();
        }
        List<SysUserVo> list = userMapper.selectVoList(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getUserId, SysUser::getDeptId, SysUser::getUserName,
                SysUser::getNickName, SysUser::getUserType, SysUser::getEmail,
                SysUser::getPhonenumber, SysUser::getSex, SysUser::getStatus,
                SysUser::getCreateTime)
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .in(SysUser::getUserId, userIds));
        List<RemoteUserVo> result = MapstructUtils.convert(list, RemoteUserVo.class);
        // 补充部门名称与岗位名称列表（SDK CloudUserVo 新增字段，getUserInfoById / getRoleUserPage 均经此方法）
        fillDeptNames(result);
        fillPostNames(result);
        return result;
    }

    /**
     * 通过角色ID查询用户ID
     *
     * @param roleIds 角色ids
     * @return 用户ids
     */
    @Override
    public List<Long> selectUserIdsByRoleIds(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return new ArrayList<>();
        }
        return userService.selectUserIdsByRoleIds(roleIds);
    }

    /**
     * 通过角色ID查询用户
     *
     * @param roleIds 角色ids
     * @return 用户
     */
    @Override
    public List<RemoteUserVo> selectUsersByRoleIds(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }

        // 通过角色ID获取用户角色信息
        List<SysUserRole> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIds));

        // 获取用户ID列表
        Set<Long> userIds = StreamUtils.toSet(userRoles, SysUserRole::getUserId);

        return selectListByIds(new ArrayList<>(userIds));
    }

    /**
     * 通过部门ID查询用户
     *
     * @param deptIds 部门ids
     * @return 用户
     */
    @Override
    public List<RemoteUserVo> selectUsersByDeptIds(List<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) {
            return List.of();
        }
        List<SysUserVo> list = userMapper.selectVoList(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName, SysUser::getEmail, SysUser::getPhonenumber)
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .in(SysUser::getDeptId, deptIds));
        return BeanUtil.copyToList(list, RemoteUserVo.class);
    }

    /**
     * 通过岗位ID查询用户
     *
     * @param postIds 岗位ids
     * @return 用户
     */
    @Override
    public List<RemoteUserVo> selectUsersByPostIds(List<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return List.of();
        }

        // 通过岗位ID获取用户岗位信息
        List<SysUserPost> userPosts = userPostMapper.selectList(
            new LambdaQueryWrapper<SysUserPost>().in(SysUserPost::getPostId, postIds));

        // 获取用户ID列表
        Set<Long> userIds = StreamUtils.toSet(userPosts, SysUserPost::getUserId);

        return selectListByIds(new ArrayList<>(userIds));
    }

    @Override
    public boolean isSimplePassword(String password, String tenantId){
        return TenantHelper.dynamic(tenantId, () -> {
            return passwordStrengthChecker.isSimplePassword(password);
        });
    }

    @Override
    public String getSimpleReason(String password, String tenantId) {
        return TenantHelper.dynamic(tenantId, () -> {
            return passwordStrengthChecker.getSimpleReason(password);
        });
    }

    /**
     * 根据用户 ID 列表查询用户名称映射关系
     *
     * @param userIds 用户 ID 列表
     * @return Map，其中 key 为用户 ID，value 为对应的用户名称
     */
    public Map<Long, String> selectUserNamesByIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<SysUser> list = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUserId, SysUser::getNickName)
                .in(SysUser::getUserId, userIds)
        );
        return StreamUtils.toMap(list, SysUser::getUserId, SysUser::getNickName);
    }

    /**
     * 批量填充部门名称。deptId 为空的跳过；用 deptService.selectDeptByIds 批量取，
     * 忽略数据权限（Dubbo 链路无登录上下文，不应受调用人数据范围限制）。
     */
    private void fillDeptNames(List<RemoteUserVo> users) {
        if (CollUtil.isEmpty(users)) {
            return;
        }
        List<Long> deptIds = users.stream().map(RemoteUserVo::getDeptId)
            .filter(Objects::nonNull).distinct().toList();
        if (CollUtil.isEmpty(deptIds)) {
            return;
        }
        Map<Long, String> deptMap = DataPermissionHelper.ignore(() ->
            StreamUtils.toMap(deptService.selectDeptByIds(deptIds), SysDeptVo::getDeptId, SysDeptVo::getDeptName));
        users.forEach(u -> {
            if (u.getDeptId() != null) {
                u.setDeptName(deptMap.get(u.getDeptId()));
            }
        });
    }

    /**
     * 批量填充岗位名称列表（仅正常状态岗位）。
     */
    private void fillPostNames(List<RemoteUserVo> users) {
        if (CollUtil.isEmpty(users)) {
            return;
        }
        List<Long> userIds = users.stream().map(RemoteUserVo::getUserId)
            .filter(Objects::nonNull).distinct().toList();
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        Map<Long, List<String>> postNamesMap = postService.selectPostNamesListByUserIds(userIds);
        users.forEach(u -> u.setPostNames(postNamesMap.getOrDefault(u.getUserId(), Collections.emptyList())));
    }

}
