package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.core.exception.base.BaseException;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserClientConfig;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserClientConfigBo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserClientConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheNames;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.*;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUser;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserPost;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserRole;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysPostVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysRoleVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserExportVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserVo;
import io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeEvent;
import io.gitee.chenshaogui.cloudauth.system.mapper.*;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户 业务层处理
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysUserServiceImpl implements ISysUserService {

    private final SysUserMapper baseMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysUserClientConfigMapper userClientConfigMapper;
    private final ISysUserClientConfigService userClientConfigService;

    /**
     * Spring 事件发布器（P0 权限同步机制使用）
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public TableDataInfo<SysUserVo> searchPageUserList(SysUserBo user, PageQuery pageQuery) {
        if(StringUtils.isBlank(user.getUserName())
            && StringUtils.isBlank(user.getNickName())
            && StringUtils.isBlank(user.getPhonenumber())){
            throw new BaseException("查询参数不能为空");
        }
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysUser::getDelFlag, SystemConstants.NORMAL)
            .like(StringUtils.isNotBlank(user.getUserName()), SysUser::getUserName, user.getUserName())
            .like(StringUtils.isNotBlank(user.getNickName()), SysUser::getNickName, user.getNickName())
            .like(StringUtils.isNotBlank(user.getPhonenumber()), SysUser::getPhonenumber, user.getPhonenumber())
            .orderByAsc(SysUser::getUserId);
        Page<SysUserVo> page = baseMapper.selectPageUserList(pageQuery.build(), this.buildQueryWrapper(user));
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<SysUserVo> selectPageUserList(SysUserBo user, PageQuery pageQuery) {
        Page<SysUserVo> page = baseMapper.selectPageUserList(pageQuery.build(), this.buildQueryWrapper(user));
        return TableDataInfo.build(page);
    }

    /**
     * 根据条件分页查询用户列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public List<SysUserExportVo> selectUserExportList(SysUserBo user) {
        Map<String, Object> params = user.getParams();
        QueryWrapper<SysUser> wrapper = Wrappers.query();
        wrapper.eq("u.del_flag", SystemConstants.NORMAL)
            .like(StringUtils.isNotBlank(user.getUserName()), "u.user_name", user.getUserName())
            .like(StringUtils.isNotBlank(user.getNickName()), "u.nick_name", user.getNickName())
            .eq(StringUtils.isNotBlank(user.getStatus()), "u.status", user.getStatus())
            .like(StringUtils.isNotBlank(user.getPhonenumber()), "u.phonenumber", user.getPhonenumber())
            .between(params.get("beginTime") != null && params.get("endTime") != null,
                "u.create_time", params.get("beginTime"), params.get("endTime"))
            .and(ObjectUtil.isNotNull(user.getDeptId()), w -> {
                List<Long> deptIds = deptMapper.selectDeptAndChildById(user.getDeptId());
                w.in("u.dept_id", deptIds);
            }).orderByAsc("u.user_id");
        return baseMapper.selectUserExportList(wrapper);
    }

    private Wrapper<SysUser> buildQueryWrapper(SysUserBo user) {
        Map<String, Object> params = user.getParams();
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();

        if (!(LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin())) {
            Long userId = LoginHelper.getUserId();
            List<Long> userIds = userClientConfigMapper.selectUserIdsByUserRole(userId);
            if (CollUtil.isEmpty(userIds)) {
                wrapper.apply("1=0");
            } else {
                wrapper.in(SysUser::getUserId, userIds);
            }
        }
        wrapper.eq(SysUser::getDelFlag, SystemConstants.NORMAL)
            .eq(ObjectUtil.isNotNull(user.getUserId()), SysUser::getUserId, user.getUserId())
            .in(StringUtils.isNotBlank(user.getUserIds()), SysUser::getUserId, StringUtils.splitTo(user.getUserIds(), Convert::toLong))
            .like(StringUtils.isNotBlank(user.getUserName()), SysUser::getUserName, user.getUserName())
            .like(StringUtils.isNotBlank(user.getNickName()), SysUser::getNickName, user.getNickName())
            .eq(StringUtils.isNotBlank(user.getStatus()), SysUser::getStatus, user.getStatus())
            .like(StringUtils.isNotBlank(user.getPhonenumber()), SysUser::getPhonenumber, user.getPhonenumber())
            .between(params.get("beginTime") != null && params.get("endTime") != null,
                SysUser::getCreateTime, params.get("beginTime"), params.get("endTime"))
            .and(ObjectUtil.isNotNull(user.getDeptId()), w -> {
                List<Long> ids = deptMapper.selectDeptAndChildById(user.getDeptId());
                w.in(SysUser::getDeptId, ids);
            }).orderByAsc(SysUser::getUserId);
        // 按客户端过滤：只显示通过 sys_user_client_config 绑定该客户端的用户
        if (ObjectUtil.isNotNull(user.getClientId())) {
            List<Long> clientUserIds = userClientConfigMapper.selectUserIdsByClient(user.getClientId());
            if (CollUtil.isEmpty(clientUserIds)) {
                wrapper.apply("1=0");
            } else {
                wrapper.in(SysUser::getUserId, clientUserIds);
            }
        }
        if (StringUtils.isNotBlank(user.getExcludeUserIds())) {
            wrapper.notIn(SysUser::getUserId, StringUtils.splitTo(user.getExcludeUserIds(), Convert::toLong));
        }
        return wrapper;
    }

    /**
     * 根据条件分页查询已分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public TableDataInfo<SysUserVo> selectAllocatedList(SysUserBo user, PageQuery pageQuery) {
        QueryWrapper<SysUser> wrapper = Wrappers.query();
        wrapper.eq("u.del_flag", SystemConstants.NORMAL)
            .eq(ObjectUtil.isNotNull(user.getRoleId()), "r.role_id", user.getRoleId())
            .like(StringUtils.isNotBlank(user.getUserName()), "u.user_name", user.getUserName())
            .eq(StringUtils.isNotBlank(user.getStatus()), "u.status", user.getStatus())
            .like(StringUtils.isNotBlank(user.getPhonenumber()), "u.phonenumber", user.getPhonenumber())
            .orderByAsc("u.user_id");
        Page<SysUserVo> page = baseMapper.selectAllocatedList(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public TableDataInfo<SysUserVo> selectUnallocatedList(SysUserBo user, PageQuery pageQuery) {
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(user.getRoleId());
        QueryWrapper<SysUser> wrapper = Wrappers.query();
        wrapper.eq("u.del_flag", SystemConstants.NORMAL)
            .and(w -> w.ne("r.role_id", user.getRoleId()).or().isNull("r.role_id"))
            .notIn(CollUtil.isNotEmpty(userIds), "u.user_id", userIds)
            .like(StringUtils.isNotBlank(user.getUserName()), "u.user_name", user.getUserName())
            .like(StringUtils.isNotBlank(user.getPhonenumber()), "u.phonenumber", user.getPhonenumber())
            .orderByAsc("u.user_id");
        Page<SysUserVo> page = baseMapper.selectUnallocatedList(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserByUserName(String userName) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, userName));
    }

    /**
     * 通过手机号查询用户
     *
     * @param phonenumber 手机号
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserByPhonenumber(String phonenumber) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhonenumber, phonenumber));
    }

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserById(Long userId) {
        SysUserVo user = baseMapper.selectVoById(userId);
        if (ObjectUtil.isNull(user)) {
            return user;
        }
        user.setRoles(roleMapper.selectRolesByUserId(user.getUserId()));
        return user;
    }

    /**
     * 通过用户ID串查询用户
     *
     * @param userIds 用户ID串
     * @param deptId  部门id
     * @return 用户列表信息
     */
    @Override
    public List<SysUserVo> selectUserByIds(List<Long> userIds, Long deptId) {
        return baseMapper.selectUserList(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName, SysUser::getEmail, SysUser::getPhonenumber)            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .eq(ObjectUtil.isNotNull(deptId), SysUser::getDeptId, deptId)
            .in(CollUtil.isNotEmpty(userIds), SysUser::getUserId, userIds));
    }

    /**
     * 查询用户所属角色组
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public String selectUserRoleGroup(Long userId) {
        List<SysRoleVo> list = roleMapper.selectRolesByUserId(userId);
        if (CollUtil.isEmpty(list)) {
            return StringUtils.EMPTY;
        }
        return StreamUtils.join(list, SysRoleVo::getRoleName);
    }

    /**
     * 查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public String selectUserPostGroup(Long userId) {
        List<SysPostVo> list = postMapper.selectPostsByUserId(userId);
        if (CollUtil.isEmpty(list)) {
            return StringUtils.EMPTY;
        }
        return StreamUtils.join(list, SysPostVo::getPostName);
    }

    /**
     * 校验用户名称是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkUserNameUnique(SysUserBo user) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUserName, user.getUserName())
            .ne(ObjectUtil.isNotNull(user.getUserId()), SysUser::getUserId, user.getUserId()));
        return !exist;
    }

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     */
    @Override
    public boolean checkPhoneUnique(SysUserBo user) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getPhonenumber, user.getPhonenumber())
            .ne(ObjectUtil.isNotNull(user.getUserId()), SysUser::getUserId, user.getUserId()));
        return !exist;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     */
    @Override
    public boolean checkEmailUnique(SysUserBo user) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getEmail, user.getEmail())
            .ne(ObjectUtil.isNotNull(user.getUserId()), SysUser::getUserId, user.getUserId()));
        return !exist;
    }

    /**
     * 校验用户是否允许操作
     *
     * @param userId 用户ID
     */
    @Override
    public void checkUserAllowed(Long userId) {
        if (ObjectUtil.isNotNull(userId) && LoginHelper.isSuperAdmin(userId)) {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    /**
     * 校验用户是否有数据权限
     *
     * @param userId 用户id
     */
    @Override
    public void checkUserDataScope(Long userId) {
        if (ObjectUtil.isNull(userId)) {
            return;
        }
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return;
        }
        if (baseMapper.countUserById(userId) == 0) {
            throw new ServiceException("没有权限访问用户数据！");
        }
    }

    /**
     * 新增保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertUser(SysUserBo user) {
        SysUser sysUser = MapstructUtils.convert(user, SysUser.class);
        // 新增用户信息
        int rows = baseMapper.insert(sysUser);
        user.setUserId(sysUser.getUserId());
        // 新增用户岗位关联
        insertUserPost(user, false);
        // 新增用户与角色管理
        insertUserRole(user, false);
        // 新用户尚未登录，无需权限同步通知
        return rows;
    }

    /**
     * 注册用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerUser(SysUserBo user, String tenantId) {
        user.setCreateBy(0L);
        user.setUpdateBy(0L);
        SysUser sysUser = MapstructUtils.convert(user, SysUser.class);
        sysUser.setTenantId(tenantId);
        int insert = baseMapper.insert(sysUser);
        // 回写新 userId 到入参 BO：MyBatis-Plus insert 后实体已回填主键，逐层透传给 addUser 调用方
        user.setUserId(sysUser.getUserId());
        if(StringUtils.isNotEmpty(user.getClientId())){
            SysUserClientConfigBo clientConfigBo = BeanUtil.toBean(sysUser, SysUserClientConfigBo.class);
            // userVo已存在userId，desensitizeFlag，和belongCode、belongName
            clientConfigBo.setClientId(user.getClientId());
            userClientConfigService.saveOrUpdateConfig(clientConfigBo);
        }
        // 注册后自动绑定角色（roleKeys 为空不做任何事；仅用户尚未绑定角色时才绑定）
        // 传入 clientId：支持配置短标识时自动补全为 {clientId}-{短标识} 匹配（与新建角色入库规则一致）
        autoBindRolesByRoleKeys(sysUser.getUserId(), user.getRoleKeys(), tenantId, user.getClientId());
        return insert>0;
    }

    /**
     * addUser 幂等补绑：按账号查询已存在用户，存在则按 roleKeys 追加绑定角色并返回既有 userId。
     * <p>与 {@link #registerUser} 的自动绑定不同：本方法不覆盖、不删除已有绑定，只补绑缺失的角色
     * （按 roleId 去重），超级管理员角色禁止经此通道绑定；用户不存在或 roleKeys 为空时不产生任何写入。</p>
     * <p>sys_user / sys_role 为租户隔离表，全程按入参 tenantId 切租户；新增绑定后发布权限变更事件，
     * 让已在线的用户在客户端 SDK 的检查周期内重载权限。</p>
     *
     * @param userName 用户账号
     * @param roleKeys 角色标识列表（sys_role.role_key，支持短标识或完整 {clientId}-标识）
     * @param tenantId 租户ID
     * @param clientId 客户端ID（短标识补全用）
     * @return 已存在用户的 userId；账号不存在时返回 null（调用方继续走新建用户流程）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long bindRolesIfUserExists(String userName, List<String> roleKeys, String tenantId, String clientId) {
        if (StringUtils.isBlank(userName)) {
            return null;
        }
        return TenantHelper.dynamic(tenantId, () -> {
            SysUser user = baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUserId)
                .eq(SysUser::getUserName, userName)
                .last("LIMIT 1"));
            if (user == null) {
                return null;
            }
            Long userId = user.getUserId();
            int inserted = bindRoleKeys(userId, roleKeys, clientId, false);
            if (inserted > 0) {
                // 已存在用户可能已在线：通知客户端 SDK 在权限检查周期内重载登录用户
                publishUserChange(List.of(userId), PermissionChangeEvent.ChangeType.UPDATE);
            }
            return userId;
        });
    }

    /**
     * 修改保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.userId")
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(SysUserBo user) {
        // 新增用户与角色管理
        insertUserRole(user, true);
        // 新增用户与岗位管理
        insertUserPost(user, true);
        SysUser sysUser = MapstructUtils.convert(user, SysUser.class);
        // 防止错误更新后导致的数据误删除
        int flag = baseMapper.updateById(sysUser);
        if (flag < 1) {
            throw new ServiceException("修改用户{}信息失败", user.getUserName());
        }
        // P0 权限同步：用户信息/角色变更，通知该用户刷新
        publishUserChange(List.of(user.getUserId()), PermissionChangeEvent.ChangeType.UPDATE);
        return flag;
    }

    /**
     * 用户授权角色
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUserAuth(Long userId, Long[] roleIds) {
        insertUserRole(userId, roleIds, true);
        // 权限同步：授权角色属于权限变更，通知该用户刷新登录态
        publishUserChange(List.of(userId), PermissionChangeEvent.ChangeType.UPDATE);
    }

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 帐号状态
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUserStatus(Long userId, String status) {
        int rows = baseMapper.update(null,
            new LambdaUpdateWrapper<SysUser>()
                .set(SysUser::getStatus, status)
                .eq(SysUser::getUserId, userId));
        if (rows > 0) {
            // 权限同步：通知该用户刷新登录态
            publishUserChange(List.of(userId), PermissionChangeEvent.ChangeType.STATUS_CHANGE);
            // 禁用用户必须立即踢下线：客户端本地登录态独立存储，仅通知刷新不足以保证失效
            if (SystemConstants.DISABLE.equals(status)) {
                kickoutOnlineByUserIds(List.of(userId));
            }
        }
        return rows;
    }

    /**
     * 修改用户状态（按租户隔离，供总线 updateUserStatus 调用，避免跨租户误操作）
     *
     * @param userId   用户ID
     * @param status   帐号状态（0正常 / 1停用）
     * @param tenantId 操作人租户ID（用于租户隔离校验，传 null 不限定）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUserStatus(Long userId, String status, String tenantId) {
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
            .set(SysUser::getStatus, status)
            .eq(SysUser::getUserId, userId);
        if (StringUtils.isNotBlank(tenantId)) {
            wrapper.eq(SysUser::getTenantId, tenantId);
        }
        int rows = baseMapper.update(null, wrapper);
        if (rows > 0) {
            // 权限同步：通知该用户刷新登录态
            publishUserChange(List.of(userId), PermissionChangeEvent.ChangeType.STATUS_CHANGE);
            // 禁用用户必须立即踢下线：客户端本地登录态独立存储，仅通知刷新不足以保证失效
            if (SystemConstants.DISABLE.equals(status)) {
                kickoutOnlineByUserIds(List.of(userId));
            }
        }
        return rows;
    }

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.userId")
    @Override
    public int updateUserProfile(SysUserBo user) {
        return baseMapper.update(null,
            new LambdaUpdateWrapper<SysUser>()
                .set(ObjectUtil.isNotNull(user.getNickName()), SysUser::getNickName, user.getNickName())
                .set(SysUser::getPhonenumber, user.getPhonenumber())
                .set(SysUser::getEmail, user.getEmail())
                .set(SysUser::getSex, user.getSex())
                .eq(SysUser::getUserId, user.getUserId()));
    }

    /**
     * 修改用户头像
     *
     * @param userId 用户ID
     * @param avatar 头像地址
     * @return 结果
     */
    @Override
    public boolean updateUserAvatar(Long userId, Long avatar) {
        return baseMapper.update(null,
            new LambdaUpdateWrapper<SysUser>()
                .set(SysUser::getAvatar, avatar)
                .eq(SysUser::getUserId, userId)) > 0;
    }

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 密码
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resetUserPwd(Long userId, String password) {
        int rows = baseMapper.update(null,
            new LambdaUpdateWrapper<SysUser>()
                .set(SysUser::getPassword, password)
                .eq(SysUser::getUserId, userId));
        if (rows > 0) {
            // 权限同步：通知该用户刷新登录态
            publishUserChange(List.of(userId), PermissionChangeEvent.ChangeType.PASSWORD_RESET);
            // 密码已变更，旧 token 必须立即失效（sa-token 不会因密码变化自动失效 token）
            kickoutOnlineByUserIds(List.of(userId));
        }
        return rows;
    }

    /**
     * 注册后按角色标识（sys_role.role_key）自动绑定角色
     * <p>规则：
     * 1. roleKeys 为空直接返回（向后兼容，行为与改造前完全一致）；
     * 2. 仅当该用户在 sys_user_role 中尚无任何绑定时才执行（已配置过的不覆盖，天然幂等）；
     * 3. sys_role 为租户隔离表，按注册租户 TenantHelper.dynamic 切上下文查询；
     * 4. role_key 库内为完整格式 {clientId}-{权限标识}（新建角色时的统一规则），
     *    故对每个配置值做双格式兼容：原值 + clientId 补全值 一并精确匹配；
     * 5. 超级管理员角色禁止经注册通道自动绑定（防提权）；
     * 6. 未命中任何启用角色时静默跳过，只记 warn 日志，不影响注册结果；
     * 7. 与 registerUser 同事务，绑定失败则注册整体回滚。</p>
     *
     * @param userId   新注册的用户ID
     * @param roleKeys 角色标识列表（可空；支持短标识或完整 {clientId}-标识）
     * @param tenantId 注册租户ID
     * @param clientId 注册来源客户端ID（用于补全短标识）
     */
    private void autoBindRolesByRoleKeys(Long userId, List<String> roleKeys, String tenantId, String clientId) {
        if (CollUtil.isEmpty(roleKeys) || userId == null) {
            return;
        }
        TenantHelper.dynamic(tenantId, () -> {
            // 注册语义：用户已有任何角色绑定时整体跳过
            bindRoleKeys(userId, roleKeys, clientId, true);
        });
    }

    /**
     * 按角色标识绑定用户角色（调用方需已切好租户上下文）。
     * <p>规则：role_key 双格式兼容（原值 + {clientId}-原值）、只命中启用状态角色、
     * 超级管理员角色禁止绑定、未命中静默跳过只记日志。</p>
     *
     * @param userId           用户ID
     * @param roleKeys         角色标识列表（可空；短标识或完整 {clientId}-标识）
     * @param clientId         客户端ID（短标识补全用）
     * @param onlyWhenNoRoles  true=用户已有任何绑定则整体跳过（注册语义）；
     *                         false=仅补绑缺失的角色（addUser 用户已存在语义，保留原有绑定）
     * @return 本次新增的 sys_user_role 条数
     */
    private int bindRoleKeys(Long userId, List<String> roleKeys, String clientId, boolean onlyWhenNoRoles) {
        if (CollUtil.isEmpty(roleKeys) || userId == null) {
            return 0;
        }
        // 已有绑定：注册语义下用于整体跳过，补绑语义下用于按 roleId 去重
        List<SysUserRole> boundList = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (onlyWhenNoRoles && CollUtil.isNotEmpty(boundList)) {
            log.info("自动绑定角色跳过：用户已绑定角色, userId={}", userId);
            return 0;
        }
        Set<Long> boundIds = new HashSet<>();
        for (SysUserRole bound : boundList) {
            boundIds.add(bound.getRoleId());
        }
        // 双格式兼容：库里 role_key 是 {clientId}-{权限标识}，配置短标识时自动补全后一起精确匹配
        List<String> candidates = new ArrayList<>(roleKeys.size() * 2);
        for (String key : roleKeys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            String k = key.trim();
            if (!candidates.contains(k)) {
                candidates.add(k);
            }
            if (StringUtils.isNotBlank(clientId)) {
                String full = clientId + "-" + k;
                if (!candidates.contains(full)) {
                    candidates.add(full);
                }
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        // 按 role_key 命中启用状态的角色（MP 逻辑删除自动过滤 del_flag）
        List<SysRole> roles = roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getRoleKey, candidates)
                .eq(SysRole::getStatus, SystemConstants.NORMAL));
        if (CollUtil.isEmpty(roles)) {
            log.warn("自动绑定角色：未命中任何启用角色, roleKeys={}, userId={}, 跳过绑定", roleKeys, userId);
            return 0;
        }
        List<SysUserRole> userRoles = new ArrayList<>(roles.size());
        for (SysRole role : roles) {
            // 防提权：超级管理员角色不允许通过本通道自动绑定
            if (SystemConstants.SUPER_ADMIN_ID.equals(role.getRoleId())) {
                log.warn("自动绑定角色：忽略超级管理员角色, roleId={}, userId={}", role.getRoleId(), userId);
                continue;
            }
            // 已绑定过 / 本次已加入的角色不再插入（补绑语义幂等，不覆盖已有配置）
            if (!boundIds.add(role.getRoleId())) {
                continue;
            }
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(role.getRoleId());
            userRoles.add(ur);
        }
        if (CollUtil.isEmpty(userRoles)) {
            return 0;
        }
        userRoleMapper.insertBatch(userRoles);
        log.info("自动绑定角色成功: userId={}, roleKeys={}, 新增绑定数={}", userId, roleKeys, userRoles.size());
        return userRoles.size();
    }

    /**
     * 新增用户角色信息
     *
     * @param user  用户对象
     * @param clear 清除已存在的关联数据
     */
    private void insertUserRole(SysUserBo user, boolean clear) {
        this.insertUserRole(user.getUserId(), user.getRoleIds(), clear);
    }

    /**
     * 新增用户岗位信息
     *
     * @param user  用户对象
     * @param clear 清除已存在的关联数据
     */
    private void insertUserPost(SysUserBo user, boolean clear) {
        Long[] postIdArr = user.getPostIds();
        if (ArrayUtil.isEmpty(postIdArr)) {
            return;
        }
        List<Long> postIds = Arrays.asList(postIdArr);

        // 校验是否有权限操作这些岗位（含数据权限控制）
        if (postMapper.selectPostCount(postIds) != postIds.size()) {
            throw new ServiceException("没有权限访问岗位的数据");
        }

        // 是否清除旧的用户岗位绑定
        if (clear) {
            userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, user.getUserId()));
        }

        // 构建用户岗位关联列表并批量插入
        List<SysUserPost> list = StreamUtils.toList(postIds,
            postId -> {
                SysUserPost up = new SysUserPost();
                up.setUserId(user.getUserId());
                up.setPostId(postId);
                return up;
            });
        userPostMapper.insertBatch(list);
    }

    /**
     * 新增用户角色信息
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     * @param clear   清除已存在的关联数据
     */
    private void insertUserRole(Long userId, Long[] roleIds, boolean clear) {
        if (ArrayUtil.isEmpty(roleIds)) {
            return;
        }

        List<Long> roleList = new ArrayList<>(Arrays.asList(roleIds));

        // 非超级管理员，禁止包含超级管理员角色
        if (!LoginHelper.isSuperAdmin(userId)) {
            roleList.remove(SystemConstants.SUPER_ADMIN_ID);
        }

        // 校验是否有权限访问这些角色（含数据权限控制）
        if (roleMapper.selectRoleCount(roleList) != roleList.size()) {
            throw new ServiceException("没有权限访问角色的数据");
        }

        // 是否清除原有绑定
        if (clear) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        }

        // 批量插入用户-角色关联
        List<SysUserRole> list = StreamUtils.toList(roleList,
            roleId -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                return ur;
            });
        userRoleMapper.insertBatch(list);
    }

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserById(Long userId) {
        // 删除用户与角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        // 删除用户与岗位表
        userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, userId));
        // 删除用户与客户端的关系
        userClientConfigMapper.delete(new LambdaQueryWrapper<SysUserClientConfig>().eq(SysUserClientConfig::getUserId, userId));
        // 防止更新失败导致的数据删除
        int flag = baseMapper.deleteById(userId);
        if (flag < 1) {
            throw new ServiceException("删除用户失败!");
        }
        // 权限同步：优先踢下线，避免已删除用户凭借缓存中的登录态继续访问
        kickoutOnlineByUserIds(List.of(userId));
        publishUserChange(List.of(userId), PermissionChangeEvent.ChangeType.DELETE);
        return flag;
    }

    /**
     * 批量删除用户信息
     *
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserByIds(Long[] userIds) {
        for (Long userId : userIds) {
            checkUserAllowed(userId);
            checkUserDataScope(userId);
        }
        List<Long> ids = List.of(userIds);
        // 删除用户与角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, ids));
        // 删除用户与岗位表
        userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().in(SysUserPost::getUserId, ids));
        // 删除用户与客户端的关系
        userClientConfigMapper.delete(new LambdaQueryWrapper<SysUserClientConfig>().in(SysUserClientConfig::getUserId, ids));
        // 防止更新失败导致的数据删除
        int flag = baseMapper.deleteByIds(ids);
        if (flag < 1) {
            throw new ServiceException("删除用户失败!");
        }
        // 权限同步：批量踢下线 + 通知
        kickoutOnlineByUserIds(ids);
        publishUserChange(ids, PermissionChangeEvent.ChangeType.DELETE);
        return flag;
    }

    /**
     * 解除单个用户与单个角色的绑定：仅删 sys_user_role 关联，保留用户本身，并同步权限（踢下线 + 通知刷新）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unbindUserRole(Long userId, Long roleId) {
        int rows = userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getUserId, userId).eq(SysUserRole::getRoleId, roleId));
        if (rows > 0) {
            // 权限同步：踢下线 + 通知刷新（角色变化需立即生效）
            kickoutOnlineByUserIds(List.of(userId));
            publishUserChange(List.of(userId), PermissionChangeEvent.ChangeType.UPDATE);
        }
        return rows;
    }

    /**
     * 通过部门id查询当前部门所有用户
     *
     * @param deptId 部门ID
     * @return 用户信息集合信息
     */
    @Override
    public List<SysUserVo> selectUserListByDept(Long deptId) {
        LambdaQueryWrapper<SysUser> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysUser::getDeptId, deptId);
        lqw.orderByAsc(SysUser::getUserId);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public List<Long> selectUserIdsByRoleIds(List<Long> roleIds) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIds));
        return StreamUtils.toList(userRoles, SysUserRole::getUserId);
    }

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    @Cacheable(cacheNames = CacheNames.SYS_USER_NAME, key = "#userId")
    @Override
    public String selectUserNameById(Long userId) {
        SysUser sysUser = baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getUserName).eq(SysUser::getUserId, userId));
        return ObjectUtils.notNullGetter(sysUser, SysUser::getUserName);
    }

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @Override
    @Cacheable(cacheNames = CacheNames.SYS_NICKNAME, key = "#userId")
    public String selectNicknameById(Long userId) {
        SysUser sysUser = baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getNickName).eq(SysUser::getUserId, userId));
        return ObjectUtils.notNullGetter(sysUser, SysUser::getNickName);
    }

    @Override
    public String selectNicknameByIds(String userIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(userIds, Convert::toLong)) {
            String nickname = SpringUtils.getAopProxy(this).selectNicknameById(id);
            if (StringUtils.isNotBlank(nickname)) {
                list.add(nickname);
            }
        }
        return StringUtils.joinComma(list);
    }

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    @Override
    public String selectPhonenumberById(Long userId) {
        SysUser sysUser = baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getPhonenumber).eq(SysUser::getUserId, userId));
        return ObjectUtils.notNullGetter(sysUser, SysUser::getPhonenumber);
    }

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    @Override
    public String selectEmailById(Long userId) {
        SysUser sysUser = baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getEmail).eq(SysUser::getUserId, userId));
        return ObjectUtils.notNullGetter(sysUser, SysUser::getEmail);
    }

    // ================== 权限同步机制辅助方法 ==================

    /**
     * 发布"按用户ID列表"权限变更事件
     * <p>{@code @TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)} 会保证
     * 事务成功提交后才真正写 Redis 标记，同时对无事务方法也生效</p>
     */
    private void publishUserChange(List<Long> userIds, PermissionChangeEvent.ChangeType changeType) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        try {
            eventPublisher.publishEvent(new PermissionChangeEvent(this, userIds, changeType));
        } catch (Exception e) {
            log.warn("用户权限变更事件发布失败 type={}, err={}", changeType, e.getMessage());
        }
    }

    /**
     * 强制指定用户下线（禁用 / 重置密码 / 删除用户时调用）
     *
     * <p><b>为什么必须显式踢下线</b>：客户端 SDK 的本地登录态存在自己的 Redis 中，
     * 认证中心仅仅"删除 token"并不会让客户端感知（客户端仍认为处于登录态）。
     * 因此禁用、重置密码、删除用户这类语义上必须立即失效的场景，
     * 需要主动使 token 失效，让客户端下次请求时得到 401 并跳转登录</p>
     *
     * <p>实现方式：遍历在线 token，按 {@code userId} 匹配后逐一出登出。
     * 若在线用户量极大，该操作有一定 Redis 开销，但用户管理类操作频率低，可接受</p>
     *
     * @param userIds 需要下线的用户ID列表
     */
    private void kickoutOnlineByUserIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        try {
            List<String> keys = StpUtil.searchTokenValue("", 0, -1, false);
            if (CollUtil.isEmpty(keys)) {
                return;
            }
            keys.parallelStream().forEach(key -> {
                String token = StringUtils.substringAfterLast(key, ":");
                // 已过期则跳过
                if (StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) < -1) {
                    return;
                }
                LoginUser loginUser = LoginHelper.getLoginUser(token);
                if (loginUser == null) {
                    return;
                }
                if (userIds.contains(loginUser.getUserId())) {
                    try {
                        StpUtil.logoutByTokenValue(token);
                    } catch (NotLoginException ignored) {
                        // token 已被并发登出，忽略
                    }
                }
            });
            log.info("[permission-sync] 已强制用户下线 userIds={}", userIds);
        } catch (Exception e) {
            // 踢下线失败不应阻塞用户管理主流程，客户端仍会通过权限同步感知
            log.warn("[permission-sync] 强制用户下线失败 userIds={}, err={}", userIds, e.getMessage());
        }
    }
}
