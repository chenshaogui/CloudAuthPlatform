package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheNames;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClientRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRole;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRoleDept;
import io.gitee.chenshaogui.cloudauth.system.domain.SysRoleMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserClientConfig;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserRole;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysClientRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysMenuMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleDeptMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysRoleMenuMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserRoleMapper;
import io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeEvent;
import io.gitee.chenshaogui.cloudauth.system.helper.SysClientAdminRoleBinder;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserClientConfigMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 客户端管理Service业务层处理
 *
 * @author Michelle.Chung
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysClientServiceImpl implements ISysClientService {

    private final SysClientMapper baseMapper;

    private final SysClientRoleMapper sysClientRoleMapper;

    private final SysUserClientConfigMapper sysUserClientConfigMapper;

    private final SysMenuMapper sysMenuMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final SysRoleMenuMapper sysRoleMenuMapper;

    private final SysRoleDeptMapper sysRoleDeptMapper;

    /**
     * admin role 绑定：抽离到 helper，避免 SysClientServiceImpl ↔ SysRoleServiceImpl 循环依赖
     */
    private final SysClientAdminRoleBinder sysClientAdminRoleBinder;

    /**
     * Spring 事件发布器（权限同步机制使用）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询客户端管理
     */
    @Override
    public SysClientVo queryById(Long id) {
        SysClientVo vo = baseMapper.selectVoById(id);
        vo.setGrantTypeList(StringUtils.splitList(vo.getGrantType()));
        return vo;
    }

    /**
     * 查询客户端管理
     */
    @Cacheable(cacheNames = CacheNames.SYS_CLIENT, key = "#clientId")
    @Override
    public SysClientVo queryByClientId(String clientId) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<SysClient>().eq(SysClient::getClientId, clientId));
    }

    @Override
    public List<SysClientVo> queryByMenuIds(List<Long> menuIdList) {
        return baseMapper.selectVoList(new LambdaQueryWrapper<SysClient>().in(SysClient::getMenuId, menuIdList));
    }

    @Override
    public boolean existsActiveClientByMenuId(Long menuId) {
        if (menuId == null) {
            return false;
        }
        return baseMapper.exists(new LambdaQueryWrapper<SysClient>()
            .eq(SysClient::getMenuId, menuId)
            .eq(SysClient::getDelFlag, SystemConstants.NORMAL));
    }

    /**
     * 查询客户端管理列表
     */
    @Override
    public TableDataInfo<SysClientVo> queryPageList(SysClientBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysClient> lqw = buildQueryWrapper(bo);
        Page<SysClientVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        result.getRecords().forEach(r -> r.setGrantTypeList(StringUtils.splitList(r.getGrantType())));
        return TableDataInfo.build(result);
    }

    /**
     * 查询客户端管理列表
     */
    @Override
    public List<SysClientVo> queryList(SysClientBo bo) {
        LambdaQueryWrapper<SysClient> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<SysClient> buildQueryWrapper(SysClientBo bo) {
        LambdaQueryWrapper<SysClient> lqw = Wrappers.lambdaQuery();
        // 管理员显示所有客户端信息 不是管理员 按用户id过滤客户端（角色授权 + 用户-客户端直接授权）
        if (!(LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin())) {
            Long userId = LoginHelper.getUserId();
            Set<String> clientIdSet = new HashSet<>();
            clientIdSet.addAll(sysClientRoleMapper.selectClientIdsByUserId(userId));
            clientIdSet.addAll(sysUserClientConfigMapper.selectClientIdsByUserId(userId));
            if (CollUtil.isEmpty(clientIdSet)) {
                lqw.apply("1=0");
            } else {
                lqw.in(SysClient::getClientId, clientIdSet);
            }
        }
        lqw.eq(StringUtils.isNotBlank(bo.getClientId()), SysClient::getClientId, bo.getClientId());
        lqw.eq(StringUtils.isNotBlank(bo.getClientKey()), SysClient::getClientKey, bo.getClientKey());
        lqw.eq(StringUtils.isNotBlank(bo.getClientSecret()), SysClient::getClientSecret, bo.getClientSecret());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SysClient::getStatus, bo.getStatus());
        lqw.orderByAsc(SysClient::getId);
        return lqw;
    }

    /**
     * 新增客户端管理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(SysClientBo bo) {
        if(StringUtils.isEmpty(bo.getMenuUrl()) && bo.getMenuId()==null){
            throw new ServiceException("应用单点登录地址[menuUrl],菜单id[menuId]其中一个不能为空");
        }
        SysClient add = MapstructUtils.convert(bo, SysClient.class);
        add.setGrantType(CollUtil.join(bo.getGrantTypeList(), StringUtils.SEPARATOR));
        // 生成clientId
        String clientKey = bo.getClientKey();
        String clientSecret = bo.getClientSecret();
        if(StringUtils.isBlank(clientSecret)){
            StringKeyGenerator keyGenerator = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);
            clientSecret = keyGenerator.generateKey();
        }
        add.setClientId(SecureUtil.md5(clientKey + clientSecret));

        // 增加菜单绑定
        bindMenuInfo(bo, add);
        // 增加与管理员角色绑定
        bindAdminRoleInfo(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 绑定菜单信息
     * @param bo 入口参数
     * @param add 新增参数
     */
    private void bindMenuInfo(SysClientBo bo, SysClient add) {
        if(add.getMenuId() == null){
            LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, 0)
                .orderByDesc(SysMenu::getOrderNum)
                .last("LIMIT 1");
            SysMenu existMenu = sysMenuMapper.selectOne(queryWrapper);
            SysMenu sysMenu = new SysMenu();
            sysMenu.setOrderNum(existMenu == null? 0 : existMenu.getOrderNum()+1);
            sysMenu.setMenuName(add.getClientKey());
            sysMenu.setParentId(0L);
            sysMenu.setIsCache("0");
            sysMenu.setIsFrame("0");
            sysMenu.setPath(bo.getMenuUrl());
            sysMenu.setStatus("0");
            sysMenu.setVisible("0");
            sysMenu.setMenuType(SystemConstants.TYPE_APP);
            sysMenu.setCreateBy(add.getCreateBy());
            sysMenu.setCreateTime(add.getCreateTime());
            sysMenu.setUpdateBy(add.getUpdateBy());
            sysMenu.setUpdateTime(add.getUpdateTime());
            sysMenu.setCreateDept(add.getCreateDept());
            sysMenuMapper.insert(sysMenu);
            add.setMenuId(sysMenu.getMenuId());
            // 应用菜单自身的 app_id = 自身 menu_id（冗余，便于后续按 app_id 过滤应用子树）
            // 这是写时维护的关键一环：让新建的应用菜单立刻可被 selectMenuList 等接口过滤
            sysMenu.setAppId(sysMenu.getMenuId());
            sysMenuMapper.updateById(sysMenu);
        } else {
            boolean exists = baseMapper.exists(new LambdaQueryWrapper<SysClient>().eq(SysClient::getMenuId, bo.getMenuId()));
            if(exists){
                throw new ServiceException("应用菜单已被其他客户端绑定");
            }
        }
    }

    /**
     * 增加管理员角色信息
     *
     * <p>具体逻辑下沉至 {@link SysClientAdminRoleBinder#bindAdminRole(SysClient)}，
     * 此处仅做委托以切断 {@code SysClientServiceImpl → ISysRoleService} 的循环依赖。</p>
     *
     * <p>管理员角色创建后会把当前操作人绑定为该客户端的管理员，
     * 否则非超管用户新建客户端后会因“用户 → 角色 → 客户端”过滤而看不到刚建的数据。</p>
     *
     * @param add 客户端添加信息
     * @return 客户端管理员角色ID（已存在则返回 null）
     */
    private Long bindAdminRoleInfo(SysClient add) {
        return sysClientAdminRoleBinder.bindAdminRole(add);
    }

    /**
     * 修改客户端管理
     */
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, key = "#bo.clientId")
    @Override
    public Boolean updateByBo(SysClientBo bo) {
        if(bo.getMenuId()!=null){
            SysClientVo existsSysClient = baseMapper.selectVoOne(
                new LambdaQueryWrapper<SysClient>().eq(SysClient::getMenuId, bo.getMenuId())
            );
            if(existsSysClient!=null
                && !Objects.equals(bo.getId(), existsSysClient.getId())){
                throw new ServiceException("当前菜单已被客户端【"+existsSysClient.getClientKey()+"】绑定");
            }
        }
        SysClient update = MapstructUtils.convert(bo, SysClient.class);
        update.setGrantType(StringUtils.joinComma(bo.getGrantTypeList()));
        boolean flag = baseMapper.updateById(update) > 0;
        if (flag) {
            // 客户端绑定的菜单决定该应用用户的菜单树，变更后广播通知所有用户重载
            publishClientChange(PermissionChangeEvent.ChangeType.UPDATE);
        }
        return flag;
    }

    /**
     * 修改状态
     *
     * <p>停用客户端时强制该客户端在线用户下线，防止已发 token 在有效期内继续访问</p>
     */
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, key = "#clientId")
    @Override
    public int updateClientStatus(String clientId, String status) {
        int rows = baseMapper.update(null,
            new LambdaUpdateWrapper<SysClient>()
                .set(SysClient::getStatus, status)
                .eq(SysClient::getClientId, clientId));
        if (rows > 0) {
            if (SystemConstants.DISABLE.equals(status)) {
                SysClientVo client = baseMapper.selectVoOne(
                    new LambdaQueryWrapper<SysClient>().eq(SysClient::getClientId, clientId));
                if (client != null) {
                    kickoutOnlineByClientKey(client.getClientKey());
                }
            }
            publishClientChange(PermissionChangeEvent.ChangeType.STATUS_CHANGE);
        }
        return rows;
    }

    /**
     * 批量删除客户端管理
     *
     * <p>级联清理：客户端管理员角色（含用户/菜单/部门关联）、客户端-角色绑定、
     * 用户-客户端配置，并强制该客户端在线用户下线。</p>
     *
     * <p>注意：不删除客户端绑定的应用菜单（及其子树），仅解除 {@code sys_client.menu_id} 绑定关系，
     * 应用菜单树保留、可重新绑定</p>
     */
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        List<SysClientVo> sysClientVos = baseMapper.selectVoByIds(ids);
        List<String> clientIdList = sysClientVos.stream().map(SysClientVo::getClientId).toList();
        if (CollUtil.isEmpty(clientIdList)) {
            return false;
        }
        // 1. 校验客户端下绑定的业务角色（客户端管理员角色除外），有需要先解绑
        // 角色权限字符串统一格式：{clientId}-{roleKey}，管理员角色固定后缀 -admin
        List<String> adminRoleKeys = clientIdList.stream()
            .map(clientId -> clientId + "-admin")
            .toList();
        Set<Long> adminRoleIds = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleKey, adminRoleKeys))
            .stream().map(SysRole::getRoleId).collect(java.util.stream.Collectors.toSet());
        List<Long> otherRoleIds = sysClientRoleMapper.selectList(
                new LambdaQueryWrapper<SysClientRole>().in(SysClientRole::getClientId, clientIdList))
            .stream().map(SysClientRole::getRoleId).filter(roleId -> !adminRoleIds.contains(roleId)).distinct().toList();
        if (CollUtil.isNotEmpty(otherRoleIds)) {
            throw new ServiceException("客户端已分配业务角色，请先解绑后再删除!");
        }
        // 2. 级联删除客户端管理员角色及其关联（绕过 deleteRoleByIds 的已分配用户校验）
        if (CollUtil.isNotEmpty(adminRoleIds)) {
            List<Long> roleIdList = new ArrayList<>(adminRoleIds);
            sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIdList));
            sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIdList));
            sysRoleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().in(SysRoleDept::getRoleId, roleIdList));
            sysRoleMapper.deleteByIds(roleIdList);
        }
        // 3. 删除客户端-角色绑定、用户-客户端配置
        sysClientRoleMapper.delete(new LambdaQueryWrapper<SysClientRole>().in(SysClientRole::getClientId, clientIdList));
        sysUserClientConfigMapper.delete(new LambdaQueryWrapper<SysUserClientConfig>().in(SysUserClientConfig::getClientId, clientIdList));
        // 4. 删除客户端（仅解除绑定，不删除绑定的应用菜单及其子树）
        //    菜单是"应用"的载体，删除客户端只应解除 sys_client 的绑定关系；
        //    应用菜单树保留，可重新绑定给其他/新建的客户端，避免误删造成整棵应用菜单与数据丢失
        // 5. 删除客户端并强制该客户端在线用户下线
        boolean flag = baseMapper.deleteByIds(ids) > 0;
        if (flag) {
            sysClientVos.forEach(client -> kickoutOnlineByClientKey(client.getClientKey()));
            publishClientChange(PermissionChangeEvent.ChangeType.DELETE);
        }
        return flag;
    }


    /**
     * 通过用户id查询客户端管理列表（角色授权 + 用户-客户端直接授权）
     */
    @Override
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
        lqw.eq( SysClient::getDelFlag, 0);
        lqw.orderByAsc(SysClient::getId);
        return baseMapper.selectVoList(lqw);
    }
    /**
     * 发布客户端变更事件（走全网广播）
     *
     * <p>客户端与其绑定菜单共同决定"某个应用下用户能看到什么菜单"，
     * 变更影响该客户端下的全部用户，难以精确枚举，因此统一广播</p>
     */
    private void publishClientChange(PermissionChangeEvent.ChangeType changeType) {
        try {
            eventPublisher.publishEvent(new PermissionChangeEvent(this, null, changeType));
        } catch (Exception e) {
            log.warn("客户端变更事件发布失败: {}", e.getMessage());
        }
    }

    /**
     * 强制指定客户端的在线用户下线（停用/删除客户端时调用）
     *
     * <p>客户端 SDK 的本地登录态存在自己的 Redis 中，仅删除 token 不会让客户端感知，
     * 需主动使 token 失效，让客户端下次请求时得到 401 并跳转登录</p>
     *
     * @param clientKey 客户端key（登录态中存储的客户端标识）
     */
    private void kickoutOnlineByClientKey(String clientKey) {
        if (StringUtils.isBlank(clientKey)) {
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
                if (loginUser == null || !clientKey.equals(loginUser.getClientKey())) {
                    return;
                }
                try {
                    StpUtil.logoutByTokenValue(token);
                } catch (NotLoginException ignored) {
                    // token 已被并发登出，忽略
                }
            });
            log.info("[permission-sync] 已强制客户端在线用户下线 clientKey={}", clientKey);
        } catch (Exception e) {
            // 踢下线失败不应阻塞客户端管理主流程，客户端仍会通过权限同步感知
            log.warn("[permission-sync] 强制客户端在线用户下线失败 clientKey={}, err={}", clientKey, e.getMessage());
        }
    }
}
