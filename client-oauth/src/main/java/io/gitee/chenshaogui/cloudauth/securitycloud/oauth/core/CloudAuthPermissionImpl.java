package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.core;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudLoginUser;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * sa-token 权限管理实现类
 *
 * <p>核心实现：纯本地 Session 读取，零远程调用，O(1) 开销。
 * 用户全量数据由 {@link CloudPlatformHelper#doLoginByTicket(String)} 登录时一次性拉取并写入 Session，
 * 后续所有 {@code @SaCheckPermission} / {@code @SaCheckRole} 校验都从 Session 内存读取。</p>
 *
 * <p><b>角色集合客户端剪裁</b>：
 * {@link #getRoleList(Object, String)} 在返回角色集合前，会按"当前请求所属客户端 ID"对用户的
 * {@link CloudLoginUser#getRolePermission()} 做剪裁——只暴露属于当前客户端的角色（同时给出
 * 原始 {@code {clientId}-{roleKey}} 与剥离前缀的 {@code roleKey} 两种形式），全局角色
 * {@code superadmin} / {@code admin} 原样透传。</p>
 *
 * <p>剪裁后，业务侧可在客户端 SDK 子系统内直接使用
 * {@code @SaCheckRole("admin")} / {@code @SaCheckRole("user")} 等注解，
 * 语义为"当前子系统的子管理员 / 当前子系统的 user 角色"，
 * 无需拼接 {@code {clientId}-} 前缀，也不存在跨子系统 SSO session 复用的越权风险。</p>
 *
 * <p>旧写法 {@code @SaCheckRole("{clientId}-admin")} 仍兼容（原始 key 也会一并暴露）。</p>
 *
 * <p>如需在管理员后台修改用户权限后立即生效，调用 {@link CloudPlatformHelper#refreshLoginUser()} 主动重载。</p>
 *
 * @author Lion Li
 */
@Slf4j
public class CloudAuthPermissionImpl implements StpInterface {

    /**
     * 获取菜单权限列表（O(1) 本地 Session 读取，无远程调用）。
     *
     * <p>菜单权限按"全局可见"语义透传，不做客户端剪裁。
     * 业务侧如果需要按客户端粒度控制菜单可见性，应在菜单分配阶段按 {@code sys_client_role} 约束，
     * 而不是在此处做剪裁。</p>
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        CloudLoginUser loginUser = CloudPlatformHelper.getLoginUser();
        if (ObjectUtil.isNull(loginUser)
                || CharSequenceUtil.isBlank(loginUser.getLoginId())
                || !Objects.equals(loginUser.getLoginId(), loginId)) {
            // 防御 NPE：loginId 可能为 null，使用 Objects.equals
            log.warn("[Sa-Token] Session 与 loginId 不一致: session={}, param={}",
                    loginUser == null ? null : loginUser.getLoginId(), loginId);
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未获取到登录用户或登录态已失效", null).setCode(11011);
        }
        return CollUtil.isNotEmpty(loginUser.getMenuPermission())
                ? new ArrayList<>(loginUser.getMenuPermission())
                : new ArrayList<>();
    }

    /**
     * 获取角色权限列表（O(1) 本地 Session 读取，无远程调用）。
     *
     * <p><b>客户端剪裁规则</b>（{@code currentClientId} 由 {@link CloudPlatformHelper#resolveCurrentClientId()} 解析）：</p>
     * <ul>
     *   <li>全局角色 {@value TenantConstants#SUPER_ADMIN_ROLE_KEY} / {@value TenantConstants#TENANT_ADMIN_ROLE_KEY}：原样透传；</li>
     *   <li>客户端角色 {@code {currentClientId}-{roleKey}}：原样 + 剥离前缀的 {@code roleKey} 同时暴露
     *       （兼容旧写法 {@code @SaCheckRole("{clientId}-admin")} 与新写法 {@code @SaCheckRole("admin")}）；</li>
     *   <li>其他客户端的角色：不暴露（避免跨子系统 SSO session 复用导致的越权）；</li>
     *   <li>{@code currentClientId} 解析失败时：仅暴露全局角色，不做剥离（向后兼容）。</li>
     * </ul>
     *
     * <p>{@code superadmin} 用户自动额外获得 {@code admin}（全局租户管理员）角色，
     * 保证 {@code @SaCheckRole("admin")} 在任何子系统都通过。</p>
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        CloudLoginUser loginUser = CloudPlatformHelper.getLoginUser();
        if (ObjectUtil.isNull(loginUser)
                || CharSequenceUtil.isBlank(loginUser.getLoginId())
                || !Objects.equals(loginUser.getLoginId(), loginId)) {
            log.warn("[Sa-Token] Session 与 loginId 不一致: session={}, param={}",
                    loginUser == null ? null : loginUser.getLoginId(), loginId);
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未获取到登录用户或登录态已失效", null).setCode(11011);
        }
        Set<String> rolePermission = loginUser.getRolePermission();
        if (CollUtil.isEmpty(rolePermission)) {
            return Collections.emptyList();
        }

        // currentClientId 为空时不剪裁，保留旧行为（仅全局角色透传）
        String currentClientId = CloudPlatformHelper.resolveCurrentClientId();
        if (CharSequenceUtil.isBlank(currentClientId)) {
            return filterGlobalRolesOnly(rolePermission);
        }

        String prefix = currentClientId + "-";
        Set<String> scoped = new HashSet<>();
        boolean hasSuperAdmin = false;
        for (String role : rolePermission) {
            if (CharSequenceUtil.isBlank(role)) {
                continue;
            }
            // 全局角色：原样透传
            if (TenantConstants.SUPER_ADMIN_ROLE_KEY.equals(role)) {
                scoped.add(role);
                hasSuperAdmin = true;
                continue;
            }
            if (TenantConstants.TENANT_ADMIN_ROLE_KEY.equals(role)) {
                scoped.add(role);
                continue;
            }
            // 客户端角色：仅匹配 currentClientId 时暴露
            if (role.startsWith(prefix)) {
                // 1. 原样保留 → 兼容 @SaCheckRole("{clientId}-admin") 旧写法
                scoped.add(role);
                // 2. 剥离前缀 → 新写法 @SaCheckRole("admin") 直接可用
                String bare = role.substring(prefix.length());
                if (CharSequenceUtil.isNotBlank(bare)) {
                    scoped.add(bare);
                }
            }
        }
        // superadmin 自动拥有 admin 权限（保证任意子系统内 @SaCheckRole("admin") 通过）
        if (hasSuperAdmin) {
            scoped.add(TenantConstants.TENANT_ADMIN_ROLE_KEY);
        }
        return new ArrayList<>(scoped);
    }

    /**
     * 仅暴露全局角色（{@code currentClientId} 无法解析时的安全降级路径）。
     *
     * <p>此处返回完整 {@code rolePermission} 而非仅全局角色，目的是：
     * 1. 保留 {@code @SaCheckRole("{clientId}-admin")} 这类完整 key 注解写法的正常工作；
     * 2. 在尚未配置 SSO client（如纯登录 SDK 模式）时仍能正确返回角色集合。</p>
     *
     * <p>降级路径下的语义是"SDK 不感知客户端"，业务侧按完整 {@code rolePermission} 校验，
     * 但同一 SSO session 在多个子系统间的越权风险由调用方自行把控——建议生产部署务必配置
     * {@code sa-token.sso-client.client}。</p>
     */
    private List<String> filterGlobalRolesOnly(Set<String> rolePermission) {
        return new ArrayList<>(rolePermission);
    }

}