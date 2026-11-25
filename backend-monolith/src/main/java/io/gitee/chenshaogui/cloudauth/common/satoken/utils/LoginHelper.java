package io.gitee.chenshaogui.cloudauth.common.satoken.utils;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.common.core.enums.UserType;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ServletUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;

import java.util.Set;

/**
 * 登录鉴权助手
 * <p>
 * user_type 为 用户类型 同一个用户表 可以有多种用户类型 例如 pc,app
 * deivce 为 设备类型 同一个用户类型 可以有 多种设备类型 例如 web,ios
 * 可以组成 用户类型与设备类型多对多的 权限灵活控制
 * <p>
 * 多用户体系 针对 多种用户类型 但权限控制不一致
 * 可以组成 多用户类型表与多设备类型 分别控制权限
 *
 * @author Lion Li
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginHelper {

    public static final String LOGIN_USER_KEY = "loginUser";
    public static final String TENANT_KEY = "tenantId";
    public static final String USER_KEY = "userId";
    public static final String USER_NAME_KEY = "userName";
    public static final String DEPT_KEY = "deptId";
    public static final String DEPT_NAME_KEY = "deptName";
    public static final String DEPT_CATEGORY_KEY = "deptCategory";
    public static final String CLIENT_KEY = "clientid";

    /**
     * Token extra：浏览器维度 ID（同浏览器多 tab 一致，跨浏览器独立）。
     * 由 SDK 的 {@code BrowserIdFilter} 或前端 axios 拦截器注入 X-Browser-Id header，
     * 服务端在 {@link #login} 时写入 token，登出时按此过滤实现"同浏览器多 tab 一起退"。
     */
    public static final String BROWSER_ID_KEY = "browserId";

    /** X-Browser-Id 请求头名称，与 SDK Filter 保持一致 */
    public static final String BROWSER_ID_HEADER = "X-Browser-Id";

    /**
     * 登录系统 基于 设备类型
     * 针对相同用户体系不同设备
     *
     * @param loginUser 登录用户信息
     * @param model     配置参数
     */
    public static void login(LoginUser loginUser, SaLoginParameter model) {
        model = ObjectUtil.defaultIfNull(model, new SaLoginParameter());
        // 浏览器维度 ID：优先从 BrowserIdContext 读（Filter 设置），
        // 其次从请求头读（前端直接调用），都没有则生成新的（每个浏览器天然独立，不共享桶）
        String browserId = BrowserIdContext.get();
        if (StringUtils.isBlank(browserId)) {
            try {
                HttpServletRequest req = ServletUtils.getRequest();
                if (req != null) {
                    browserId = req.getHeader(BROWSER_ID_HEADER);
                }
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.isBlank(browserId)) {
            browserId = IdUtil.simpleUUID();
        }
        StpUtil.login(loginUser.getLoginId(),
            model.setExtra(TENANT_KEY, loginUser.getTenantId())
                .setExtra(USER_KEY, loginUser.getUserId())
                .setExtra(USER_NAME_KEY, loginUser.getUsername())
                .setExtra(DEPT_KEY, loginUser.getDeptId())
                .setExtra(DEPT_NAME_KEY, loginUser.getDeptName())
                .setExtra(DEPT_CATEGORY_KEY, loginUser.getDeptCategory())
                .setExtra(BROWSER_ID_KEY, browserId)
        );
        StpUtil.getTokenSession().set(LOGIN_USER_KEY, loginUser);
    }

    /**
     * 获取用户(多级缓存)
     */
    @SuppressWarnings("unchecked cast")
    public static <T extends LoginUser> T getLoginUser() {
        SaSession session = StpUtil.getTokenSession();
        if (ObjectUtil.isNull(session)) {
            return null;
        }
        return (T) session.get(LOGIN_USER_KEY);
    }

    /**
     * 获取用户基于token
     */
    @SuppressWarnings("unchecked cast")
    public static <T extends LoginUser> T getLoginUser(String token) {
        SaSession session = StpUtil.getTokenSessionByToken(token);
        if (ObjectUtil.isNull(session)) {
            return null;
        }
        return (T) session.get(LOGIN_USER_KEY);
    }

    /**
     * 获取用户id
     */
    public static Long getUserId() {
        return Convert.toLong(getExtra(USER_KEY));
    }

    /**
     * 获取用户id
     */
    public static String getUserIdStr() {
        return Convert.toStr(getExtra(USER_KEY));
    }

    /**
     * 获取用户账户
     */
    public static String getUsername() {
        return Convert.toStr(getExtra(USER_NAME_KEY));
    }

    /**
     * 获取租户ID
     */
    public static String getTenantId() {
        return Convert.toStr(getExtra(TENANT_KEY));
    }

    /**
     * 获取部门ID
     */
    public static Long getDeptId() {
        return Convert.toLong(getExtra(DEPT_KEY));
    }

    /**
     * 获取部门名
     */
    public static String getDeptName() {
        return Convert.toStr(getExtra(DEPT_NAME_KEY));
    }

    /**
     * 获取部门类别编码
     */
    public static String getDeptCategory() {
        return Convert.toStr(getExtra(DEPT_CATEGORY_KEY));
    }

    /**
     * 获取客户端id
     */
    public static String getClientId() {
        return Convert.toStr(getExtra(CLIENT_KEY));
    }

    /**
     * 获取当前 Token 的扩展信息
     *
     * @param key 键值
     * @return 对应的扩展数据
     */
    private static Object getExtra(String key) {
        try {
            return StpUtil.getExtra(key);
        } catch (Exception e) {
            return null;
        }

    }

    /**
     * 获取用户类型
     */
    public static UserType getUserType() {
        String loginType = StpUtil.getLoginIdAsString();
        return UserType.getUserType(loginType);
    }

    /**
     * 是否为超级管理员
     *
     * @param userId 用户ID
     * @return 结果
     */
    public static boolean isSuperAdmin(Long userId) {
        return SystemConstants.SUPER_ADMIN_ID.equals(userId);
    }

    /**
     * 是否为超级管理员
     *
     * @return 结果
     */
    public static boolean isSuperAdmin() {
        // 兼容两种判断：原生 userId=1（默认超管），或拥有 superadmin 角色（迁移用户兼容）
        if (SystemConstants.SUPER_ADMIN_ID.equals(getUserId())) {
            return true;
        }
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            return false;
        }
        return isSuperAdmin(loginUser);
    }

    /**
     * 是否为超级管理员（基于传入的 LoginUser 判断，不依赖当前请求上下文）
     *
     * <p><b>使用场景</b>：当前线程上下文中不存在用户 token 的调用链
     * （如 SSO pushS 消息处理 → Dubbo 下游调用）。此时无参版 {@link #isSuperAdmin()}
     * 会从上下文取 token，token 为空时 {@code StpUtil.getTokenSession()} 内部
     * {@code checkActiveTimeout()} 查不到 last-active key，会抛出具有误导性的
     * {@code NotLoginException: token 已被冻结}。已持有 LoginUser 时必须用本重载。</p>
     *
     * @param loginUser 用户信息
     * @return 结果
     */
    public static boolean isSuperAdmin(LoginUser loginUser) {
        if (loginUser == null) {
            return false;
        }
        // 兼容两种判断：原生 userId=1（默认超管），或拥有 superadmin 角色（迁移用户兼容）
        if (SystemConstants.SUPER_ADMIN_ID.equals(loginUser.getUserId())) {
            return true;
        }
        Set<String> rolePermission = loginUser.getRolePermission();
        return rolePermission != null && rolePermission.contains(TenantConstants.SUPER_ADMIN_ROLE_KEY);
    }

    /**
     * 是否为租户管理员
     *
     * @param rolePermission 角色权限标识组
     * @return 结果
     */
    public static boolean isTenantAdmin(Set<String> rolePermission) {
        if (CollUtil.isEmpty(rolePermission)) {
            return false;
        }
        return rolePermission.contains(TenantConstants.TENANT_ADMIN_ROLE_KEY);
    }

    /**
     * 是否为租户管理员
     *
     * @return 结果
     */
    public static boolean isTenantAdmin() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            return false;
        }
        return Convert.toBool(isTenantAdmin(loginUser.getRolePermission()));
    }

    /**
     * 检查当前用户是否已登录
     *
     * @return 结果
     */
    public static boolean isLogin() {
        try {
            StpUtil.checkLogin();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
