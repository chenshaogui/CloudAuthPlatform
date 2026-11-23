package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.sso.SaSsoManager;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.http.HttpStatus;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.*;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.cache.SharedRemoteCache;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config.CloudProperties;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.constant.TenantConstants;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.BrowserIdContext;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.core.SsoClientContext;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.listener.CloudAuthListener;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.template.RemoteSaSsoClientTemplate;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.CloudOperLogRecorder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 登录鉴权助手
 *
 * <p><b>缓存策略</b>：登录时一次性拉取用户全量数据写入 Session，
 * 后续 {@link #getLoginUser()} / 权限码读取均从本地 Session 取，
 * 不再每次请求远程拉取。{@code @SaCheckPermission} 校验性能为 O(1)。
 *
 * @author sgchen
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CloudPlatformHelper {

    public static final String LOGIN_USER_KEY = "loginUser";
    public static final String TENANT_KEY = "tenantId";
    public static final String USER_KEY = "userId";
    public static final String USER_NAME_KEY = "userName";
    public static final String NICK_NAME_KEY = "nickName";
    public static final String DEPT_KEY = "deptId";
    public static final String DEPT_NAME_KEY = "deptName";
    public static final String DEPT_CATEGORY_KEY = "deptCategory";
    public static final String CLIENT_KEY = "clientid";
    /**
     * Token extra：浏览器维度 ID（同浏览器所有 tab 一致，跨浏览器独立）。
     * 配合 {@link BrowserIdContext} 使用，实现"同浏览器多 tab 一起登出"。
     */
    public static final String BROWSER_ID_KEY = "browserId";

    /**
     * 远程调用结果缓存默认 TTL（秒）
     * <p>设置依据：后台管理类场景，20 秒感知延迟可接受；
     * 缓存命中时不产生任何远程调用，TTL 期间同用户多请求合并为 1 次远程</p>
     */
    public static final long DEFAULT_REMOTE_CACHE_TTL_SECONDS = 20L;

    /**
     * 远程调用结果缓存最小 TTL（秒），低于此值会被强制提升，避免业务方误配导致远程调用过频
     */
    public static final long MIN_REMOTE_CACHE_TTL_SECONDS = 5L;

    /**
     * 配置键：是否启用权限同步
     */
    private static final String CONFIG_ENABLED = "sa-token.permission-sync.enabled";

    /**
     * 配置键：权限同步检查间隔（秒），同时作为远程缓存 TTL 的次级回退
     * <p>优先级低于 {@link #CONFIG_CACHE_TTL_SECONDS}</p>
     */
    private static final String CONFIG_INTERVAL_SECONDS = "sa-token.permission-sync.interval-seconds";

    /**
     * 配置键：默认档 TTL（秒）—— 权限相关接口（{@link #DEFAULT_TIER_APIS_DESC}）
     * <p>同时作为权限变更检查间隔；默认 20 秒，最小 5 秒。
     * 未配置时回退到 {@link #CONFIG_INTERVAL_SECONDS}。</p>
     */
    private static final String CONFIG_CACHE_TTL_SECONDS = "sa-token.cache.ttl-seconds";

    /**
     * Session key：上次执行检查的时间戳（用于节流，避免每个请求都发起远程调用）
     */
    private static final String LAST_CHECK_AT_KEY = "permissionSyncLastCheckAt";

    /**
     * Session key：上次已处理的服务端变更时间戳（用于去重，避免同一变更重复刷新）
     */
    private static final String LAST_CHANGE_TIME_KEY = "permissionSyncLastChangeTime";


    /**
     * 配置键：单个 API 的远程调用结果缓存 TTL（秒）
     * <p>形如 {@code sa-token.cache.<apiName>.ttl-seconds}，
     * 例如 {@code sa-token.cache.getDictDataByType.ttl-seconds=600}。</p>
     *
     * <p>这是<b>单接口例外</b>通道，优先级高于档位键；只有某一个接口要偏离所在档位时才写。
     * 整类调整请用档位键（{@link #CONFIG_REFERENCE_TTL_SECONDS} /
     * {@link #CONFIG_USER_TTL_SECONDS} / {@link #CONFIG_CACHE_TTL_SECONDS}），
     * 一档一次生效，不必逐个接口声明。</p>
     */
    private static final String CONFIG_API_CACHE_TTL_PREFIX = "sa-token.cache.";

    private static final String CONFIG_API_CACHE_TTL_SUFFIX = ".ttl-seconds";

    /**
     * 配置键：参考数据档 TTL（秒）—— 部门 / 字典 / 参数配置
     * <p>配一次即覆盖 {@link #TIER_REFERENCE_APIS} 全部接口；
     * 不配置时用内置默认 {@link #DEFAULT_REFERENCE_TTL_SECONDS} 秒。</p>
     */
    private static final String CONFIG_REFERENCE_TTL_SECONDS = "sa-token.cache.reference-ttl-seconds";

    /**
     * 配置键：用户信息档 TTL（秒）—— 按 id / 批量查用户
     * <p>配一次即覆盖 {@link #TIER_USER_APIS} 全部接口；
     * 不配置时用内置默认 {@link #DEFAULT_USER_TTL_SECONDS} 秒。</p>
     */
    private static final String CONFIG_USER_TTL_SECONDS = "sa-token.cache.user-ttl-seconds";

    /**
     * 参考数据档内置默认 TTL（秒）：后台低频维护，变一次等几分钟无感
     */
    private static final long DEFAULT_REFERENCE_TTL_SECONDS = 300L;

    /**
     * 用户信息档内置默认 TTL（秒）：昵称 / 手机号偶有修改
     */
    private static final long DEFAULT_USER_TTL_SECONDS = 60L;

    /**
     * 默认档（权限相关）成员说明，仅用于日志 / 文档描述。
     * <p>这些接口的 TTL 必须与权限变更检查节流保持一致，因此不单独设置档位键，
     * 直接走全局 {@link #CONFIG_CACHE_TTL_SECONDS}。</p>
     */
    private static final String DEFAULT_TIER_APIS_DESC = "userInfo/routers/isRemoteLogin";

    /**
     * 参考数据档成员：部门名 / 部门树 / 字典 / 参数配置。
     * <p>档位成员是「哪些接口属于哪一类」的唯一依据，同时决定
     * {@link #DEFAULT_API_CACHE_TTL} 与 {@link #getCacheTtlSeconds(String)} 的档位归属。</p>
     */
    private static final Set<String> TIER_REFERENCE_APIS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "getDeptNameById", "getDeptNameByIds", "getDeptCodeById", "getDeptCodeByIds",
            "getDeptChildListById",
            "getDictTypeAll", "getDictTypeById", "getDictTypeByType",
            "getDictDataById", "getDictDataByType",
            "getConfigAll", "getConfigById", "getConfigByKey", "getConfigValueByKey",
            // 1.3.0 区域编码 sys_area（参考数据：行政区划低频维护，5 分钟感知延迟可接受）
            "getAreaList", "getAreaByCode",
            // 1.3.0 通知公告 sys_notice（参考数据：公告发布后多读少改，5 分钟缓存可接受）
            "getNoticeList", "getNoticeById",
            // 1.3.0 文件存储 sys_oss（参考数据：URL 变化主要依赖 OssFactory / matchingUrl 重新生成，
            // 5 分钟缓存可接受，避免每次列表渲染都打远程）
            "getOssUrlById", "getOssUrlByIds", "getOssByIds",
            // 1.5.6 角色信息查询（参考数据：角色定义低频维护，5 分钟缓存可接受）
            "getRoleByRoleKey")));

    /**
     * 用户信息档成员：按 id / 批量查用户
     */
    private static final Set<String> TIER_USER_APIS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "getUserInfoById", "getUserListByIds",
            // 1.5.6 角色下用户分页查询（用户信息档：角色人员分配变动频率与用户资料一致）
            "getRoleUserPage")));

    /**
     * 各档位内置默认 TTL 明细（apiName → TTL 秒），由档位成员推导，供 DEBUG 明细日志使用。
     *
     * <p>未列入任何档位的接口（{@link #DEFAULT_TIER_APIS_DESC} 等权限相关接口）
     * 走 {@link #getRemoteCacheTtlSeconds()} 默认档（20 秒，与权限同步节流一致）。</p>
     */
    private static final Map<String, Long> DEFAULT_API_CACHE_TTL;
    static {
        Map<String, Long> map = new LinkedHashMap<>();
        TIER_REFERENCE_APIS.forEach(api -> map.put(api, DEFAULT_REFERENCE_TTL_SECONDS));
        TIER_USER_APIS.forEach(api -> map.put(api, DEFAULT_USER_TTL_SECONDS));
        DEFAULT_API_CACHE_TTL = Collections.unmodifiableMap(map);
    }

    /**
     * 获取各档位内置默认 TTL 明细（apiName → TTL 秒），启动日志 DEBUG 明细等场景使用
     */
    public static Map<String, Long> getDefaultApiCacheTtl() {
        return DEFAULT_API_CACHE_TTL;
    }

    /**
     * 获取各档位<b>当前生效</b>的 TTL（档位描述 → 秒），启动日志 / 文档等场景使用。
     *
     * <p>档位描述带上了对应配置键，便于从日志直接定位到 YAML 里该改哪一行。</p>
     */
    public static Map<String, Long> getApiCacheTtlTiers() {
        Map<String, Long> tiers = new LinkedHashMap<>();
        tiers.put("权限相关(" + DEFAULT_TIER_APIS_DESC + ") | " + shortKey(CONFIG_CACHE_TTL_SECONDS),
                getRemoteCacheTtlSeconds());
        tiers.put("参考数据(部门/字典/参数配置) | " + shortKey(CONFIG_REFERENCE_TTL_SECONDS),
                readTierTtl(CONFIG_REFERENCE_TTL_SECONDS, DEFAULT_REFERENCE_TTL_SECONDS));
        tiers.put("用户信息(getUserInfoById/getUserListByIds) | " + shortKey(CONFIG_USER_TTL_SECONDS),
                readTierTtl(CONFIG_USER_TTL_SECONDS, DEFAULT_USER_TTL_SECONDS));
        return tiers;
    }

    /**
     * 去掉配置键的公共前缀，仅保留 {@code sa-token.cache.} 之后的部分
     */
    private static String shortKey(String fullKey) {
        return fullKey.startsWith(CONFIG_API_CACHE_TTL_PREFIX)
                ? fullKey.substring(CONFIG_API_CACHE_TTL_PREFIX.length()) : fullKey;
    }

    public static final RemoteSaSsoClientTemplate REMOTE_SSO_CLIENT_TEMPLATE = new RemoteSaSsoClientTemplate();

    // ================== 用户信息变更监听器（权限同步机制） ==================

    /**
     * 用户信息变更监听器集合（线程安全）
     * <p>服务端修改角色/权限/菜单后，会通过 Redis 标记通知客户端，
     * 客户端 SDK 在收到通知后自动调用 {@link #refreshLoginUser()} 重载登录用户，
     * 触发本监听器，业务方可注册回调刷新本地业务缓存</p>
     */
    private static final List<Consumer<CloudLoginUser>> userInfoChangedListeners
            = new CopyOnWriteArrayList<>();

    // ================== 登录 / 登出监听器 ==================

    /**
     * 子系统登录 / 登出监听器集合（线程安全）。
     * <p>由 {@code CloudTokenClientAutoConfigure} 在启动时自动收集容器内所有
     * {@link CloudAuthListener} Bean 并注册；也可通过 {@link #registerAuthListener(CloudAuthListener)} 手动注册。</p>
     */
    private static final List<CloudAuthListener> authListeners = new CopyOnWriteArrayList<>();

    // ================== 用户信息查询（O(1) 缓存读取） ==================

    /**
     * 获取当前登录用户（优先从本地 Session 读取，缓存未命中时远程拉取兜底）
     */
    public static CloudLoginUser getLoginUser() {
        if (!StpUtil.isLogin()) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未能读取到有效token或者token已过期", null).setCode(11011);
        }
        // 1. 优先从当前 Session 读（O(1)，方案 B 落地核心）
        Object cached = null;
        try {
            cached = StpUtil.getSession().get(LOGIN_USER_KEY);
        } catch (Exception e) {
            log.debug("Session 读取失败，将走远程兜底: {}", e.getMessage());
        }
        // 1.1 直接命中
        if (cached instanceof CloudLoginUser) {
            return (CloudLoginUser) cached;
        }
        // 1.2 Redis JSON 反序列化会将复杂对象还原为 LinkedHashMap，
        //     这里按 Class 转换回 CloudLoginUser（本地 Jackson 微秒级，无远程调用）
        if (cached instanceof Map) {
            CloudLoginUser converted = JsonUtils.convertValue(cached, CloudLoginUser.class);
            if (converted != null) {
                return converted;
            }
        }
        // 2. 真正空缓存（首次登录异常路径）才走远程兜底
        log.warn("Session 中未缓存 loginUser，将远程拉取一次并回写，建议检查 doLoginByTicket 流程");
        CloudLoginUser remote = fetchLoginUserFromRemote();
        cacheLoginUser(remote);
        return remote;
    }

    /**
     * 远程拉取完整登录用户（不走缓存，仅供内部使用）
     */
    private static CloudLoginUser fetchLoginUserFromRemote() {
        // 1.3.9 外来票守卫：跨客户端服务不能用本服务 client 向中心拉取登录用户
        //（中心按 client 过滤会话，外来票必查不到 → 假阴性 401）。
        // 正常路径下归属端登录时已把 loginUser 写入共享 Session，{@link #getLoginUser()}
        // 直接命中缓存、不会走到本方法；只有 Session 缺失（异常）才降级到这里，
        // 此时既无法远程补齐、本地也无角色/权限数据，故明确抛未登录（与本方法
        // 远程失败时一致抛 NotLoginException 的契约相符），仅省掉一次会误判的远程往返。
        if (SsoClientContext.hasForeignOwner() && SsoClientContext.isSkipForeign()) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "跨客户端访问且本服务无该用户会话缓存，请通过其归属客户端服务访问", null).setCode(11011);
        }
        Object loginId = StpUtil.getLoginId();
        SaSsoMessage message = new SaSsoMessage();
        message.setType("loginUser");
        message.set("loginId", loginId);
        SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
        message.set("client", clientConfig.getClient());
        Object data = getRemoteSaResult(message);
        if (data instanceof CloudLoginUser) {
            return (CloudLoginUser) data;
        }
        return JsonUtils.convertValue(data, CloudLoginUser.class);
    }

    /**
     * 将用户全量数据写入 Session
     */
    private static void cacheLoginUser(CloudLoginUser loginUser) {
        if (loginUser == null) {
            return;
        }
        try {
            StpUtil.getSession().set(LOGIN_USER_KEY, loginUser);
        } catch (Exception e) {
            // 必须打印完整堆栈：NPE 的 getMessage() 为 null，仅打 message 无法定位
            log.warn("写入 Session loginUser 失败", e);
        }
    }

    /**
     * 主动刷新登录用户（管理员后台调整用户权限后，业务方可调用本方法立即生效）
     * <p>刷新成功后会触发 {@link #fireUserInfoChanged(CloudLoginUser)}，
     * 已通过 {@link #onUserInfoChanged(Consumer)} 注册的监听器会被调用</p>
     */
    public static CloudLoginUser refreshLoginUser() {
        if (!StpUtil.isLogin()) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "登录已失效，无法刷新用户信息", null).setCode(11011);
        }
        CloudLoginUser remote = fetchLoginUserFromRemote();
        cacheLoginUser(remote);
        // 同步清空当前用户的远程读取缓存（userInfo / routers / isRemoteLogin），
        // 避免在用户角色变更后仍读到旧数据
        invalidateCurrentUserCaches();
        try {
            Map<String, Object> extra = buildLoginExtra(remote);
            SaSession saSession = StpUtil.getSessionByLoginId(StpUtil.getLoginId());
            extra.forEach(saSession::set);
        } catch (Exception e) {
            log.warn("刷新用户扩展信息失败: {}", e.getMessage());
        }
        // 通知监听器（P0 权限同步机制）
        fireUserInfoChanged(remote);
        return remote;
    }

    /**
     * 注册用户信息变更监听器
     * <p>适用场景：业务方持有自定义缓存（如本地字典缓存、路由快照），希望服务端权限变更后能自动失效</p>
     * <p>触发时机：
     * <ul>
     *   <li>{@link #refreshLoginUser()} 完成后</li>
     *   <li>{@link #checkPermissionChange()} 检测到变更并完成刷新后</li>
     * </ul>
     * </p>
     *
     * @param listener 监听器，参数为刷新后的最新 CloudLoginUser；监听器异常会被 try-catch 吞掉不影响其他监听器
     */
    public static void onUserInfoChanged(Consumer<CloudLoginUser> listener) {
        if (listener == null) {
            return;
        }
        userInfoChangedListeners.add(listener);
        log.debug("[permission-sync] 注册用户信息变更监听器，当前监听器数量: {}", userInfoChangedListeners.size());
    }

    /**
     * 移除用户信息变更监听器
     *
     * @param listener 之前通过 {@link #onUserInfoChanged(Consumer)} 注册的监听器实例
     * @return 是否成功移除
     */
    public static boolean removeUserInfoChangedListener(Consumer<CloudLoginUser> listener) {
        boolean removed = userInfoChangedListeners.remove(listener);
        if (removed) {
            log.debug("[permission-sync] 移除用户信息变更监听器，剩余: {}", userInfoChangedListeners.size());
        }
        return removed;
    }

    /**
     * 内部触发所有监听器（异常隔离，确保一个监听器失败不影响其他）
     */
    private static void fireUserInfoChanged(CloudLoginUser fresh) {
        if (fresh == null || userInfoChangedListeners.isEmpty()) {
            return;
        }
        for (Consumer<CloudLoginUser> listener : userInfoChangedListeners) {
            try {
                listener.accept(fresh);
            } catch (Exception e) {
                log.warn("[permission-sync] 监听器执行异常（已隔离）: {}", e.getMessage());
            }
        }
    }

    /**
     * 注册登录 / 登出监听器（幂等：同一实例重复注册只保留一次）。
     * <p>常规场景无需手动调用——把 {@link CloudAuthListener} 实现声明为 Spring Bean，
     * SDK 启动时会自动收集注册。本方法供非 Spring 环境或动态注册场景使用。</p>
     *
     * @param listener 监听器实例
     * @since 1.3.5
     */
    public static void registerAuthListener(CloudAuthListener listener) {
        if (listener != null && !authListeners.contains(listener)) {
            authListeners.add(listener);
            log.debug("[auth-listener] 注册登录/登出监听器 {}，当前数量: {}",
                    listener.getClass().getName(), authListeners.size());
        }
    }

    /**
     * 移除已注册的登录 / 登出监听器。
     *
     * @param listener 监听器实例
     * @return 是否成功移除
     * @since 1.3.5
     */
    public static boolean unregisterAuthListener(CloudAuthListener listener) {
        return listener != null && authListeners.remove(listener);
    }

    /** 触发登录监听器（异常隔离，单个监听器报错不影响登录主流程） */
    private static void fireLogin(CloudLoginUser user) {
        for (CloudAuthListener listener : authListeners) {
            try {
                listener.onLogin(user);
            } catch (Exception e) {
                log.warn("[auth-listener] onLogin 执行异常（已隔离）{}: {}",
                        listener.getClass().getName(), e.getMessage());
            }
        }
    }

    /** 触发登出监听器（异常隔离） */
    private static void fireLogout(CloudLoginUser user) {
        for (CloudAuthListener listener : authListeners) {
            try {
                listener.onLogout(user);
            } catch (Exception e) {
                log.warn("[auth-listener] onLogout 执行异常（已隔离）{}: {}",
                        listener.getClass().getName(), e.getMessage());
            }
        }
    }

    /**
     * 从 Session 读取当前登录用户快照（<b>不远程、不抛异常</b>），供登出回调使用。
     * <p>必须在 {@code StpUtil.logout()} 销毁会话<b>之前</b>调用。无缓存时返回 {@code null}。</p>
     */
    private static CloudLoginUser currentUserSnapshot() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            Object cached = StpUtil.getSession().get(LOGIN_USER_KEY);
            if (cached instanceof CloudLoginUser) {
                return (CloudLoginUser) cached;
            }
            if (cached instanceof Map) {
                return JsonUtils.convertValue(cached, CloudLoginUser.class);
            }
            return null;
        } catch (Exception e) {
            log.debug("[auth-listener] 读取登出用户快照失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 在请求链路中检查服务端是否有当前用户的权限变更（<b>自动同步入口</b>）
     *
     * <p><b>为什么不用定时任务</b>：SDK 集成在业务系统中，sa-token 的登录态是"每请求"的
     * （token 来自当次请求的 header / cookie）。后台定时任务线程没有 SaTokenContext，
     * {@code StpUtil.isLogin()} 会直接抛异常并使任务被永久取消，因此检查必须发生在请求线程内。</p>
     *
     * <p>为避免每个请求都产生远程调用，本方法按默认档 TTL
     * （{@link #CONFIG_CACHE_TTL_SECONDS}，默认 20 秒）在 Session 维度节流：
     * 未到检查周期时零开销直接返回</p>
     *
     * <p>本方法内部已捕获全部异常，调用方无需处理</p>
     */
    public static void checkPermissionChangeIfNeeded() {
        try {
            if (!isPermissionSyncEnabled() || !StpUtil.isLogin()) {
                return;
            }
            // 1.3.9：外来票不参与本服务的权限同步。
            // 必须在写节流时间戳之前判断——否则会把外来票的检查时间戳写进【共享】Account-Session，
            // 干扰同账号在归属客户端下的检查节奏。
            if (SsoClientContext.hasForeignOwner()) {
                if (SsoClientContext.isRejectForeign()) {
                    throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                            "该登录态属于其他授权客户端，本服务不提供服务", null).setCode(11011);
                }
                if (SsoClientContext.isSkipForeign()) {
                    log.debug("[permission-sync] 外来票 owner={} 本服务={}，跳过权限同步",
                            SsoClientContext.currentOwnerClient(), SsoClientContext.primaryClient());
                    return;
                }
                // 未开启 cross-client：继续按 1.3.8 流程，由 doCheckPermissionChange 的登出守卫兜底
            }
            SaSession session = StpUtil.getSession();
            long now = System.currentTimeMillis();
            long lastCheckAt = Convert.toLong(session.get(LAST_CHECK_AT_KEY), 0L);
            if (now - lastCheckAt < getPermissionSyncIntervalSeconds() * 1000L) {
                // 未到检查周期，避免每个请求都发起远程调用
                return;
            }
            session.set(LAST_CHECK_AT_KEY, now);
            long lastChangeTime = Convert.toLong(session.get(LAST_CHANGE_TIME_KEY), 0L);
            doCheckPermissionChange(session, lastChangeTime);
        } catch (NotLoginException e) {
            // 登录态已在服务端失效：必须向上抛出，让鉴权过滤器立即返回 401 并中断本次请求，
            // 否则已失效的会话还能完成当前这次业务访问
            throw e;
        } catch (Exception e) {
            // 其余异常（网络抖动等）属于旁路能力故障，不应影响业务请求
            log.debug("[permission-sync] 权限变更检查失败: {}", e.getMessage());
        }
    }

    /**
     * 立即检查服务端是否有当前用户的权限变更（<b>忽略节流</b>）
     *
     * <p>供业务方在关键操作前主动调用，例如执行删除、审批等敏感动作前强制拉取最新权限</p>
     *
     * @return true=本次检测到变更并已刷新登录用户；false=无变更或检查失败
     */
    public static boolean checkPermissionChange() {
        try {
            if (!StpUtil.isLogin()) {
                return false;
            }
            // 1.3.9：外来票跳过远程权限检查（详见 checkPermissionChangeIfNeeded 说明）
            if (SsoClientContext.hasForeignOwner() && SsoClientContext.isSkipForeign()) {
                return false;
            }
            SaSession session = StpUtil.getSession();
            long lastChangeTime = Convert.toLong(session.get(LAST_CHANGE_TIME_KEY), 0L);
            return doCheckPermissionChange(session, lastChangeTime);
        } catch (Exception e) {
            log.debug("[permission-sync] 检查权限变更失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 真正的远程检查逻辑
     *
     * <p>协议：上报 {@code userId / tenantId / lastCheckTime}，服务端返回
     * {@code {changed, changeTime}}。服务端<b>不会删除</b>变更标记（靠 TTL 过期），
     * 客户端只需推进本地"已处理时间戳"即可实现去重，刷新失败也能在后续请求中重试</p>
     *
     * @param session        当前用户 Session
     * @param lastChangeTime 本地已处理的服务端变更时间戳
     * @return 是否检测到变更并已刷新
     */
    private static boolean doCheckPermissionChange(SaSession session, long lastChangeTime) {
        SaSsoMessage message = new SaSsoMessage();
        message.setType("checkPermission");
        message.set("loginId", StpUtil.getLoginId());
        message.set("userId", getUserId());
        message.set("tenantId", getTenantId());
        message.set("lastCheckTime", lastChangeTime);
        SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
        message.set("client", clientConfig.getClient());
        // 携带 browserId：服务端据此把会话收敛到“同一浏览器”，实现只退同一浏览器；
        // 取不到（老 token 无该 extra）则不带，服务端退化为按 loginId+client 的旧逻辑（向后兼容，不误判）
        String checkBrowserId = currentBrowserIdExtra();
        if (checkBrowserId != null) {
            message.set("browserId", checkBrowserId);
        }

        Object data;
        try {
            data = getRemoteSaResult(message);
        } catch (NotLoginException e) {
            // 1.3.9 关键守卫：外来票查不到会话，是"本服务无权代表对方的 client 签名"导致的
            // 假阴性（服务端按上报的 client 过滤 SysUserOnline.clientKey），并不代表用户真的掉线。
            // 此时若照常 StpUtil.logout()，会删掉共享 Redis 中的 token 映射，
            // 把该用户在归属客户端下的会话一并摧毁 —— 这正是跨端 401 全域扩散的根因。
            // 本守卫不受 cross-client.enabled 控制：它只是停止一个错误行为，默认即安全。
            if (SsoClientContext.hasForeignOwner()) {
                log.warn("[permission-sync] token 归属客户端({}) 与本服务({}) 不一致，"
                                + "忽略远程未登录结论且不执行本地登出: {}",
                        SsoClientContext.currentOwnerClient(), SsoClientContext.primaryClient(), e.getMessage());
                if (SsoClientContext.isRejectForeign()) {
                    throw e;
                }
                return false;
            }
            // 服务端已无该登录态（被禁用 / 删除 / 强退 / token 过期）→ 本地登出并向上抛出，
            // 由调用方决定是否中断当前请求
            log.info("[permission-sync] 服务端登录态已失效，执行本地登出: {}", e.getMessage());
            logoutQuietly();
            throw e;
        }

        Map<?, ?> result = JsonUtils.convertValue(data, Map.class);
        boolean changed = result != null && BooleanUtil.toBoolean(Convert.toStr(result.get("changed")));
        long changeTime = result == null ? 0L : Convert.toLong(result.get("changeTime"), 0L);

        // 先推进"已处理时间戳"，保证即使刷新失败也不会在同一变更上无限重试
        if (changeTime > lastChangeTime) {
            session.set(LAST_CHANGE_TIME_KEY, changeTime);
        }
        if (!changed) {
            return false;
        }
        log.info("[permission-sync] 检测到权限变更，正在重载登录用户...");
        CloudLoginUser fresh = refreshLoginUser();
        // refreshLoginUser 内部已触发 userInfoChanged 监听器，此处无需重复触发
        log.info("[permission-sync] 登录用户已重载，userId={}", fresh == null ? null : fresh.getUserId());
        return true;
    }

    /**
     * 本地登出（忽略异常）
     */
    private static void logoutQuietly() {
        CloudLoginUser snapshot = currentUserSnapshot();
        try {
            StpUtil.logout();
            fireLogout(snapshot);
        } catch (Exception e) {
            log.debug("[permission-sync] 本地登出失败: {}", e.getMessage());
        }
    }

    /**
     * 权限同步是否启用（{@code sa-token.permission-sync.enabled}，默认 true）
     */
    private static boolean isPermissionSyncEnabled() {
        return BooleanUtil.toBoolean(getValue(CONFIG_ENABLED, "true"));
    }

    /**
     * 获取权限同步检查间隔（秒）
     *
     * <p>本值由 {@link #getRemoteCacheTtlSeconds()} 统一控制：
     * 既用于权限变更检查的请求链路节流，也是「默认档」（权限相关接口）的缓存 TTL，
     * 二者取同一个值才能保证权限变更在 TTL 内被感知。</p>
     *
     * @return 间隔/TTL 秒数，小于 {@link #MIN_REMOTE_CACHE_TTL_SECONDS} 时取最小值
     */
    public static long getPermissionSyncIntervalSeconds() {
        return getRemoteCacheTtlSeconds();
    }

    /**
     * 获取默认档 TTL（秒）—— 权限相关接口（{@link #DEFAULT_TIER_APIS_DESC}）
     *
     * <p>读取顺序：
     * <ol>
     *   <li>{@link #CONFIG_CACHE_TTL_SECONDS}（{@code sa-token.cache.ttl-seconds}）</li>
     *   <li>{@link #CONFIG_INTERVAL_SECONDS}（兼容回退，早期版本的键名）</li>
     *   <li>默认 {@link #DEFAULT_REMOTE_CACHE_TTL_SECONDS}（20 秒）</li>
     * </ol>
     * </p>
     *
     * <p>参考数据档 / 用户信息档不走本方法，见 {@link #getCacheTtlSeconds(String)}。</p>
     *
     * @return TTL 秒数，最小 {@link #MIN_REMOTE_CACHE_TTL_SECONDS}
     */
    public static long getRemoteCacheTtlSeconds() {
        long ttl = DEFAULT_REMOTE_CACHE_TTL_SECONDS;
        // 1. 优先读取 cache.ttl-seconds
        String value = getValue(CONFIG_CACHE_TTL_SECONDS, null);
        // 2. 回退到 permission-sync.interval-seconds
        if (value == null || value.trim().isEmpty()) {
            value = getValue(CONFIG_INTERVAL_SECONDS, null);
        }
        if (value != null && !value.trim().isEmpty()) {
            try {
                ttl = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                log.warn("[cache] TTL 配置非法: {}，使用默认值 {} 秒", value, ttl);
            }
        }
        return Math.max(ttl, MIN_REMOTE_CACHE_TTL_SECONDS);
    }

    /**
     * 获取指定 API 的远程调用结果缓存 TTL（秒）
     *
     * <p>按「档位」解析，优先级从高到低：
     * <ol>
     *   <li><b>单接口例外</b>：{@code sa-token.cache.<apiName>.ttl-seconds}，
     *       仅在某一个接口需要偏离所在档位时才写</li>
     *   <li><b>档位键</b>：接口归属 {@link #TIER_REFERENCE_APIS} 时读
     *       {@link #CONFIG_REFERENCE_TTL_SECONDS}，归属 {@link #TIER_USER_APIS} 时读
     *       {@link #CONFIG_USER_TTL_SECONDS}；档位键未配置则用该档内置默认值</li>
     *   <li><b>默认档</b>：其余接口（{@link #DEFAULT_TIER_APIS_DESC} 及其他未登记接口）
     *       走 {@link #getRemoteCacheTtlSeconds()}，即 {@link #CONFIG_CACHE_TTL_SECONDS}（默认 20 秒）</li>
     * </ol>
     * </p>
     *
     * <p>典型用法：{@code getCacheTtlSeconds("getDeptNameById")=300} 让部门名缓存 5 分钟，
     * 同时保留 {@code getUserInfo()} 等 20 秒。</p>
     *
     * @param apiName API 名称（与 {@link #remoteCache} 第一个参数一致）
     * @return TTL 秒数，最小 {@link #MIN_REMOTE_CACHE_TTL_SECONDS}
     */
    public static long getCacheTtlSeconds(String apiName) {
        if (apiName != null && !apiName.isEmpty()) {
            // 1. 单接口例外：<apiName>.ttl-seconds 优先级最高
            Long perApiTtl = readTtl(CONFIG_API_CACHE_TTL_PREFIX + apiName + CONFIG_API_CACHE_TTL_SUFFIX);
            if (perApiTtl != null) {
                return perApiTtl;
            }
            // 2. 档位：先取该档位 YAML 覆盖，未配置则用内置默认
            if (TIER_REFERENCE_APIS.contains(apiName)) {
                return readTierTtl(CONFIG_REFERENCE_TTL_SECONDS, DEFAULT_REFERENCE_TTL_SECONDS);
            }
            if (TIER_USER_APIS.contains(apiName)) {
                return readTierTtl(CONFIG_USER_TTL_SECONDS, DEFAULT_USER_TTL_SECONDS);
            }
        }
        // 3. 默认档（权限相关及任何未登记的新接口）
        return getRemoteCacheTtlSeconds();
    }

    /**
     * 读取 TTL 配置值（秒）
     *
     * @param key 配置键
     * @return 解析后的 TTL（已按 {@link #MIN_REMOTE_CACHE_TTL_SECONDS} 收敛下限）；
     *         未配置或非法时返回 {@code null}，由调用方决定回退目标
     */
    private static Long readTtl(String key) {
        String value = getValue(key, null);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Math.max(Long.parseLong(value.trim()), MIN_REMOTE_CACHE_TTL_SECONDS);
        } catch (NumberFormatException e) {
            log.warn("[cache] TTL 配置非法 {}={}，忽略该项", key, value);
            return null;
        }
    }

    /**
     * 读取档位 TTL：YAML 未配置或非法时回退到该档位内置默认值
     */
    private static long readTierTtl(String tierKey, long builtinDefault) {
        Long configured = readTtl(tierKey);
        return configured != null ? configured : Math.max(builtinDefault, MIN_REMOTE_CACHE_TTL_SECONDS);
    }

    /**
     * 获取共享缓存 Bean。
     * <p>未集成 Redis 时返回 null，调用方应直接执行 loader，不要走缓存路径。</p>
     */
    private static SharedRemoteCache sharedCache() {
        try {
            return SpringContextUtil.getBean(SharedRemoteCache.class);
        } catch (Exception e) {
            // Bean 不存在：业务未集成 Redis / SDK 未启用 @ComponentScan
            return null;
        }
    }

    /**
     * 清除 SDK 全部缓存（清空共享远程调用结果缓存）
     *
     * <p>删除 Redis 命名空间 {@code cloudauth:security-client-oauth:cache:*} 下的全部 key，
     * 使后续所有远程读取接口都重新拉取认证中心最新数据，
     * 无需等待各档位 TTL 自然过期。适用于后台改完部门 / 字典 / 参数配置后的即时生效场景。</p>
     *
     * <p><b>不影响登录态</b>：不清除 SaSession 中的登录用户快照，用户不会被踢下线；
     * 若还需让当前用户立即重载权限，请另行调用 {@link #refreshLoginUser()}
     * 或 {@link #checkPermissionChange()}。</p>
     *
     * <p>未集成 Redis（缓存降级为不缓存）时不抛异常，返回 0。</p>
     *
     * @return 实际删除的 Redis key 数量；未启用共享缓存时为 0
     */
    public static long clearAllCache() {
        SharedRemoteCache cache = sharedCache();
        if (cache == null) {
            log.info("[cache] 未启用共享缓存（无 Redis 或未扫描到 SharedRemoteCache Bean），无需清理");
            return 0L;
        }
        long deleted = cache.clearAll();
        log.info("[cache] 共享缓存已清空，共删除 {} 个 key", deleted);
        return deleted;
    }

    // ================== 子系统登录会话扩展数据（Session Ext） ==================

    /**
     * 往<b>当前登录用户</b>的 {@code SaSession} 里存一个业务扩展字段（与内置 {@code loginUser} 快照同处一份 Session）。
     *
     * <p><b>特性</b>：按登录用户天然隔离、随登录态存活、<b>登出自动清理</b>，无需自己拼 loginId。</p>
     * <p><b>适用</b>：用户维度、量小、生命周期跟随登录的标志位 / 短数据。</p>
     * <p><b>不适用</b>：跨用户共享的数据（会各存一份）、大对象 / 高频写
     * （每次 {@code set} 会序列化整个 Session blob，代价随 Session 体积增长）、需要“按 key 独立 TTL”的数据
     * （Session 生命周期 = token，无字段级过期，如需请自行存时间戳判断）。</p>
     *
     * <p>{@code value == null} 时删除该字段；未登录抛 {@code NotLoginException}（与其他登录态接口一致）。</p>
     *
     * <p><b>撞名保护</b>：key 命中 SDK 内置 Session 键（{@code loginUser/tenantId/userId/userName/nickName/
     * deptId/deptName/deptCategory/clientid/browserId} 等）时直接抛 {@link IllegalArgumentException}，
     * 不会被写入（这些键由 {@link #refreshLoginUser()} 等内部逻辑维护）。</p>
     *
     * @since 1.3.5
     */
    public static void setSessionExt(String key, Object value) {
        requireLogin();
        checkSessionExtKey(key);
        SaSession session = StpUtil.getSession();
        if (value == null) {
            session.delete(key);
            return;
        }
        session.set(key, value);
    }

    /**
     * 批量写入当前登录用户 Session（等价于 {@code data.forEach(StpUtil.getSession()::set)}）。
     * <p>单个 value 为 {@code null} 时删除该 key；未登录抛 {@code NotLoginException}。</p>
     *
     * @since 1.3.5
     */
    public static void setSessionExt(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        requireLogin();
        // 先整体校验 key（空 / 撞名），fail-fast，避免部分写入后才报错
        data.keySet().forEach(CloudPlatformHelper::checkSessionExtKey);
        SaSession session = StpUtil.getSession();
        data.forEach((k, v) -> {
            if (v == null) {
                session.delete(k);
            } else {
                session.set(k, v);
            }
        });
    }

    /**
     * 读取当前登录用户 Session 扩展字段并转换为 {@code type}；未登录 / 无值返回 {@code null}。
     * <p>用 {@code convertValue} 兜底：无论 sa-token 序列化器还原出的是原对象还是 Map，都能拿到强类型。</p>
     *
     * @since 1.3.5
     */
    public static <T> T getSessionExt(String key, Class<T> type) {
        Object raw = getRawSessionExt(key);
        return raw == null ? null : JsonUtils.convertValue(raw, type);
    }

    /**
     * 读取当前登录用户 Session 扩展字段并转换为 {@code List<elementType>}；未登录 / 无值返回空列表。
     *
     * @since 1.3.5
     */
    public static <T> List<T> getSessionExtList(String key, Class<T> elementType) {
        Object raw = getRawSessionExt(key);
        if (raw == null) {
            return new ArrayList<>();
        }
        JavaType listType = JsonUtilsHolder.MAPPER.getTypeFactory()
                .constructCollectionType(List.class, elementType);
        return JsonUtils.convertValue(raw, listType);
    }

    /**
     * 读取当前登录用户 Session 扩展字段的原始值（不做类型转换）；未登录 / 无值返回 {@code null}。
     *
     * @since 1.3.5
     */
    public static Object getSessionExt(String key) {
        return getRawSessionExt(key);
    }

    /**
     * 判断当前登录用户 Session 是否含某扩展字段；未登录返回 {@code false}。
     *
     * @since 1.3.5
     */
    public static boolean hasSessionExt(String key) {
        return getRawSessionExt(key) != null;
    }

    /**
     * 删除当前登录用户 Session 的某扩展字段（未登录静默返回）。
     *
     * @since 1.3.5
     */
    public static void delSessionExt(String key) {
        if (!StpUtil.isLogin() || CharSequenceUtil.isBlank(key)) {
            return;
        }
        if (RESERVED_SESSION_KEYS.contains(key)) {
            throw new IllegalArgumentException("Session 扩展数据 key 与 SDK 内置键撞名，禁止删除: " + key);
        }
        StpUtil.getSession().delete(key);
    }

    /** 内部：读取原始 Session 值；未登录 / key 空均返回 {@code null}。 */
    private static Object getRawSessionExt(String key) {
        if (!StpUtil.isLogin() || CharSequenceUtil.isBlank(key)) {
            return null;
        }
        return StpUtil.getSession().get(key);
    }

    /**
     * SDK 内置占用的 Session 键，扩展数据不得使用。
     * <p>这些键由 {@link #cacheLoginUser} / {@link #refreshLoginUser()} / 权限同步节流等内部逻辑维护，
     * 若被子系统写入会直接破坏登录态，或在下一次刷新时被覆盖。</p>
     */
    private static final Set<String> RESERVED_SESSION_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            LOGIN_USER_KEY, TENANT_KEY, USER_KEY, USER_NAME_KEY, NICK_NAME_KEY,
            DEPT_KEY, DEPT_NAME_KEY, DEPT_CATEGORY_KEY, CLIENT_KEY, BROWSER_ID_KEY,
            LAST_CHECK_AT_KEY, LAST_CHANGE_TIME_KEY)));

    /** 校验扩展数据 key：非空且未与 SDK 内置 Session 键撞名，否则抛 {@link IllegalArgumentException}。 */
    private static void checkSessionExtKey(String key) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("Session 扩展数据 key 不能为空");
        }
        if (RESERVED_SESSION_KEYS.contains(key)) {
            throw new IllegalArgumentException("Session 扩展数据 key 与 SDK 内置键撞名，禁止使用: " + key
                    + "（内置键: " + RESERVED_SESSION_KEYS + "）");
        }
    }

    // ================== 用户信息（UserInfo） ==================

    /**
     * 获取用户信息（按 loginId 缓存，TTL 由 {@code sa-token.cache.ttl-seconds} 控制）
     */
    public static CloudUserInfoVo getUserInfo() {
        if (!StpUtil.isLogin()) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未能读取到有效token或者token已过期", null).setCode(11011);
        }
        // 1.3.9 外来票守卫：本服务 client 与票上归属不一致时，中心按 client 过滤必查不到 → 401。
        // 退化为用共享 Session 已有的登录用户即时拼装，不发起远程、也**不写共享缓存**
        // （避免降级结果污染归属客户端下的正常数据）。
        if (SsoClientContext.hasForeignOwner() && SsoClientContext.isSkipForeign()) {
            return buildUserInfoFromLoginUser(getLoginUser());
        }
        Object loginId = StpUtil.getLoginId();
        return remoteCache("userInfo", loginIdCacheKey(loginId), CloudUserInfoVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("userInfo");
            message.set("loginId", loginId);
            SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
            message.set("client", clientConfig.getClient());
            Object data = getRemoteSaResult(message);
            if (data instanceof CloudUserInfoVo) {
                return (CloudUserInfoVo) data;
            }
            return JsonUtils.convertValue(data, CloudUserInfoVo.class);
        });
    }

    // ================== 登录态判断 ==================

    /**
     * 检查当前用户是否已登录（仅本地 token 校验，O(1)，无远程调用）
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 读取当前本地 token extra 里的 browserId（登录时由 {@link #doLogin} 写入，与认证中心同源）。
     *
     * <p>供总线消息显式携带：授权中心据此把会话收敛到“同一浏览器”，实现只退同一浏览器。
     * 取不到（未登录 / 老 token 无该 extra）时返回 {@code null}，调用方据此让服务端退化为
     * 按 loginId+client 的旧逻辑（向后兼容，不误判）。</p>
     */
    private static String currentBrowserIdExtra() {
        try {
            Object bid = StpUtil.getExtra(BROWSER_ID_KEY);
            if (bid == null) {
                return null;
            }
            String s = String.valueOf(bid);
            return (s.isEmpty() || "null".equals(s)) ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 远程校验当前用户是否仍处于登录态（按 loginId 缓存，TTL 与权限同步共用）
     * <p>仅在关键操作前使用，业务请求应使用 {@link #isLogin()}（本地 O(1)）</p>
     *
     * <p>缓存策略：只有 {@link NotLoginException}（服务端已确认登出）才缓存 {@code false}；
     * 其它异常（网络抖动 / 服务端 500 / Redis 暂时不可达）返回 {@code null}，即 <b>不缓存</b>，
     * 让下一次请求重试，避免一次网络抖动把用户锁 20 秒。
     * 代价是每次抖动都会重新远程调用，但相比"被错误地锁 20 秒"的安全代价要小得多。</p>
     */
    public static boolean isRemoteLogin() {
        if (!StpUtil.isLogin()) {
            return false;
        }
        // 1.3.9：外来票无法用本服务密钥向中心证明其会话有效性，远程结论必然是假阴性；
        // 跳过远程、以本地票校验为准（本地 Session + JWT 签名仍可信）。
        if (SsoClientContext.hasForeignOwner() && SsoClientContext.isSkipForeign()) {
            return StpUtil.isLogin();
        }
        Object loginId = StpUtil.getLoginId();
        Boolean cached = remoteCache("isRemoteLogin", loginIdCacheKey(loginId), Boolean.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("isLogin");
            message.set("loginId", loginId);
            SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
            message.set("client", clientConfig.getClient());
            // 携带 browserId：服务端据此只认“同一浏览器”的会话，避免其它浏览器同账号仍在线时被误判为在线
            String loginBrowserId = currentBrowserIdExtra();
            if (loginBrowserId != null) {
                message.set("browserId", loginBrowserId);
            }
            try {
                Object data = getRemoteSaResult(message);
                return BooleanUtil.toBoolean(Convert.toStr(data));
            } catch (NotLoginException e) {
                // 服务端已确认该 loginId 失效（被禁用 / 删除 / 强退 / token 过期），
                // 缓存 false 让后续请求立即得到一致结论
                log.info("[cache] isRemoteLogin 服务端确认登出，缓存 false: {}", e.getMessage());
                return Boolean.FALSE;
            } catch (Exception e) {
                // 网络抖动 / 服务端 5xx / 序列化异常等：不确定状态，不缓存结果，
                // 下次请求重新尝试远程调用
                log.warn("[cache] isRemoteLogin 异常，不缓存结果（下次重试）: {}", e.getMessage());
                return null;
            }
        });
        return Boolean.TRUE.equals(cached);
    }

    // ================== 路由信息 ==================

    /**
     * 获取前端路由信息（按 loginId 缓存，TTL 与权限同步共用）
     */
    public static List<CloudRouterVo> getRouters() {
        if (!StpUtil.isLogin()) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未能读取到有效token或者token已过期", null).setCode(11011);
        }
        // 1.3.9 外来票守卫：菜单由中心按 client 组装，外来票无法用本服务 client 取得（过滤不到 → 401），
        // 本地也无菜单数据源；故返回空列表而非 401，且**不写共享缓存**（避免污染归属客户端下的菜单）。
        if (SsoClientContext.hasForeignOwner() && SsoClientContext.isSkipForeign()) {
            return new ArrayList<>();
        }
        Object loginId = StpUtil.getLoginId();
        return remoteCacheList("routers", loginIdCacheKey(loginId), CloudRouterVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getRouters");
            message.set("loginId", loginId);
            SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
            message.set("client", clientConfig.getClient());
            Object data = getRemoteSaResult(message);
            if (data instanceof List) {
                return JsonUtils.convertValue(data, JsonUtilsHolder.LIST_OF_ROUTER_VO);
            }
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudRouterVo.class);
        });
    }

    /**
     * 由本地 Session 的登录用户即时拼装 {@link CloudUserInfoVo}（1.3.9 外来票守卫）。
     *
     * <p>外来票不能按本服务 client 远程拉取（中心按 client 过滤会话，必查不到 → 401），
     * 故用共享 Session 中已有的 {@link CloudLoginUser} 拼装：基本信息 + 角色/权限集合。
     * 字段以 Session 中可得者为准（含 {@code phonenumber}，登录时已由服务端 {@code loginUser}
     * 消息一并缓存）；Session 中没有的远程专有字段（邮箱 / 帐号状态等）留空。</p>
     */
    private static CloudUserInfoVo buildUserInfoFromLoginUser(CloudLoginUser loginUser) {
        CloudUserInfoVo vo = new CloudUserInfoVo();
        if (loginUser == null) {
            return vo;
        }
        CloudUserVo user = new CloudUserVo();
        user.setUserId(loginUser.getUserId());
        user.setDeptId(loginUser.getDeptId());
        user.setDeptName(loginUser.getDeptName());
        user.setUserName(loginUser.getUsername());
        user.setNickName(loginUser.getNickname());
        user.setPhonenumber(loginUser.getPhonenumber());
        user.setUserType(loginUser.getUserType());
        user.setClientId(loginUser.getClientKey());
        // 岗位名称列表
        if (CollUtil.isNotEmpty(loginUser.getPosts())) {
            List<String> postNames = new ArrayList<>();
            for (CloudPostDTO post : loginUser.getPosts()) {
                if (CharSequenceUtil.isNotBlank(post.getPostName())) {
                    postNames.add(post.getPostName());
                }
            }
            user.setPostNames(postNames);
        }
        vo.setUser(user);
        vo.setPermissions(loginUser.getMenuPermission());
        vo.setRoles(loginUser.getRolePermission());
        return vo;
    }

    // ================== 登录流程 ==================

    /**
     * 根据 ticket 获取登录信息并完成本地登录。
     *
     * <p><b>ticket 由 sa-token 保证一次性有效，校验后立即删除</b>。认证中心回跳地址把 ticket 作为
     * 明文 query 参数留在浏览器地址栏与历史记录中，重复提交必然失败；本端<b>不做幂等重放缓存</b>：
     * JVM 本地缓存多实例部署下不生效、进程重启即丢失，且无法解决前端把"无效 ticket"当成
     * 登录超时并清除本地 token 的根本问题。</p>
     *
     * <p><b>重复消费由客户端前端的 SSO 中转页保证不发生</b>，接入时必须落实：
     * ① 拿到登录结果后立即用 {@code history.replaceState} 清除 URL 上的 ticket；
     * ② 跳 back 用 {@code location.replace}，且 back 为空时兜底业务首页；
     * ③ 进入中转页先校验本地登录态，已登录直接跳 back，不再消费 ticket；
     * ④ 把"无效 ticket / 票已消费"与"登录超时"区分开，本地 token 仍有效时按已登录处理，
     * 不要无条件清 token 或重新发起 silent 授权。详见 SDK 接入说明中的 SSO 中转页实现。</p>
     */
    public static CloudLoginVo doLoginByTicket(String ticket) {
        SaCheckTicketResult ctr = SaSsoClientProcessor.instance.checkTicket(ticket);
        return doLogin(ctr);
    }

    /**
     * 实际登录逻辑：完成本地登录、拉取用户全量数据、写入 Session
     */
    private static CloudLoginVo doLogin(SaCheckTicketResult ctr) {
        SaLoginParameter model = new SaLoginParameter()
                .setTimeout(ctr.remainTokenTimeout)
                .setActiveTimeout(ctr.remainSessionTimeout)
                .setDeviceId(ctr.deviceId)
                // 把当前请求线程的 browserId 写入 token extra，
                // auth server 也会从同一次请求的 X-Browser-Id header 读到同一个值，
                // 实现 SDK 本地 token 与认证中心 token 的 browserId 同步
                .setExtra(BROWSER_ID_KEY, BrowserIdContext.current());
        // 1.3.9：把"本 token 归属哪个授权客户端"钉到票上，随请求流转。
        // 此后任何子系统（含与本服务 client 不同的共享服务）都能只解 JWT 签名读出归属，
        // 无需对方 secret-key，也不依赖本服务的 Nacos 配置。
        String ownerClient = resolveOwnerClient(ctr);
        if (CharSequenceUtil.isNotBlank(ownerClient)) {
            model.setExtra(SsoClientContext.TOKEN_EXTRA_CLIENT_KEY, ownerClient);
        }
        StpUtil.login(ctr.loginId, model);

        // 拉取用户全量数据 + 写入 Session 缓存（仅远程拉 1 次）
        CloudLoginUser loginUser = null;
        try {
            loginUser = fetchLoginUserFromRemote();
            if (loginUser != null) {
                cacheLoginUser(loginUser);
                // extra 复用同一份 loginUser，避免重复远程（缺陷 B-2）
                Map<String, Object> extra = buildLoginExtra(loginUser);
                SaSession saSession = StpUtil.getSessionByLoginId(ctr.loginId);
                Objects.requireNonNull(saSession, "Account-Session 获取失败: loginId=" + ctr.loginId);
                // 跳过 null 值：SaSession 底层 dataMap 为 ConcurrentHashMap，put(key, null) 会直接抛 NPE
                // （null 字段直接跳过，保留 Session 中该 key 的旧值即可）
                extra.forEach((k, v) -> {
                    if (v != null) {
                        saSession.set(k, v);
                    }
                });
            }
        } catch (Exception e) {
            // 必须打印完整堆栈：NPE 的 getMessage() 为 null，仅打 message 无法定位
            log.warn("登录后加载用户信息失败，将仅使用本地 token 登录", e);
        }

        // 触发子系统登录监听器（此时全量用户数据已写入 Session，可安全读取）
        if (loginUser != null) {
            fireLogin(loginUser);
        }

        SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
        CloudLoginVo loginVo = new CloudLoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(clientConfig.getClient());
        return loginVo;
    }

    /**
     * 解析本次登录 token 的归属授权客户端（1.3.9）。
     *
     * <p>优先级：认证中心票上的 {@code clientid} extra → 本服务 {@code sa-token.sso-client.client}。</p>
     *
     * <p>中心各登录策略（password / sms / email / social / xcx）与 SSO 免登补登录路径
     * 均已 {@code model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId())}，
     * 键名与 SDK 的 {@link #CLIENT_KEY}（{@code "clientid"}）一致，且中心与子系统的
     * {@code jwt-secret-key} 同源，故本地可直接验签解析，<b>无需授权中心改造</b>。</p>
     *
     * <p>回退到本服务配置是安全的：执行 doLogin（签发本地票）的服务，其配置的 client
     * 就是本次登录的 client。</p>
     */
    private static String resolveOwnerClient(SaCheckTicketResult ctr) {
        if (ctr != null && CharSequenceUtil.isNotBlank(ctr.tokenValue)) {
            try {
                // 只验签解析，不查 Redis、不要求登录态
                Object v = StpUtil.stpLogic.getExtra(ctr.tokenValue, CLIENT_KEY);
                String fromCenter = v == null ? null : String.valueOf(v);
                if (CharSequenceUtil.isNotBlank(fromCenter) && !"null".equals(fromCenter)) {
                    return fromCenter.trim();
                }
            } catch (Exception e) {
                log.debug("[sso-client] 从认证中心 token 解析 clientid 失败，回退本服务配置: {}", e.getMessage());
            }
        }
        return SsoClientContext.primaryClient();
    }

    // ================== 认证中心免登接口（走 pushS 总线，登录前调用） ==================

    /**
     * 账号密码登录：通过 pushS 消息总线调用认证中心 {@code login}（免登、自动签名，不触发 @ApiEncrypt），
     * 认证中心校验通过后签发一次性 ticket，本方法复用 {@link #doLoginByTicket(String)} 完成本地登录并拉取全量用户数据。
     *
     * <p>之所以走总线而非直连 {@code /login}：认证中心 {@code /login} 挂了 {@code @ApiEncrypt}，
     * 明文透传会被 CryptoFilter 拦截；而 {@code /sso/pushS} 仅校验 {@code client + sign}、不校验登录态，
     * 服务端 handler 进程内直调 {@code tokenController.login} 又绕开了 {@code @ApiEncrypt}，
     * 因此客户端<b>无需持有、也无需下发任何 RSA 公钥</b>。</p>
     *
     * <p>{@code loginBody} 需包含认证中心 {@code LoginBody} 约定字段：
     * {@code grantType}（如 {@code "password"}）、{@code username}、{@code password}，
     * 开启图形验证码时还需 {@code code}、{@code uuid}（{@code uuid} 来自 {@link #getCaptcha()}）。
     * 其中 {@code clientId}、{@code tenantId} 由 SDK 自动兜底，无需前端传入。</p>
     *
     * @param loginBody 登录参数（原样转交认证中心校验）
     * @return 子系统本地登录结果（含子系统本地 token）
     * @since 1.3.3
     */
    public static CloudLoginVo loginByPassword(CloudLoginBo loginBody) {
        return loginViaTicket("login", loginBody);
    }

    /**
     * 账号密码登录（<b>免图形验证码</b>）：入参为 {@link CloudLoginNoCaptchaBo}（只含 {@code username}、{@code password}，
     * 刻意不含 {@code code}/{@code uuid} 图形码字段），走服务端独立的 {@code loginByPasswordNoCaptcha} handler，明确不校验图形验证码。
     *
     * <p><b>安全设计</b>：是否跳过验证码由“命中哪个端点 / 总线 type”决定，而<b>非</b>客户端 body 里的某个字段——
     * 服务端 {@code loginByPasswordNoCaptcha} handler 会强制 {@code ignoreCaptcha=true}、普通 {@code login} handler 强制
     * {@code ignoreCaptcha=false}，因此 {@link #loginByPassword} 的图形验证码无法通过构造报文被绕过。</p>
     *
     * <p>适用于子系统自有风控、无需图形码的密码登录场景。成功后同样以一次性 ticket 桥接本地登录。</p>
     *
     * @since 1.3.4
     */
    public static CloudLoginVo loginByPasswordNoCaptcha(CloudLoginNoCaptchaBo loginBody) {
        return loginViaTicket("loginByPasswordNoCaptcha", loginBody);
    }

    /**
     * 手机号 + 短信验证码登录：走总线调认证中心 {@code loginByPhone}，成功后同样以 ticket 桥接本地登录。
     *
     * <p>{@code loginBody} 需包含：{@code phonenumber}、{@code smsCode}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
     *
     * @since 1.3.3
     */
    public static CloudLoginVo loginByPhone(CloudPhoneLoginBo loginBody) {
        return loginViaTicket("loginByPhone", loginBody);
    }

    /**
     * 微信小程序登录：小程序 {@code wx.login} 拿到临时 code 后，走总线调认证中心通用 {@code login} handler
     * （{@code grantType=xcx}，由服务端 {@code IAuthStrategy} 路由到 {@code XcxAuthStrategy}），
     * 认证中心用 xcxCode 换 openid 并匹配/绑定用户，成功后以一次性 ticket 桥接本地登录。
     *
     * <p><b>复用而非新增 handler</b>：本方法与 {@link #loginByPassword(CloudLoginBo)} 发送同一个总线
     * type {@code "login"}，仅 {@code grantType} 不同（{@code xcx}），因此认证中心<b>无需单独注册
     * {@code loginByWechat} handler</b>。前置条件：调用方 client 授权类型需含 {@code xcx}。</p>
     *
     * <p>{@code loginBody} 需包含：{@code xcxCode}（小程序临时凭证，注意字段名非 {@code code}）
     * （{@code appid}、{@code clientId}、{@code tenantId} 可选，缺省由 SDK / 认证中心处理）。</p>
     *
     * @since 1.3.3
     */
    public static CloudLoginVo loginByWechat(CloudWechatLoginBo loginBody) {
        return loginViaTicket("login", loginBody);
    }

    /**
     * ticket 桥接：走总线拿认证中心返回的一次性 ticket，再交给现成的 {@link #doLoginByTicket(String)}，
     * 复用本地登录 + session 填充 + 全量用户拉取逻辑（登录内核零改动）。
     */
    private static CloudLoginVo loginViaTicket(String type, Object loginBody) {
        Object ticket = callApi(type, wrapBody(loginBody));
        if (ticket == null || CharSequenceUtil.isBlank(String.valueOf(ticket))) {
            throw new BackResultException("认证中心登录未返回有效 ticket");
        }
        return doLoginByTicket(String.valueOf(ticket));
    }

    /**
     * 用户注册（通用超集入参）：走总线调认证中心 {@code register}（免登、自动签名），失败时由 {@code callApi} 抛业务异常。
     *
     * <p>认证中心 {@code register} 为<b>单端点、按字段数据驱动</b>：图形码场景填 {@code uuid}+{@code code}，
     * 短信场景填 {@code phonenumber}+{@code code}（此时 {@code uuid} 留空即可，非缺陷）。验证码统一由
     * {@code code} 字段承载。若希望编译期就避免字段混用，建议改用 {@link #registerByCaptcha} 或 {@link #registerBySms}。</p>
     *
     * <p><b>自动角色</b>：{@code roleKeys} 未显式传值时，SDK 自动注入客户端配置
     * {@code sa-token.register-role-keys}，服务端在用户未绑定角色时按标识自动绑定（无值不管）。</p>
     *
     * @return {@code true} 表示注册请求成功；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean register(CloudRegisterBo registerBody) {
        callApi("register", wrapRegisterBody(registerBody));
        return true;
    }

    /**
     * 用户注册（<b>图形验证码</b>场景）：走总线调认证中心 {@code register}，服务端按 {@code CAPTCHA_CODE_KEY + uuid} 校验 {@code code}。
     *
     * <p>需先调 {@link #getCaptcha()} 拿 {@code uuid} 与图片，用户输入 {@code code} 后连同 {@code username}、{@code password} 提交。
     * 与 {@link #register} 走同一个服务端 {@code register}（type 相同），仅入参 BO 语义更收敛。
     * 自动角色注入规则同 {@link #register}。</p>
     *
     * @return {@code true} 表示注册请求成功；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean registerByCaptcha(CloudRegisterByCaptchaBo registerBody) {
        callApi("register", wrapRegisterBody(registerBody));
        return true;
    }

    /**
     * 用户注册（<b>手机号 + 短信验证码</b>场景）：走总线调认证中心 {@code register}，服务端按 {@code CAPTCHA_CODE_KEY + phonenumber} 校验 {@code code}。
     *
     * <p>需先调 {@link #sendSmsCodeForRegister(CloudSendSmsForRegisterBo)}（面向未注册号码）用 {@code phonenumber} 收码，
     * 再把 6 位码放进 {@link CloudRegisterBySmsBo#getCode() code} 字段提交（短信码字段名是 {@code code}，非 {@code smsCode}）。
     * 与 {@link #register} 走同一个服务端 {@code register}（type 相同），仅入参 BO 语义更收敛。
     * 自动角色注入规则同 {@link #register}。</p>
     *
     * @return {@code true} 表示注册请求成功；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean registerBySms(CloudRegisterBySmsBo registerBody) {
        callApi("register", wrapRegisterBody(registerBody));
        return true;
    }

    /**
     * 发送短信验证码（用户名 + 密码校验维度）：走总线调认证中心 {@code sendSmsCode}。
     *
     * <p>{@code body} 需包含：{@code username}、{@code password}、{@code tenantId}。</p>
     *
     * @return {@code true} 表示短信已下发；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean sendSmsCode(CloudSendSmsBo body) {
        callApi("sendSmsCode", wrapBody(body));
        return true;
    }

    /**
     * 发送短信验证码（仅手机号，用于手机号登录）：走总线调认证中心 {@code sendSmsCodeByPhone}。
     *
     * <p>{@code body} 需包含：{@code phonenumber}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
     *
     * @return {@code true} 表示短信已下发；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean sendSmsCodeByPhone(CloudSendSmsByPhoneBo body) {
        callApi("sendSmsCodeByPhone", wrapBody(body));
        return true;
    }

    /**
     * 发送短信验证码（注册场景）：走总线调认证中心 {@code sendSmsCodeForRegister}。
     *
     * <p>与 {@link #sendSmsCodeByPhone(CloudSendSmsByPhoneBo)} 的关键区别：注册验证码面向<b>尚未注册</b>的手机号，
     * 服务端不应校验手机号是否已存在；配合 {@link #register(CloudRegisterBo)} 使用。</p>
     *
     * <p>{@code body} 需包含：{@code phonenumber}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
     *
     * @return {@code true} 表示短信已下发；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean sendSmsCodeForRegister(CloudSendSmsForRegisterBo body) {
        callApi("sendSmsCodeForRegister", wrapBody(body));
        return true;
    }

    /**
     * 发送短信验证码（重置密码场景）：走总线调认证中心 {@code sendSmsCodeByReset}。
     *
     * <p>{@code body} 需包含：{@code username}、{@code oldPassword}、{@code newPassword}、{@code tenantId}。</p>
     *
     * @return {@code true} 表示短信已下发；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean sendSmsCodeByReset(CloudSendSmsByResetBo body) {
        callApi("sendSmsCodeByReset", wrapBody(body));
        return true;
    }

    /**
     * 忘记密码 / 重置密码：走总线调认证中心 {@code resetPassword}，凭手机号 + 短信验证码提交新密码完成改密。
     *
     * <p>典型流程：先调 {@link #sendSmsCodeByPhone(CloudSendSmsByPhoneBo)}（或 {@code /sso/sendSmsCodeByPhone}）
     * 用手机号收取短信码，再调本方法提交 {@code phonenumber}、{@code smsCode}、{@code newPassword} 完成重置。</p>
     *
     * <p>{@code body} 需包含：{@code phonenumber}、{@code smsCode}、{@code newPassword}（{@code clientId}、{@code tenantId} 由 SDK 自动兜底）。</p>
     *
     * @return {@code true} 表示改密成功；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean resetPassword(CloudResetPasswordBo body) {
        callApi("resetPassword", wrapBody(body));
        return true;
    }

    /**
     * 获取图形验证码：走总线调认证中心 {@code captcha}，返回 {@link CloudCaptchaVo}（uuid + base64 图片）。
     *
     * <p>前端把 {@code uuid} 连同用户输入的 {@code code} 一起放进登录 / 注册 body 回传即可。</p>
     *
     * @since 1.3.3
     */
    public static CloudCaptchaVo getCaptcha() {
        return callApi("captcha", null, CloudCaptchaVo.class);
    }

    /**
     * 修改密码（<b>登录态</b>）：走总线调认证中心 {@code updatePassword}，凭当前用户旧密码提交新密码完成改密。
     *
     * <p>与 {@link #resetPassword(CloudResetPasswordBo)}（忘记密码、走短信码）不同：本方法要求当前已登录，
     * SDK 自动注入当前 {@code loginId}，前端只需传 {@code oldPassword}、{@code newPassword}。</p>
     *
     * @return {@code true} 表示改密成功；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.3.3
     */
    public static boolean updatePassword(CloudUpdatePasswordBo body) {
        requireLogin();
        callApi("updatePassword", wrapBodyLogin(body));
        return true;
    }

    /**
     * 新增用户（<b>登录态</b>）：走总线调认证中心 {@code addUser}，由当前登录用户在其所属租户下创建新用户。
     *
     * <p>支持用户名、密码、手机号、昵称、部门、邮箱、性别、状态、备注、角色编码等信息。
     * SDK 自动注入当前操作人 {@code loginId}，服务端以操作人会话的 {@code tenantId}/{@code clientId} 为准。</p>
     *
     * <p><b>密码</b>可留空（为空走服务端默认密码 {@code sys.user.initPassword}）；
     * <b>角色编码 {@code roleKeys}</b> 为纯透传字段，由接入系统自行填充，SDK 不做默认注入。</p>
     *
     * <p><b>用户已存在</b>：若账号在操作人租户下已存在且本次传了 {@code roleKeys}，服务端不重复建号，
     * 只给该用户<b>追加绑定</b>命中的角色（不覆盖已有绑定、超级管理员角色禁止绑定），
     * 并返回既有用户的 {@code userId}；未传 {@code roleKeys} 时仍按原有"账号已存在"报错。</p>
     *
     * @return 用户的 {@code userId}（新建或已存在补绑角色时均返回）；需服务端 {@code addUser} handler
     *         通过 {@code SaResult.setData} 回传，未回传时为 {@code null}
     * @since 1.5.2
     */
    public static Long addUser(CloudAddUserBo body) {
        requireLogin();
        return callApi("addUser", wrapBodyLogin(body), Long.class);
    }

    /**
     * 修改当前登录人信息（<b>登录态</b>）：走总线调认证中心 {@code updateUser}，修改本人资料字段。
     *
     * <p>SDK 自动注入当前 {@code loginId}，服务端只允许改本人，{@code userId}/{@code tenantId} 以会话为准。</p>
     *
     * <p><b>按需更新</b>：只改传入的字段，未传字段保持原值不动，不会被清空。</p>
     *
     * @return {@code true} 表示修改成功；失败会抛异常，由全局异常处理器转为 HTTP 响应
     * @since 1.5.2
     */
    public static boolean updateCurrentUser(CloudUpdateUserBo body) {
        requireLogin();
        callApi("updateUser", wrapBodyLogin(body));
        return true;
    }

    /**
     * 启用/停用其他用户（<b>登录态</b>）：走总线调认证中心 {@code updateUserStatus}，按目标用户ID操作。
     *
     * <p><b>SDK 不暴露 HTTP 端点</b>：权限由接入系统在其自建接口中承担，接入系统应在自己创建的接口里
     * 加权限校验后调用本方法。服务端不做权限字符校验，仅以操作人会话租户为准做隔离，
     * 并禁止禁用超级管理员、写审计日志（sys_oper_log）以便追溯。</p>
     *
     * <p><b>返回值</b>：成功返回 {@code true}；业务失败（用户不存在、禁止禁用超管、非法状态等）
     * 返回 {@code false}。操作人登录态失效（401）/ 无权限（403）等鉴权异常仍向上抛出，不参与成功/失败判定。</p>
     *
     * @param userId 目标用户ID
     * @param status 状态：{@code "0"} 正常 / {@code "1"} 停用
     * @return 是否更新成功
     * @since 1.5.7
     */
    public static boolean updateUserStatusByUserId(Long userId, String status) {
        requireLogin();
        CloudUpdateUserStatusBo bo = new CloudUpdateUserStatusBo();
        bo.setUserId(userId);
        bo.setStatus(status);
        return invokeUpdateUserStatus(bo);
    }

    /**
     * 启用/停用其他用户（<b>登录态</b>）：走总线调认证中心 {@code updateUserStatus}，按目标账号（userName）操作。
     *
     * <p>服务端在<b>操作人会话所属租户</b>内解析账号对应的 userId，跨租户 / 不存在的账号解析不到，
     * 返回“用户不存在或无权限”。其余护栏同 {@link #updateUserStatusByUserId(Long, String)}。</p>
     *
     * <p><b>返回值</b>：同 {@link #updateUserStatusByUserId(Long, String)}，成功 {@code true}，业务失败 {@code false}。</p>
     *
     * @param userName 目标用户账号
     * @param status   状态：{@code "0"} 正常 / {@code "1"} 停用
     * @return 是否更新成功
     * @since 1.5.7
     */
    public static boolean updateUserStatusByUsername(String userName, String status) {
        requireLogin();
        CloudUpdateUserStatusBo bo = new CloudUpdateUserStatusBo();
        bo.setUserName(userName);
        bo.setStatus(status);
        return invokeUpdateUserStatus(bo);
    }

    /**
     * 统一发起 {@code updateUserStatus} 总线调用，并把结果收敛为 {@code boolean}。
     * 业务失败（{@link BackResultException}）返回 {@code false}；鉴权异常（401/403）仍向上抛出。
     */
    private static boolean invokeUpdateUserStatus(CloudUpdateUserStatusBo bo) {
        try {
            callApi("updateUserStatus", wrapBodyLogin(bo));
            return true;
        } catch (BackResultException e) {
            return false;
        }
    }

    /**
     * 停用指定用户（按 userId，禁用其登录）。等价于 {@link #updateUserStatusByUserId(Long, String)} 传 {@code "1"}。
     *
     * @return 是否成功
     */
    public static boolean disableUserByUserId(Long userId) {
        return updateUserStatusByUserId(userId, "1");
    }

    /**
     * 启用指定用户（按 userId，恢复其登录）。等价于 {@link #updateUserStatusByUserId(Long, String)} 传 {@code "0"}。
     *
     * @return 是否成功
     */
    public static boolean enableUserByUserId(Long userId) {
        return updateUserStatusByUserId(userId, "0");
    }

    /**
     * 停用指定用户（按账号 userName，禁用其登录）。等价于 {@link #updateUserStatusByUsername(String, String)} 传 {@code "1"}。
     *
     * @return 是否成功
     */
    public static boolean disableUserByUsername(String userName) {
        return updateUserStatusByUsername(userName, "1");
    }

    /**
     * 启用指定用户（按账号 userName，恢复其登录）。等价于 {@link #updateUserStatusByUsername(String, String)} 传 {@code "0"}。
     *
     * @return 是否成功
     */
    public static boolean enableUserByUsername(String userName) {
        return updateUserStatusByUsername(userName, "0");
    }

    /**
     * 删除用户（<b>登录态</b>）：走总线调认证中心 {@code deleteUser}，按目标用户ID操作。
     *
     * <p><b>SDK 不暴露 HTTP 端点</b>：权限由接入系统在其自建接口中承担，接入系统应在自己创建的接口里
     * 加权限校验后调用本方法。服务端以操作人会话租户为准做隔离，判定规则：</p>
     * <ul>
     *   <li>用户<b>仅绑定该唯一角色</b>时，解除角色后无角色，<b>删除用户</b>；</li>
     *   <li>用户<b>绑定多个角色</b>时，<b>仅解除该角色绑定</b>（保留用户）；</li>
     *   <li>用户<b>不属于该角色</b>时，返回 {@code false}（无需解除）。</li>
     * </ul>
     * <p>服务端并禁止操作超级管理员、写审计日志（sys_oper_log）以便追溯。</p>
     *
     * <p><b>返回值</b>：成功 {@code true}，业务失败（用户不存在 / 不属于该角色 / 禁止操作超管等）{@code false}；
     * 鉴权异常（401/403）仍向上抛出，不参与成败判定。</p>
     *
     * @param userId 目标用户ID
     * @param roleId 待解除绑定的角色ID
     * @return 是否成功
     * @since 1.5.7
     */
    public static boolean deleteUserByUserId(Long userId, Long roleId) {
        requireLogin();
        CloudDeleteUserBo bo = new CloudDeleteUserBo();
        bo.setUserId(userId);
        bo.setRoleId(roleId);
        return invokeDeleteUser(bo);
    }

    /**
     * 删除用户（<b>登录态</b>）：走总线调认证中心 {@code deleteUser}，按目标账号（userName）操作。
     * 其余语义同 {@link #deleteUserByUserId(Long, Long)}（含角色解绑/删用户判定、租户隔离、超管护栏、审计）。
     *
     * @param userName 目标用户账号
     * @param roleId   待解除绑定的角色ID
     * @return 是否成功
     * @since 1.5.7
     */
    public static boolean deleteUserByUsername(String userName, Long roleId) {
        requireLogin();
        CloudDeleteUserBo bo = new CloudDeleteUserBo();
        bo.setUserName(userName);
        bo.setRoleId(roleId);
        return invokeDeleteUser(bo);
    }

    /**
     * 统一发起 {@code deleteUser} 总线调用，并把结果收敛为 {@code boolean}。
     * 业务失败（{@link BackResultException}）返回 {@code false}；鉴权异常（401/403）仍向上抛出。
     */
    private static boolean invokeDeleteUser(CloudDeleteUserBo bo) {
        try {
            callApi("deleteUser", wrapBodyLogin(bo));
            return true;
        } catch (BackResultException e) {
            return false;
        }
    }

    /**
     * 刷新令牌 / 续期（<b>登录态</b>）：走总线请认证中心校验当前 {@code loginId} 会话有效后重新签发一次性 ticket，
     * 本方法再用 {@link #doLoginByTicket(String)} 桥接登录，实现本地 token 时长刷新。
     *
     * <p>返回新的 {@link CloudLoginVo}（含刷新后的本地 accessToken）。若认证中心未提供 refreshToken 机制，
     * 本方法是“以当前会话重新登录一次”的等效续期；具体语义由服务端 {@code refreshToken} handler 决定。</p>
     *
     * @since 1.3.3
     */
    public static CloudLoginVo refreshToken() {
        requireLogin();
        Object ticket = callApi("refreshToken", wrapBodyLogin(null));
        if (ticket == null || CharSequenceUtil.isBlank(String.valueOf(ticket))) {
            throw new BackResultException("认证中心续期未返回有效 ticket");
        }
        return doLoginByTicket(String.valueOf(ticket));
    }

    /**
     * 登录态校验：未登录时抛出与其他需登录接口一致的 {@link NotLoginException}。
     */
    private static void requireLogin() {
        if (!StpUtil.isLogin()) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未能读取到有效token或者token已过期", null).setCode(11011);
        }
    }

    /**
     * 把复杂业务体（实体类 / Map）序列化为单个 {@code body} 参数（{@code SaSsoMessage} 走 form 表单，值为扁平 String）。
     * <p>入参为 {@code null} 时以空对象 {@code "{}"} 提交（等价于无 body）。</p>
     *
     * <p><b>统一兜底</b>：{@code clientId} 前端无需传，为空时用 SDK 自身配置的 sa-token client 自动填充；
     * {@code tenantId} 不填时默认主租户 {@link TenantConstants#DEFAULT_TENANT_ID}（{@code 000000}）。
     * 两者均仅在“未显式赋值”时才兜底，业务方若主动传值则以业务方为准。</p>
     */
    private static Map<String, Object> wrapBody(Object body) {
        Map<String, Object> params = new HashMap<>(1);
        params.put("body", JsonUtils.toJsonString(buildBodyFields(body)));
        return params;
    }

    /**
     * 注册总线参数：在 {@link #wrapBody(Object)} 基础上注入<b>注册自动绑定角色标识</b>。
     *
     * <p>背景：用户自助注册后无角色/菜单权限，PC 端看不到菜单，只能等管理员手动配置。
     * 客户端配置 {@code sa-token.register-role-keys}（{@code sys_role.role_key}，可多个）后，
     * 本方法把角色标识随注册 body 上报认证中心，服务端在新用户<b>尚未绑定任何角色</b>时
     * 按标识自动绑定。</p>
     *
     * <p>优先级：业务方在 {@link CloudRegisterBo#getRoleKeys()} 显式传值 &gt; 客户端配置；
     * 两者均无值则不注入 {@code roleKeys} 字段，服务端行为与旧版完全一致（向后兼容）。</p>
     *
     * @since 1.4.0
     */
    private static Map<String, Object> wrapRegisterBody(Object body) {
        Dict fields = buildBodyFields(body);
        // 仅当业务方未显式传 roleKeys 时用客户端配置兜底（convertValue 后集合字段仍为 List）
        Object explicit = fields.get("roleKeys");
        boolean notSet = !(explicit instanceof Collection) || ((Collection<?>) explicit).isEmpty();
        if (notSet) {
            List<String> roleKeys = configuredRegisterRoleKeys();
            if (CollUtil.isNotEmpty(roleKeys)) {
                fields.set("roleKeys", roleKeys);
            }
        }
        Map<String, Object> params = new HashMap<>(1);
        params.put("body", JsonUtils.toJsonString(fields));
        return params;
    }

    /**
     * 读取客户端配置的注册自动绑定角色标识（{@code sa-token.register-role-keys}）。
     * <p>CloudProperties Bean 不可用（非 Spring 环境等）时返回 {@code null}，静默降级不注入。</p>
     */
    private static List<String> configuredRegisterRoleKeys() {
        try {
            CloudProperties properties = SpringContextUtil.getBean(CloudProperties.class);
            return properties == null ? null : properties.getRegisterRoleKeys();
        } catch (Exception e) {
            log.debug("[register] 读取 CloudProperties 失败，跳过自动角色注入: {}", e.getMessage());
            return null;
        }
    }

    // ================== 自定义业务参数（sa-token.custom-properties.*） ==================

    /**
     * 读取业务方自定义参数（{@code sa-token.custom-properties.<key>}），字符串原值。
     *
     * <p>配置示例（yaml，kebab-case 为推荐写法，与既有 {@code permit-paths} 风格一致）：</p>
     * <pre>{@code
     * sa-token:
     *   custom-properties:
     *     xxxx: "123"
     *     xxx1: qweer
     * }</pre>
     *
     * <p>未配置 / CloudProperties Bean 不可用时返回 {@code null}，不抛异常。</p>
     *
     * @param key 参数键名
     * @since 1.5.3
     */
    public static String getCustomProperty(String key) {
        return getCustomProperty(key, null);
    }

    /**
     * 读取业务方自定义参数，未配置时返回给定的默认值。
     *
     * @param key          参数键名
     * @param defaultValue 默认值（key 为空或未配置时返回）
     * @since 1.5.3
     */
    public static String getCustomProperty(String key, String defaultValue) {
        if (CharSequenceUtil.isBlank(key)) {
            return defaultValue;
        }
        String value = customProperties().get(key);
        return CharSequenceUtil.isBlank(value) ? defaultValue : value;
    }

    /**
     * 读取业务方自定义参数并转为 {@link Integer}，非数字或不存在时返回默认值。
     *
     * @since 1.5.3
     */
    public static Integer getCustomPropertyInt(String key, Integer defaultValue) {
        return Convert.toInt(getCustomProperty(key), defaultValue);
    }

    /**
     * 读取业务方自定义参数并转为 {@link Boolean}（{@code true/1/on/yes} 等非标准写法外的
     * 非法值与不存在均返回默认值）。
     *
     * @since 1.5.3
     */
    public static Boolean getCustomPropertyBoolean(String key, Boolean defaultValue) {
        return Convert.toBool(getCustomProperty(key), defaultValue);
    }

    /**
     * 读取全部业务方自定义参数（不可变快照）；未配置时返回空 Map。
     * <p>适用于一次性取回整组参数（如前端透传的业务配置）。</p>
     *
     * @since 1.5.3
     */
    public static Map<String, String> getCustomProperties() {
        return Collections.unmodifiableMap(customProperties());
    }

    /**
     * 取当前绑定的自定义参数 Map：优先 {@code CloudProperties}（支持 Nacos 热更新），
     * Bean 不可用时回退 {@code Environment} 按前缀扫描（非 Spring 环境静默降级为空）。
     * <p>每次实时读取、不做本地缓存，与 permit-paths 的热更新口径一致。</p>
     */
    private static Map<String, String> customProperties() {
        try {
            CloudProperties properties = SpringContextUtil.getBean(CloudProperties.class);
            if (properties != null && properties.getCustomProperties() != null) {
                return properties.getCustomProperties();
            }
        } catch (Exception e) {
            log.debug("[custom] 读取 CloudProperties 失败，回退 Environment: {}", e.getMessage());
        }
        try {
            Environment environment = SpringContextUtil.getBean(Environment.class);
            if (environment == null) {
                return Collections.emptyMap();
            }
            Map<String, String> result = new LinkedHashMap<>();
            String prefix = "sa-token.custom-properties.";
            for (String key : iterableCustomKeys(environment)) {
                result.put(key, environment.getProperty(prefix + key));
            }
            return result;
        } catch (Exception e) {
            log.debug("[custom] Environment 不可用，自定义参数按空处理: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 从 Environment 枚举 {@code sa-token.custom-properties.*} 下的键名
     * （仅 EnumerablePropertySource 可枚举；取不到时返回空数组，由调用方降级处理）。
     */
    private static List<String> iterableCustomKeys(Environment environment) {
        List<String> keys = new ArrayList<>();
        if (!(environment instanceof org.springframework.core.env.ConfigurableEnvironment)) {
            return keys;
        }
        String prefix = "sa-token.custom-properties.";
        for (Object source : ((org.springframework.core.env.ConfigurableEnvironment) environment)
                .getPropertySources()) {
            if (source instanceof org.springframework.core.env.EnumerablePropertySource) {
                for (String name : ((org.springframework.core.env.EnumerablePropertySource<?>) source)
                        .getPropertyNames()) {
                    if (name.startsWith(prefix)) {
                        String key = name.substring(prefix.length());
                        if (!key.isEmpty() && !keys.contains(key)) {
                            keys.add(key);
                        }
                    }
                }
            }
        }
        return keys;
    }

    /**
     * 把业务体转为 {@link Dict} 并做 {@code clientId}/{@code tenantId} 统一兜底
     * （{@link #wrapBody} 与 {@link #wrapRegisterBody} 共用）。
     */
    private static Dict buildBodyFields(Object body) {
        Dict fields = body == null ? Dict.create() : JsonUtils.convertValue(body, Dict.class);
        if (fields == null) {
            fields = Dict.create();
        }
        // clientId：前端不传，SDK 用自身配置的 sa-token client 自动填充
        if (CharSequenceUtil.isBlank(fields.getStr("clientId"))) {
            fields.set("clientId", SaSsoManager.getClientConfig().getClient());
        }
        // tenantId：不填默认主租户 000000
        if (CharSequenceUtil.isBlank(fields.getStr("tenantId"))) {
            fields.set("tenantId", TenantConstants.DEFAULT_TENANT_ID);
        }
        return fields;
    }

    /**
     * 登录态总线参数：在 {@link #wrapBody(Object)} 基础上额外注入当前用户 {@code loginId}。
     *
     * <p>pushS 总线只校验 {@code client + sign}，<b>不携带终端用户的本地 token</b>，
     * 因此“修改密码 / 续期”等登录态接口必须把当前 {@code loginId} 随消息一起上报，
     * 服务端 handler 才能定位到操作用户。{@code loginId} 由 SDK 从当前会话取值，
     * 前端无法伪造（实体类中无 {@code loginId} 字段）。</p>
     */
    private static Map<String, Object> wrapBodyLogin(Object body) {
        Map<String, Object> params = wrapBody(body);
        params.put("loginId", StpUtil.getLoginId());
        return params;
    }

    /**
     * 构建 token extra 扩展数据（复用 CloudLoginUser，不再远程调用）
     */
    private static Map<String, Object> buildLoginExtra(CloudLoginUser loginUser) {
        if (loginUser == null) {
            return Collections.emptyMap();
        }
        SaLoginParameter model = new SaLoginParameter();
        model.setExtra(CloudPlatformHelper.CLIENT_KEY, loginUser.getClientKey())
                .setExtra(TENANT_KEY, loginUser.getTenantId())
                .setExtra(USER_KEY, loginUser.getUserId())
                .setExtra(USER_NAME_KEY, loginUser.getUsername())
                .setExtra(NICK_NAME_KEY, loginUser.getNickname())
                .setExtra(DEPT_KEY, loginUser.getDeptId())
                .setExtra(DEPT_NAME_KEY, loginUser.getDeptName())
                .setExtra(DEPT_CATEGORY_KEY, loginUser.getDeptCategory());
        return model.getExtraData();
    }

    // ================== ticket 场景下的用户获取 ==================

    /**
     * 根据 ticket 获取登录用户（会消费 ticket 并完成本地登录）
     */
    public static CloudLoginUser getLoginUserByTicket(String ticket) {
        SaCheckTicketResult ctr = SaSsoClientProcessor.instance.checkTicket(ticket);
        doLogin(ctr);
        return getLoginUser();
    }

    /**
     * 根据 ticket 获取用户信息（不消费 ticket，不修改本地登录态）
     */
    public static CloudUserInfoVo getUserInfoByTicketWithoutLogin(String ticket) {
        SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
        SaSsoMessage message = new SaSsoMessage();
        message.setType("userInfoByTicket");
        message.set("ticket", ticket);
        message.set("client", clientConfig.getClient());
        Object data = getRemoteSaResult(message);
        if (data instanceof CloudUserInfoVo) {
            return (CloudUserInfoVo) data;
        }
        return JsonUtils.convertValue(data, CloudUserInfoVo.class);
    }

    /**
     * 根据 ticket 获取用户信息（保持旧行为：会消费 ticket 并完成本地登录）
     */
    public static CloudUserInfoVo getUserInfoByTicket(String ticket) {
        SaCheckTicketResult ctr = SaSsoClientProcessor.instance.checkTicket(ticket);
        doLogin(ctr);
        Object loginId = ctr.loginId;
        SaSsoMessage message = new SaSsoMessage();
        message.setType("userInfo");
        message.set("loginId", loginId);
        SaSsoClientConfig clientConfig = SaSsoManager.getClientConfig();
        message.set("client", clientConfig.getClient());
        Object data = getRemoteSaResult(message);
        if (data instanceof CloudUserInfoVo) {
            return (CloudUserInfoVo) data;
        }
        return JsonUtils.convertValue(data, CloudUserInfoVo.class);
    }

    // ================== SSO URL 与注销 ==================

    /** 授权模式参数名：center=直接进入授权中心，silent=免确认直接授权回跳客户端；不传=原弹窗逻辑 */
    public static final String AUTH_MODE_PARAM = "auth_mode";
    /** 直接进入授权中心（未登录停留登录页） */
    public static final String AUTH_MODE_CENTER = "center";
    /** 免确认直接授权回跳客户端 */
    public static final String AUTH_MODE_SILENT = "silent";

    /**
     * 获取 SSO 认证中心登录地址
     */
    public static String getSsoAuthUrl(String clientLoginUrl) {
        return getSsoAuthUrl(clientLoginUrl, "");
    }

    /**
     * 获取 SSO 认证中心登录地址（带 back 参数）
     *
     * <p>配置项 {@code sa-token.sso-encrypt-param=true} 时，自动把 URL 上的
     * client / redirect 等明文参数收进一个 {@code auth_request} 密文参数（业务方无需改代码），
     * 认证中心前端会解密还原；默认 false，行为与旧版本完全一致。</p>
     *
     * @param clientLoginUrl 客户端登录地址，授权中心登录后回跳该地址（票据投递地址）
     * @param back           客户端登录成功后再次回跳到哪（用户原本想去的业务页面），可空
     */
    public static String getSsoAuthUrl(String clientLoginUrl, String back) {
        return getSsoAuthUrl(clientLoginUrl, back, null);
    }

    /**
     * 获取 SSO 认证中心登录地址（可指定授权模式）。
     *
     * @param clientLoginUrl 客户端登录地址（票据投递地址）
     * @param back           登录成功后回跳的业务页面，可空
     * @param authMode       授权模式：{@code center} 直接进入授权中心、
     *                       {@code silent} 免确认直接授权回跳客户端；为空或非法值时沿用原弹窗逻辑
     */
    public static String getSsoAuthUrl(String clientLoginUrl, String back, String authMode) {
        String url = appendAuthMode(SaSsoClientUtil.buildServerAuthUrl(clientLoginUrl, back), authMode);
        if (Boolean.parseBoolean(getValue("sa-token.sso-encrypt-param", "false"))) {
            return encryptSsoAuthUrl(url);
        }
        return url;
    }

    /**
     * 显式获取"参数已加密"的 SSO 登录地址（不依赖全局开关）。
     *
     * @param clientLoginUrl 客户端登录地址（票据投递地址）
     * @param back           登录成功后回跳的业务页面，可空
     */
    public static String getEncryptedSsoAuthUrl(String clientLoginUrl, String back) {
        return getEncryptedSsoAuthUrl(clientLoginUrl, back, null);
    }

    /** 显式获取"参数已加密"的 SSO 登录地址（可指定授权模式） */
    public static String getEncryptedSsoAuthUrl(String clientLoginUrl, String back, String authMode) {
        return encryptSsoAuthUrl(appendAuthMode(SaSsoClientUtil.buildServerAuthUrl(clientLoginUrl, back), authMode));
    }

    /** 显式获取"参数已加密"的 SSO 登录地址（无 back 重载） */
    public static String getEncryptedSsoAuthUrl(String clientLoginUrl) {
        return getEncryptedSsoAuthUrl(clientLoginUrl, "");
    }

    /**
     * 把授权模式参数追加到跳转 URL（必须在加密前调用，会随其它参数一起收进 auth_request 密文）。
     * 仅接受约定取值，避免拼接任意 query 参数；为空或非法值时原样返回，保证旧行为不变。
     */
    private static String appendAuthMode(String url, String authMode) {
        if (url == null || authMode == null || authMode.trim().isEmpty()) {
            return url;
        }
        String mode = authMode.trim();
        if (!AUTH_MODE_CENTER.equalsIgnoreCase(mode) && !AUTH_MODE_SILENT.equalsIgnoreCase(mode)) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + AUTH_MODE_PARAM + "=" + mode;
    }

    /**
     * 把跳转 URL 的明文 query 加密成 {@code ?auth_request=xxx}。
     *
     * <p>仅对 hash 登录页（auth-url 指向 {@code index.html#/ssologin}）生效；
     * auth-url 指向服务端 {@code /sso/auth} 时其参数由服务端验签，必须保持明文，
     * 此时打印告警并原样返回，避免破坏 SSO 流程。</p>
     */
    public static String encryptSsoAuthUrl(String url) {
        if (url == null || !url.contains("#")) {
            log.warn("[sso] auth-url 非前端 hash 登录页，跳过跳转参数加密: {}", url);
            return url;
        }
        return SsoParamCrypto.encryptSsoUrl(url);
    }

    /**
     * 读取鉴权中心 URL 配置信息（供前端查询并跳转使用）
     *
     * <p>仅返回前端跳转登录所需的最小字段：{@code client}、{@code serverUrl}、{@code authUrl}。</p>
     *
     * <p>典型调用链路：</p>
     * <pre>{@code
     *   // 1) 前端调用 GET /authCenter/getSsoUrl
     *   const vo = await api.get('/authCenter/getSsoUrl')
     *   // 2) 业务方自行拼装跳转 URL，例如：
     *   const clientLoginUrl = `${location.origin}/sso/doLoginByTicket`
     *   window.location.href = `${vo.authUrl}?client=${vo.client}&clientLoginUrl=${encodeURIComponent(clientLoginUrl)}&back=${encodeURIComponent(location.pathname + location.search)}`
     * }</pre>
     *
     * @return 鉴权中心 URL 配置（任一字段未配置时该字段为 null）
     */
    public static CloudSsoUrlVo getSsoUrlInfo() {
        SaSsoClientConfig cfg = SaSsoClientUtil.getSsoTemplate().getClientConfig();
        CloudSsoUrlVo vo = new CloudSsoUrlVo();
        vo.setClient(cfg.getClient());
        vo.setServerUrl(CharSequenceUtil.emptyToDefault(cfg.getServerUrl(), null));
        vo.setAuthUrl(CharSequenceUtil.emptyToDefault(cfg.splicingAuthUrl(), null));
        return vo;
    }

    /**
     * 通过 clientId 从服务端拉取一个默认登录地址。
     * <p>供
     * {@link io.gitee.chenshaogui.cloudauth.securitycloud.oauth.controller.CloudTokenSsoClientController#getSsoAuthUrl}
     * 在调用方未传 {@code clientLoginUrl} 时使用：走 sa-token SSO push message 通道
     * （消息类型 {@code getDefaultLoginUrl}），服务端 {@code SsoServerController} 注册同名
     * handler，按 clientId 查绑定菜单，取 {@code menu.path} 作为默认登录地址返回。</p>
     *
     * @return 默认登录地址，菜单未配置时返回空串
     */
    public static String getDefaultLoginUrl() {
        SaSsoMessage message = new SaSsoMessage();
        message.setType("getDefaultLoginUrl");
        Object data = getRemoteSaResult(message);
        return data == null ? "" : data.toString();
    }

    /**
     * 当前应用独自注销（不退出其它应用，仅清本地 token）。
     *
     * <p><b>为何保持 {@code Object}</b>：末尾透传 sa-token {@code SaSsoClientProcessor._ssoLogoutBack()} 的
     * 回跳响应，该方法签名即为 {@code java.lang.Object}，不收窄。</p>
     */
    public static Object logoutByAlone() {
        CloudLoginUser snapshot = currentUserSnapshot();
        StpUtil.logout();
        fireLogout(snapshot);
        return SaSsoClientProcessor.instance._ssoLogoutBack(SaHolder.getRequest(), SaHolder.getResponse());
    }

    /**
     * 当前应用退出登录（仅本地注销，不触发 SSO 全局注销；如需 SSO 全局注销请使用 ssoLogout）
     */
    public static void logout() {
        CloudLoginUser snapshot = currentUserSnapshot();
        StpUtil.logout();
        fireLogout(snapshot);
    }

    /**
     * 处理单点登录请求（直接透传 sa-token {@code SaSsoClientProcessor.ssoLogin()}）。
     *
     * <p><b>为何保持 {@code Object}</b>：sa-token 该方法按 mode（认证中心跳转 / 回调 / ticket）
     * 分别返回 {@code SaResult} 或重定向响应，方法签名即为 {@code java.lang.Object}，SDK 原样透传，
     * 收窄为具体类会破坏重定向语义。</p>
     */
    public static Object ssoLogin() {
        return SaSsoClientProcessor.instance.ssoLogin();
    }

    /**
     * 单点全局注销（直接透传 sa-token {@code SaSsoClientProcessor.ssoLogout()}，并按浏览器优先收敛）。
     *
     * <p><b>为何保持 {@code Object}</b>：同 {@link #ssoLogin()}，返回值为 sa-token 处理器的原始
     * {@code Object}（{@code SaResult} 或回跳响应），不收窄。</p>
     */
    public static Object ssoLogout() {
        CloudLoginUser snapshot = currentUserSnapshot();

        // 优先“按浏览器登出”：只通知授权中心杀掉本浏览器(loginId+browserId)在各子系统签发的 token，
        // 其它子系统靠 checkPermission 轮询感知 401 后各自本地登出 —— 实现“只退同一浏览器”，
        // 不影响该账号在其它浏览器/设备的会话。
        if (tryRemoteLogoutByBrowser(snapshot)) {
            return SaSsoClientProcessor.instance._ssoLogoutBack(SaHolder.getRequest(), SaHolder.getResponse());
        }

        // 回退：sa-token 原生单点登出（按 loginId 通知授权中心并向各子系统广播 SLO）。
        // 触发条件：本地无 browserId extra（老 token）/ 授权中心未部署 remoteLogout handler / 调用失败。
        // 保证“退出一定能传播出去”，绝不出现“本地退了、其它不退”的断裂（宁可退多，不可不退）。
        Object result = SaSsoClientProcessor.instance.ssoLogout();
        fireLogout(snapshot);
        return result;
    }

    /**
     * 尝试“按浏览器维度”登出：向授权中心发送 {@code remoteLogout(loginId, browserId)}，成功后本地登出。
     *
     * @param snapshot 登出前的用户快照，用于触发登出监听器
     * @return {@code true}=授权中心已确认按浏览器登出且本地已登出；{@code false}=不具备条件
     *         （未登录 / 无 browserId / 授权中心未实现该 handler / 调用失败），调用方应回退到原生登出
     */
    private static boolean tryRemoteLogoutByBrowser(CloudLoginUser snapshot) {
        if (!StpUtil.isLogin()) {
            return false;
        }
        String browserId = currentBrowserIdExtra();
        if (browserId == null) {
            // 本地 token 没有 browserId extra（老 token），无法按浏览器收敛，交给原生登出
            return false;
        }
        SaResult r;
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("loginId", StpUtil.getLoginId());
            params.put("browserId", browserId);
            r = REMOTE_SSO_CLIENT_TEMPLATE.invoke("remoteLogout", params);
        } catch (Exception e) {
            log.warn("[ssoLogout] 通知授权中心按浏览器登出失败，回退原生登出: {}", e.getMessage());
            return false;
        }
        if (r == null || r.getCode() != HttpStatus.HTTP_OK) {
            log.info("[ssoLogout] 授权中心未按浏览器登出(code={})，回退原生登出", r == null ? null : r.getCode());
            return false;
        }
        // 授权中心已按浏览器收敛登出，本地即时登出（修复“退出后界面仍登录”）
        StpUtil.logout();
        fireLogout(snapshot);
        return true;
    }

    /**
     * 单点注销回调（认证中心回调客户端，直接透传 sa-token {@code SaSsoClientProcessor.ssoLogoutCall()}）。
     *
     * <p><b>为何保持 {@code Object}</b>：sa-token 处理器方法签名即为 {@code java.lang.Object}，原样透传不收窄。</p>
     */
    public static Object ssoLogoutCall() {
        return SaSsoClientProcessor.instance.ssoLogoutCall();
    }

    /**
     * 接收认证中心消息推送（直接透传 sa-token {@code SaSsoClientProcessor.ssoPushC()}）。
     *
     * <p><b>为何保持 {@code Object}</b>：sa-token 处理器方法签名即为 {@code java.lang.Object}，原样透传不收窄。</p>
     */
    public static Object ssoPushC() {
        return SaSsoClientProcessor.instance.ssoPushC();
    }

    // ================== 远程调用 ==================
    
    /**
     * 统一处理 SSO 远程调用结果的状态码
     */
    private static Object getRemoteSaResult(SaSsoMessage message) {
        SaResult saResult = SaSsoClientUtil.pushMessageAsSaResult(message);
        return handleSaResult(saResult);
    }
    
    /**
     * SaResult → 数据/异常 统一映射（401/403/其他非 200 分别抛对应异常）
     *
     * <p>抽出后既复用于消息通道（{@link #getRemoteSaResult}），也复用于
     * 直连开放接口（{@link #callOpenApi}），保证两条通道的错误语义完全一致。</p>
     */
    private static Object handleSaResult(SaResult saResult) {
        if (saResult.getCode() == HttpStatus.HTTP_UNAUTHORIZED) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未能读取到有效token或者token已过期", null).setCode(11011);
        }
        if (saResult.getCode() == HttpStatus.HTTP_FORBIDDEN) {
            throw new NotPermissionException("没有访问权限，请联系管理员授权");
        }
        if (saResult.getCode() != HttpStatus.HTTP_OK) {
            throw new BackResultException(saResult.getMsg());
        }
        return saResult.getData();
    }
    
    // ================== v1.3.1 新增：通用远程调用出口 ==================
    
    /**
     * 通用：走 sa-token 消息通道调用认证中心（<b>已登录</b>场景）。
     *
     * <p>业务方新增一类接口时，只要服务端在 {@code SsoServerController} 注册了同名 handler，
     * 客户端一行搞定，无需在 SDK 里再加一个 wrapper 方法：</p>
     * <pre>{@code
     *   // 无返回体
     *   CloudPlatformHelper.callApi("sendSmsCode",
     *           Collections.singletonMap("phone", "131xxxx"));
     *
     *   // 需要拿返回值并转 DTO
     *   MyVo vo = CloudPlatformHelper.callApi("getUserExtra", params, MyVo.class);
     * }</pre>
     *
     * @param apiType 服务端注册的 SaSsoMessage type
     * @param params  业务参数（可空）
     * @return SaResult.data 原始对象（Map / List / 标量）
     */
    public static Object callApi(String apiType, Map<String, Object> params) {
        SaResult r = REMOTE_SSO_CLIENT_TEMPLATE.invoke(apiType, params);
        return handleSaResult(r);
    }
    
    /**
     * {@link #callApi(String, Map)} 的类型化版本；{@code data} 已是目标类型直接返回，
     * 否则走 Jackson {@code convertValue}（Redis JSON 反序列化后常见为 LinkedHashMap）。
     */
    public static <T> T callApi(String apiType, Map<String, Object> params, Class<T> responseType) {
        Object data = callApi(apiType, params);
        return convertSafely(data, responseType);
    }
    
    /**
     * 通用：直连认证中心开放接口（<b>不要求登录</b>），基址 = {@code sa-token.sso-client.server-url}。
     *
     * <p>典型场景：注册、重置密码、发送短信验证码、图形验证码等公开接口。
     * 自动注入 X-Browser-Id；SaResult 结构按 code 转异常，与消息通道语义一致。</p>
     *
     * <pre>{@code
     *   // 注册：POST application/json
     *   Map<String,Object> body = new HashMap<>();
     *   body.put("username", "alice");
     *   body.put("password", rawPwd);
     *   CloudPlatformHelper.callOpenApi("POST", "/api/auth/register", null, body);
     *
     *   // 发送短信：POST form
     *   CloudPlatformHelper.callOpenApi("POST", "/api/auth/smsCode",
     *           Collections.singletonMap("phone", "131xxxx"), null);
     *
     *   // 图形验证码：GET
     *   CaptchaVo vo = CloudPlatformHelper.callOpenApi(
     *           "GET", "/api/auth/captcha", null, null, CaptchaVo.class);
     * }</pre>
     *
     * @param method   GET / POST / PUT / DELETE
     * @param path     相对 server-url 的路径（以 "/" 开头），也可传完整 http(s) URL
     * @param params   query 或 form 参数（视 method 而定，可为 null）
     * @param jsonBody JSON 请求体（非 null 时以 application/json 提交，忽略 params）
     * @return SaResult.data 原始对象
     */
    public static Object callOpenApi(String method,
                                     String path,
                                     Map<String, ?> params,
                                     Object jsonBody) {
        return callOpenApi(method, path, params, jsonBody, (Map<String, String>) null);
    }
    
    /**
     * {@link #callOpenApi(String, String, Map, Object)} 的完整版本，额外支持自定义 header。
     *
     * <p><b>非 200 处理与消息通道不同</b>：开放接口的 401 语义是“认证中心接口不存在/
     * 未匿名放行”，不是“本地 token 失效”，因此不映射为 {@link NotLoginException}，
     * 而是携带认证中心原始 code + msg 抛 {@link BackResultException}，避免误导排障。</p>
     */
    public static Object callOpenApi(String method,
                                     String path,
                                     Map<String, ?> params,
                                     Object jsonBody,
                                     Map<String, String> extraHeaders) {
        SaResult r = callOpenApiAsSaResult(method, path, params, jsonBody, extraHeaders);
        if (r.getCode() != HttpStatus.HTTP_OK) {
            throw new BackResultException("认证中心[" + path + "]返回 code=" + r.getCode()
                    + ", msg=" + r.getMsg());
        }
        return r.getData();
    }

    /**
     * 开放接口透传版本：不拆包、不转异常，将认证中心原始响应的 code / msg / data
     * 直接回传调用方（适用于 Controller 透传场景，前端可看到认证中心真实错误，
     * 如“接口不存在 / 未加白名单被 401”）。
     *
     * <p>响应体不含 code 字段（非 SaResult 约定的裸 JSON）时，整体包在 data 中返回。</p>
     */
    public static SaResult callOpenApiAsSaResult(String method,
                                                 String path,
                                                 Map<String, ?> params,
                                                 Object jsonBody) {
        return callOpenApiAsSaResult(method, path, params, jsonBody, (Map<String, String>) null);
    }

    /**
     * {@link #callOpenApiAsSaResult(String, String, Map, Object)} 的完整版本，额外支持自定义 header。
     */
    public static SaResult callOpenApiAsSaResult(String method,
                                                 String path,
                                                 Map<String, ?> params,
                                                 Object jsonBody,
                                                 Map<String, String> extraHeaders) {
        String body = REMOTE_SSO_CLIENT_TEMPLATE.executeOpenApi(method, path, params, jsonBody, extraHeaders);
        if (CharSequenceUtil.isBlank(body)) {
            return SaResult.error("认证中心开放接口返回为空: " + path);
        }
        // 兼容非 SaResult 结构的裸返回：先尝试 JSON 解析为 Map，识别 code/data 语义
        Map<String, Object> map = SaManager.getSaJsonTemplate().jsonToMap(body);
        if (map == null) {
            return SaResult.error("认证中心开放接口返回非 JSON: " + path);
        }
        if (!map.containsKey("code")) {
            // 不是 SaResult 约定，原样包装返回
            return SaResult.data(map);
        }
        return new SaResult(map);
    }
    
    /**
     * {@link #callOpenApi(String, String, Map, Object)} 的类型化版本。
     */
    public static <T> T callOpenApi(String method,
                                    String path,
                                    Map<String, ?> params,
                                    Object jsonBody,
                                    Class<T> responseType) {
        Object data = callOpenApi(method, path, params, jsonBody, (Map<String, String>) null);
        return convertSafely(data, responseType);
    }
    
    /**
     * 安全类型转换：目标已匹配直接 cast；否则走 Jackson convertValue（兼容 Redis 反序列化出来的 Map / List）。
     */
    @SuppressWarnings("unchecked")
    private static <T> T convertSafely(Object data, Class<T> responseType) {
        if (data == null || responseType == null || Object.class == responseType) {
            return (T) data;
        }
        if (responseType.isInstance(data)) {
            return responseType.cast(data);
        }
        return JsonUtils.convertValue(data, responseType);
    }
    
    // ================== 配置读取 ==================

    /**
     * 是否只集成登录
     */
    public static boolean isOnlyLogin() {
        String onlyLogin = getValue("sa-token.only-login", "false");
        return BooleanUtil.toBoolean(onlyLogin);
    }

    /**
     * 通用读取配置方法
     */
    public static String getValue(String key, String defaultValue) {
        Environment env = SpringContextUtil.getEnvironment();
        if (env == null) {
            return defaultValue;
        }
        return env.getProperty(key, defaultValue);
    }

    // ================== 角色判断 ==================

    /**
     * 是否为超级管理员
     */
    public static boolean isSuperAdmin(Long userId) {
        return SystemConstants.SUPER_ADMIN_ID.equals(userId);
    }

    public static boolean isSuperAdmin() {
        return isSuperAdmin(getUserId());
    }

    /**
     * 是否为租户管理员（基于角色权限集合判断）
     */
    public static boolean isTenantAdmin(Set<String> rolePermission) {
        if (CollUtil.isEmpty(rolePermission)) {
            return false;
        }
        return rolePermission.contains(TenantConstants.TENANT_ADMIN_ROLE_KEY);
    }

    /**
     * 是否为租户管理员（从本地 Session 读取，O(1)，无远程调用）
     */
    public static boolean isTenantAdmin() {
        CloudLoginUser loginUser = getLoginUser();
        if (Objects.isNull(loginUser)) {
            return false;
        }
        return isTenantAdmin(loginUser.getRolePermission());
    }

    /**
     * 请求头名称：用于网关 / 业务侧动态覆盖"当前请求所属客户端 ID"（如超级管理员临时管理其他客户端）。
     */
    public static final String HEADER_CURRENT_CLIENT_ID = "X-Client-Id";

    /**
     * 请求参数名称：业务接口可显式传入当前 clientId 做精细化权限剪裁（按调用上下文动态指定）。
     */
    public static final String PARAM_CURRENT_CLIENT_ID = "clientId";

    /**
     * 解析"当前请求所属客户端 ID"，用于 {@link io.gitee.chenshaogui.cloudauth.securitycloud.oauth.core.CloudAuthPermissionImpl}
     * 按当前客户端剪裁 {@code rolePermission}。
     *
     * <p><b>解析优先级</b>（从高到低）：</p>
     * <ol>
     *   <li>请求头 {@value #HEADER_CURRENT_CLIENT_ID} —— 网关 / 业务侧动态覆盖（管理员切换管理目标）；</li>
     *   <li>请求参数 {@value #PARAM_CURRENT_CLIENT_ID} —— 接口级动态指定；</li>
     *   <li>{@code sa-token.sso-client.client} —— <b>SDK 必备配置</b>，标识"当前子系统所属客户端"，作为默认值；</li>
     *   <li>{@link CloudLoginUser#getClientKey()} 兜底 —— 用户登录时的主客户端。</li>
     * </ol>
     *
     * <p>说明：{@code sa-token.sso-client.client} 是 SDK 启动的必要配置（缺失则 SSO 无法工作），
     * 直接复用其值作为"当前子系统身份"，无需新增 {@code current-client-id} 配置项。</p>
     *
     * <p>所有解析失败（无请求上下文、未登录、未配置）时返回 {@code null}。
     * 调用方应将 {@code null} 视为"未启用客户端剪裁"——此时 {@code getRoleList()}
     * 仅暴露全局角色（{@code superadmin} / {@code admin}），不剥离任何前缀，
     * 与改造前行为一致，向后兼容。</p>
     *
     * <p>非请求上下文（异步线程、定时任务等）调用时，请求头/参数解析静默失败，
     * 不会抛异常，继续返回 {@code sa-token.sso-client.client} 配置或 {@code loginUser.clientKey}。</p>
     *
     * @return 当前客户端 ID，可能为 {@code null}
     */
    public static String resolveCurrentClientId() {
        // 1. 请求头 / 2. 请求参数（仅在请求线程内可读，非请求上下文静默降级）
        try {
            SaRequest saRequest = SaHolder.getRequest();
            if (saRequest != null) {
                String header = saRequest.getHeader(HEADER_CURRENT_CLIENT_ID);
                if (CharSequenceUtil.isNotBlank(header)) {
                    return header;
                }
                // 1.3.9：票上归属优先于服务配置。共享服务（infra / 其它被多端调用的服务）
                // 必须按"来路 token 的归属客户端"剪裁角色，否则外来端的 {clientId}- 前缀角色会被剥空（403）。
                // 位置在 X-Client-Id 之后：保留管理员"临时以另一客户端身份查看"的覆盖能力。
                String owner = SsoClientContext.tokenOwnerClient();
                if (CharSequenceUtil.isNotBlank(owner)) {
                    return owner;
                }
                String param = saRequest.getParam(PARAM_CURRENT_CLIENT_ID);
                if (CharSequenceUtil.isNotBlank(param)) {
                    return param;
                }
            }
        } catch (Exception ignored) {
            // 非请求上下文（异步 / 定时任务）SaHolder 不可用，静默降级
        }
        // 3. sa-token.sso-client.client 配置（SDK 必备，默认值）
        try {
            SaSsoClientConfig ssoConfig = SaSsoManager.getClientConfig();
            if (ssoConfig != null) {
                String client = ssoConfig.getClient();
                if (CharSequenceUtil.isNotBlank(client)) {
                    return client;
                }
            }
        } catch (Exception ignored) {
            // SaSsoManager 未初始化（only-login 模式）静默降级
        }
        // 4. 登录用户的 clientKey 兜底
        CloudLoginUser loginUser = getLoginUser();
        if (loginUser != null && CharSequenceUtil.isNotBlank(loginUser.getClientKey())) {
            return loginUser.getClientKey();
        }
        return null;
    }

    /**
     * 当前用户是否为"当前请求所属客户端"的子管理员（O(1)，无远程调用）。
     *
     * <p>"当前请求所属客户端"由 {@link #resolveCurrentClientId()} 解析。
     * 若无法解析（如未登录、未配置、未传参），返回 {@code false}。</p>
     *
     * <p>等价于 {@code rolePermission.contains(currentClientId + "-admin")}，
     * 与 sa-token 注解 {@code @SaCheckRole("admin")} 在客户端剪裁后的角色集合中暴露的 {@code "admin"} 互为校验冗余。</p>
     *
     * @return 是否为当前客户端的子管理员
     */
    public static boolean isClientAdmin() {
        return isClientAdmin(resolveCurrentClientId());
    }

    /**
     * 当前用户是否为指定客户端的子管理员（O(1)，无远程调用）。
     *
     * @param clientId 客户端 ID，{@code null}/空 视为无客户端
     * @return 是否为该客户端的子管理员；超级管理员（{@code superadmin}）自动返回 {@code true}
     */
    public static boolean isClientAdmin(String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return false;
        }
        CloudLoginUser loginUser = getLoginUser();
        if (loginUser == null || CollUtil.isEmpty(loginUser.getRolePermission())) {
            return false;
        }
        // 超级管理员视为任意客户端的子管理员
        if (loginUser.getRolePermission().contains(TenantConstants.SUPER_ADMIN_ROLE_KEY)) {
            return true;
        }
        return loginUser.getRolePermission().contains(clientId + "-admin");
    }

    // ================== Token Extra 扩展字段读取 ==================

    private static Object getExtra(String key) {
        try {
            return StpUtil.getSession().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    public static Long getUserId() {
        return Convert.toLong(getExtra(USER_KEY));
    }

    public static String getUsername() {
        return Convert.toStr(getExtra(USER_NAME_KEY));
    }

    public static String getTenantId() {
        return Convert.toStr(getExtra(TENANT_KEY));
    }

    public static Long getDeptId() {
        return Convert.toLong(getExtra(DEPT_KEY));
    }

    public static String getDeptName() {
        return Convert.toStr(getExtra(DEPT_NAME_KEY));
    }

    public static String getDeptCategory() {
        return Convert.toStr(getExtra(DEPT_CATEGORY_KEY));
    }

    public static String getNickname() {
        return Convert.toStr(getExtra(NICK_NAME_KEY));
    }

    // ================== 部门 / 用户信息远程查询 ==================

    public static List<CloudSysDeptVo> getDeptChildListById(Long deptId) {
        if (deptId == null) {
            return new ArrayList<>();
        }
        return remoteCacheList("getDeptChildListById", "D" + deptId, CloudSysDeptVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDeptChildListById");
            message.set("deptId", deptId);
            Object data = getRemoteSaResult(message);
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudSysDeptVo.class);
        });
    }

    public static String getDeptNameByIds(String deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return "";
        }
        return remoteCache("getDeptNameByIds", "K" + deptIds, String.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDeptNameByIds");
            message.set("deptIds", deptIds);
            Object data = getRemoteSaResult(message);
            return Convert.toStr(data);
        });
    }

    public static String getDeptNameById(Long deptId) {
        if (deptId == null) {
            return "";
        }
        return remoteCache("getDeptNameById", "D" + deptId, String.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDeptNameByIds");
            message.set("deptIds", String.valueOf(deptId));
            Object data = getRemoteSaResult(message);
            return Convert.toStr(data);
        });
    }

    /**
     * 通过部门ID查询部门编码（单个）
     *
     * <p>与 {@link #getDeptNameById(Long)} 对称，由服务端 sys_dept.dept_code 字段提供；
     * 业务可在部门管理界面维护编码，客户端按部门 ID 拉取。</p>
     *
     * @param deptId 部门ID
     * @return 部门编码；deptId 为空或服务端未配置时返回空串
     */
    public static String getDeptCodeById(Long deptId) {
        if (deptId == null) {
            return "";
        }
        return remoteCache("getDeptCodeById", "D" + deptId, String.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDeptCodeByIds");
            message.set("deptIds", String.valueOf(deptId));
            Object data = getRemoteSaResult(message);
            return Convert.toStr(data);
        });
    }

    /**
     * 通过部门ID查询部门编码（多个，逗号分隔返回，与入参顺序一一对应）
     *
     * @param deptIds 部门ID串，逗号分隔（如 "1,2,3"）
     * @return 部门编码串，逗号分隔；入参为空或单个 ID 不存在时该位为空串
     */
    public static String getDeptCodeByIds(String deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return "";
        }
        return remoteCache("getDeptCodeByIds", "K" + deptIds, String.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDeptCodeByIds");
            message.set("deptIds", deptIds);
            Object data = getRemoteSaResult(message);
            return Convert.toStr(data);
        });
    }

    /**
     * 直接获取当前登录人的部门编码（{@code dept_code}）
     *
     * <p>便捷方法：等价于 {@code getDeptCodeById(getDeptId())}，复用 300s TTL 远程缓存
     * （与 {@link #getDeptCodeById(Long)} 共享 Redis key，登录后多次调用零远程）。</p>
     *
     * <p>与 {@link #getDeptName()} 等 session 缓存字段的差异：本方法走远程缓存，
     * 反映服务端最新值（如部门编码被业务方调整后，最多 300s 内可见）；
     * 而 {@link #getDeptName()} 在登录时一次性写入 SaSession，需重新登录才会刷新。</p>
     *
     * @return 当前登录人部门的 {@code dept_code}；未登录（{@link StpUtil#isLogin()} 为 false）、
     *         当前用户无部门（{@link #getDeptId()} 为 null）或部门未配置编码时返回空串
     */
    public static String getCurrentDeptCode() {
        return getDeptCodeById(getDeptId());
    }

    public static CloudUserVo getUserInfoById(Long userId) {
        if (userId == null) {
            return null;
        }
        return remoteCache("getUserInfoById", "U" + userId, CloudUserVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getUserInfoById");
            message.set("userId", userId);
            Object data = getRemoteSaResult(message);
            if (data instanceof CloudUserVo) {
                return (CloudUserVo) data;
            }
            return JsonUtils.convertValue(data, CloudUserVo.class);
        });
    }

    public static List<CloudUserVo> getUserListByIds(String userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        return remoteCacheList("getUserListByIds", "K" + userIds, CloudUserVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getUserListByIds");
            message.set("userIds", userIds);
            Object data = getRemoteSaResult(message);
            if (data instanceof List) {
                return JsonUtils.convertValue(data, JsonUtilsHolder.LIST_OF_USER_VO);
            }
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudUserVo.class);
        });
    }

    // ================== 用户角色远程查询 ==================

    /**
     * 根据用户 ID 获取该用户的角色列表（走总线调认证中心 {@code getUserRoles}，结果按 TTL 缓存）。
     *
     * <p>与 {@link #getUserInfoById(Long)} 对称：后者查用户基本信息，本方法查绑定角色。
     * 返回 {@link CloudRoleDTO}（roleId / roleName / roleKey / dataScope）。</p>
     *
     * <p>服务端需注册 {@code getUserRoles} handler，按 userId 查 sys_user_role JOIN sys_role（status=0）
     * 并返回角色列表，详见改造文档。用户无角色时返回空列表。</p>
     *
     * @param userId 用户 ID
     * @return 角色列表；userId 为空时返回空列表
     * @since 1.5.5
     */
    public static List<CloudRoleDTO> getRolesByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return remoteCacheList("getUserRoles", "RU" + userId, CloudRoleDTO.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getUserRoles");
            message.set("userId", userId);
            Object data = getRemoteSaResult(message);
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudRoleDTO.class);
        });
    }

    /**
     * 根据用户登录账号（username）获取该用户的角色列表（走总线调认证中心 {@code getUserRoles}，结果按 TTL 缓存）。
     *
     * <p>服务端 handler 按 userName 定位用户后查角色，与 {@link #getRolesByUserId(Long)} 同一 type、
     * 参数区别仅为 {@code userId} vs {@code username}。账号不存在时服务端返回空列表（不报错）。</p>
     *
     * @param username 用户登录账号（sys_user.user_name）
     * @return 角色列表；username 为空时返回空列表
     * @since 1.5.5
     */
    public static List<CloudRoleDTO> getRolesByUsername(String username) {
        if (CharSequenceUtil.isBlank(username)) {
            return new ArrayList<>();
        }
        return remoteCacheList("getUserRoles", "RN" + username, CloudRoleDTO.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getUserRoles");
            message.set("username", username);
            Object data = getRemoteSaResult(message);
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudRoleDTO.class);
        });
    }

    /**
     * 根据角色权限标识（roleKey）查询单个角色信息（走总线调认证中心 {@code getRoleByRoleKey}，结果按 TTL 缓存）。
     *
     * <p>返回 {@link CloudRoleDTO}（roleId / roleName / roleKey / dataScope）。roleKey 在租户内唯一，
     * 格式通常为 {@code {clientId}-{权限标识}}（如 {@code 894bd7683c885153e24844750d59134d-admin}），
     * 也支持短标识 {@code admin}（服务端自动补全 clientId 精确匹配）。</p>
     *
     * <p>角色不存在时返回 {@code null}。</p>
     *
     * @param roleKey 角色权限标识
     * @return 角色信息；roleKey 为空时返回 {@code null}
     * @since 1.5.6
     */
    public static CloudRoleDTO getRoleByRoleKey(String roleKey) {
        if (CharSequenceUtil.isBlank(roleKey)) {
            return null;
        }
        return remoteCache("getRoleByRoleKey", "RK" + roleKey, CloudRoleDTO.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getRoleByRoleKey");
            message.set("roleKey", roleKey);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudRoleDTO.class);
        });
    }

    /**
     * 分页查询角色下绑定的用户列表（走总线调认证中心 {@code getRoleUserPage}，结果按 TTL 缓存）。
     *
     * <p>按 {@code roleId} 或 {@code roleKey}（二选一，roleId 优先）定位角色，
     * 分页返回其关联的启用状态用户（status=0）。返回 {@link CloudUserPageVo}（含 {@code rows} / {@code total}）。</p>
     *
     * <p>roleKey 支持短标识或完整 {@code {clientId}-{标识}}，服务端自动补全匹配。
     * 角色不存在或无关联用户时返回空分页（rows=[], total=0）。</p>
     *
     * @param bo 查询条件（roleId/roleKey 二选一 + pageNum/pageSize）
     * @return 分页结果；bo 为 null 或两个定位字段均为空时返回空分页
     * @since 1.5.6
     */
    public static CloudUserPageVo getRoleUserPage(CloudRoleUserQueryBo bo) {
        CloudRoleUserQueryBo query = bo == null ? new CloudRoleUserQueryBo() : bo;
        if (query.getRoleId() == null && CharSequenceUtil.isBlank(query.getRoleKey())) {
            CloudUserPageVo empty = new CloudUserPageVo();
            empty.setRows(new ArrayList<>());
            empty.setTotal(0L);
            return empty;
        }
        // 默认分页：pageNum=1, pageSize=10
        if (query.getPageNum() == null || query.getPageNum() <= 0) {
            query.setPageNum(1);
        }
        if (query.getPageSize() == null || query.getPageSize() <= 0) {
            query.setPageSize(10);
        }
        String key = "K" + query.toCacheKeySuffix();
        CloudUserPageVo page = remoteCache("getRoleUserPage", key, CloudUserPageVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getRoleUserPage");
            message.set("roleId", query.getRoleId());
            message.set("roleKey", query.getRoleKey());
            message.set("pageNum", query.getPageNum());
            message.set("pageSize", query.getPageSize());
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudUserPageVo.class);
        });
        if (page == null) {
            page = new CloudUserPageVo();
            page.setRows(new ArrayList<>());
            page.setTotal(0L);
        }
        if (page.getRows() == null) {
            page.setRows(new ArrayList<>());
        }
        return page;
    }

    // ================== 字典信息远程查询 ==================

    /**
     * 查询全部字典类型（远程拉取认证中心字典类型集合，结果按 TTL 缓存）
     * <p>适用于前端一次性拉取全部字典定义后自行缓存的场景。</p>
     */
    public static List<CloudDictTypeVo> getDictTypeAll() {
        return remoteCacheList("getDictTypeAll", "ALL", CloudDictTypeVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDictTypeAll");
            Object data = getRemoteSaResult(message);
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudDictTypeVo.class);
        });
    }

    /**
     * 根据字典类型ID查询字典类型（远程拉取，结果按 TTL 缓存）
     *
     * @param dictId 字典类型ID
     */
    public static CloudDictTypeVo getDictTypeById(Long dictId) {
        if (dictId == null) {
            return null;
        }
        return remoteCache("getDictTypeById", "I" + dictId, CloudDictTypeVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDictTypeById");
            message.set("dictId", dictId);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudDictTypeVo.class);
        });
    }

    /**
     * 根据字典类型编码查询字典类型（远程拉取，结果按 TTL 缓存）
     *
     * @param dictType 字典类型编码，如 sys_yes_no
     */
    public static CloudDictTypeVo getDictTypeByType(String dictType) {
        if (dictType == null || dictType.isEmpty()) {
            return null;
        }
        return remoteCache("getDictTypeByType", "T" + dictType, CloudDictTypeVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDictTypeByType");
            message.set("dictType", dictType);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudDictTypeVo.class);
        });
    }

    /**
     * 根据字典数据ID查询字典数据（远程拉取，结果按 TTL 缓存）
     *
     * @param dictCode 字典数据ID
     */
    public static CloudDictDataVo getDictDataById(Long dictCode) {
        if (dictCode == null) {
            return null;
        }
        return remoteCache("getDictDataById", "I" + dictCode, CloudDictDataVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDictDataById");
            message.set("dictCode", dictCode);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudDictDataVo.class);
        });
    }

    /**
     * 根据字典类型编码查询字典数据集合（远程拉取，结果按 TTL 缓存）
     * <p>适用于下拉框 / 单选组等按字典类型取值的场景。</p>
     *
     * @param dictType 字典类型编码，如 sys_yes_no
     */
    public static List<CloudDictDataVo> getDictDataByType(String dictType) {
        if (dictType == null || dictType.isEmpty()) {
            return new ArrayList<>();
        }
        return remoteCacheList("getDictDataByType", "T" + dictType, CloudDictDataVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getDictDataByType");
            message.set("dictType", dictType);
            Object data = getRemoteSaResult(message);
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudDictDataVo.class);
        });
    }

    // ================== 参数配置远程查询 ==================

    /**
     * 查询全部参数配置（远程拉取认证中心参数设置，结果按 TTL 缓存）
     * <p>适用于前端一次性拉取全部参数后自行缓存的场景。</p>
     */
    public static List<CloudConfigVo> getConfigAll() {
        String tenantId = getTenantId();
        return remoteCacheList("getConfigAll", configCacheKey(tenantId, "ALL"), CloudConfigVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getConfigAll");
            message.set("tenantId", tenantId);
            Object data = getRemoteSaResult(message);
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudConfigVo.class);
        });
    }

    /**
     * 根据参数ID查询参数配置（远程拉取，结果按 TTL 缓存）
     *
     * @param configId 参数配置ID
     */
    public static CloudConfigVo getConfigById(Long configId) {
        if (configId == null) {
            return null;
        }
        String tenantId = getTenantId();
        return remoteCache("getConfigById", configCacheKey(tenantId, "I" + configId), CloudConfigVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getConfigById");
            message.set("configId", configId);
            message.set("tenantId", tenantId);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudConfigVo.class);
        });
    }

    /**
     * 根据参数键名查询参数配置（远程拉取，结果按 TTL 缓存）
     *
     * @param configKey 参数键名，如 sys.user.initPassword
     */
    public static CloudConfigVo getConfigByKey(String configKey) {
        if (configKey == null || configKey.isEmpty()) {
            return null;
        }
        String tenantId = getTenantId();
        return remoteCache("getConfigByKey", configCacheKey(tenantId, "K" + configKey), CloudConfigVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getConfigByKey");
            message.set("configKey", configKey);
            message.set("tenantId", tenantId);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudConfigVo.class);
        });
    }

    /**
     * 根据参数键名查询参数键值（远程拉取，结果按 TTL 缓存）
     * <p>只读取开关 / 阈值等单值场景可直接使用本方法，无需自行解析 {@link CloudConfigVo}。</p>
     *
     * @param configKey 参数键名，如 sys.account.registerUser
     * @return 参数键值；参数不存在时返回 {@code null}
     */
    public static String getConfigValueByKey(String configKey) {
        if (configKey == null || configKey.isEmpty()) {
            return null;
        }
        String tenantId = getTenantId();
        return remoteCache("getConfigValueByKey", configCacheKey(tenantId, "K" + configKey), String.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getConfigValueByKey");
            message.set("configKey", configKey);
            message.set("tenantId", tenantId);
            Object data = getRemoteSaResult(message);
            return Convert.toStr(data);
        });
    }

    /**
     * 构建参数配置的缓存 key（{@code sys_config} 按租户隔离，避免跨租户缓存串号）
     *
     * @param tenantId 租户id，可为 null
     * @param suffix   业务后缀（如 {@code K + configKey}）
     */
    private static String configCacheKey(String tenantId, String suffix) {
        return "T" + (tenantId == null ? "-" : tenantId) + ":" + suffix;
    }

    // ================== 区域编码（sys_area）远程查询 ==================
    // 1.3.0 新增：参考数据档，TTL 默认 300s（YAML 可配 sa-token.cache.reference-ttl-seconds）

    /**
     * 查询行政地区编码列表（按查询条件过滤，缓存键包含所有 BO 字段的拼接）
     *
     * <p>参考数据档（默认 300s TTL），用于下拉框 / 级联选择等低频维护场景。
     * {@code bo} 中所有字段均可空（为空时不过滤）。</p>
     *
     * @param bo 查询条件，可空；为空时返回全部启用区域
     * @return 区域列表（按 id 升序），无结果返回空列表
     */
    public static List<CloudAreaVo> getAreaList(CloudAreaBo bo) {
        String key = buildAreaCacheKey(bo);
        return remoteCacheList("getAreaList", key, CloudAreaVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getAreaList");
            if (bo != null) {
                message.set("id", bo.getId());
                message.set("areaCode", bo.getAreaCode());
                message.set("areaName", bo.getAreaName());
                message.set("areaLevel", bo.getAreaLevel());
                message.set("parentCode", bo.getParentCode());
                message.set("compartment", bo.getCompartment());
            }
            Object data = getRemoteSaResult(message);
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudAreaVo.class);
        });
    }

    /**
     * 根据区域编码查询单个区域（精确匹配）
     *
     * <p>参考数据档（默认 300s TTL）。</p>
     *
     * @param areaCode 区域编码（如 "110000" / "130100"），为空时返回 null
     * @return 区域详情；未找到时返回 null
     */
    public static CloudAreaVo getAreaByCode(String areaCode) {
        if (CharSequenceUtil.isBlank(areaCode)) {
            return null;
        }
        return remoteCache("getAreaByCode", "C" + areaCode, CloudAreaVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getAreaByCode");
            message.set("areaCode", areaCode);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudAreaVo.class);
        });
    }

    /**
     * 构造区域查询的缓存 key（所有 BO 字段都参与，避免不同条件共用同一份缓存）
     */
    private static String buildAreaCacheKey(CloudAreaBo bo) {
        if (bo == null) {
            return "ALL";
        }
        StringBuilder sb = new StringBuilder("Q");
        sb.append(bo.getId() == null ? "" : bo.getId())
                .append('|').append(bo.getAreaCode() == null ? "" : bo.getAreaCode())
                .append('|').append(bo.getAreaName() == null ? "" : bo.getAreaName())
                .append('|').append(bo.getAreaLevel() == null ? "" : bo.getAreaLevel())
                .append('|').append(bo.getParentCode() == null ? "" : bo.getParentCode())
                .append('|').append(bo.getCompartment() == null ? "" : bo.getCompartment());
        return sb.toString();
    }

    // ================== 通知公告（sys_notice）远程查询 ==================
    // 1.3.0 新增：参考数据档，TTL 默认 300s

    /**
     * 查询通知公告分页列表（按查询条件过滤，缓存键包含所有 BO 字段的拼接）
     *
     * <p>参考数据档（默认 300s TTL）。业务方通常在前端一次性加载后自行分页缓存，
     * 本方法仍按"全量条件 + 分页"作为缓存 key，避免不同条件互相串号。</p>
     *
     * <p>{@code bo.pageNum / pageSize} 默认为 1 / 10。</p>
     *
     * @param bo 查询条件，可空
     * @return 公告分页数据（rows / total）
     */
    public static CloudNoticePageVo getNoticeList(CloudNoticeBo bo) {
        CloudNoticeBo query = bo == null
                ? new CloudNoticeBo() : bo;
        // 默认分页：pageNum=1, pageSize=10
        if (query.getPageNum() == null || query.getPageNum() <= 0) {
            query.setPageNum(1);
        }
        if (query.getPageSize() == null || query.getPageSize() <= 0) {
            query.setPageSize(10);
        }
        String key = "K" + query.toCacheKeySuffix();
        CloudNoticePageVo page = remoteCache("getNoticeList", key, CloudNoticePageVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getNoticeList");
            message.set("noticeId", query.getNoticeId());
            message.set("noticeTitle", query.getNoticeTitle());
            message.set("noticeType", query.getNoticeType());
            message.set("status", query.getStatus());
            message.set("createByName", query.getCreateByName());
            message.set("pageNum", query.getPageNum());
            message.set("pageSize", query.getPageSize());
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudNoticePageVo.class);
        });
        if (page == null) {
            page = new CloudNoticePageVo();
            page.setRows(new ArrayList<>());
            page.setTotal(0L);
        }
        if (page.getRows() == null) {
            page.setRows(new ArrayList<>());
        }
        return page;
    }

    /**
     * 根据公告 ID 查询公告详情
     *
     * <p>参考数据档（默认 300s TTL）。</p>
     *
     * @param noticeId 公告 ID
     * @return 公告详情；未找到时返回 null
     */
    public static CloudNoticeVo getNoticeById(Long noticeId) {
        if (noticeId == null) {
            return null;
        }
        return remoteCache("getNoticeById", "I" + noticeId, CloudNoticeVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getNoticeById");
            message.set("noticeId", noticeId);
            Object data = getRemoteSaResult(message);
            return JsonUtils.convertValue(data, CloudNoticeVo.class);
        });
    }

    // ================== 文件存储（sys_oss）远程操作 ==================
    /**
     * 上传文件到授权中心对象存储服务（不缓存，每次调用都会落 OSS 并写 sys_oss 记录）
     *
     * <p>业务方通常通过 {@code /oss/upload} HTTP 端点（{@code CloudOssController}）调用；
     * 直接调用本方法适用于：业务后台服务、异步任务、SDK 内部场景。</p>
     *
     * <p><b>必填</b>：{@code content}、{@code originalName}；{@code contentType} 可空（服务端按后缀推断）。</p>
     *
     * @param content       文件字节内容
     * @param originalName  原始文件名（含后缀，如 {@code 合同.pdf}）
     * @param contentType   MIME 类型，可空（如 {@code application/pdf}）
     * @return 上传响应（含 url / fileName / ossId）
     */
    public static CloudOssUploadVo uploadOss(byte[] content, String originalName, String contentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("上传文件内容不能为空");
        }
        if (CharSequenceUtil.isBlank(originalName)) {
            throw new IllegalArgumentException("上传文件原始文件名不能为空");
        }
        SaSsoMessage message = new SaSsoMessage();
        message.setType("uploadOss");
        message.set("name", originalName);
        message.set("originalName", originalName);
        message.set("contentType", contentType == null ? "" : contentType);
        // 文件内容走 base64 字符串：sa-token push 通道只支持文本，所以这里转 base64，
        // 服务端解码后写入 OSS。
        message.set("base64", cn.hutool.core.codec.Base64.encode(content));
        // 用 SDK 扩展的 POST 通道，避免 base64 拼进 URL 触发 length 限制
        SaResult saResult = REMOTE_SSO_CLIENT_TEMPLATE.pushMessageAsSaResult(message, null);
        if (saResult.getCode() == HttpStatus.HTTP_UNAUTHORIZED) {
            throw NotLoginException.newInstance(StpUtil.stpLogic.loginType, "-1",
                    "未能读取到有效token或者token已过期", null).setCode(11011);
        }
        if (saResult.getCode() != HttpStatus.HTTP_OK) {
            throw new BackResultException(saResult.getMsg());
        }
        return JsonUtils.convertValue(saResult.getData(), CloudOssUploadVo.class);
    }

    /**
     * 根据 ossId 查询单个文件 URL（轻量，纯字符串，按 ossId 缓存，参考数据档 300s）
     *
     * <p>URL 来自授权中心 {@code sys_oss.url}（私有桶会自动生成 120s 临时 URL，
     * 公网桶返回原始 URL）。业务方拿到 URL 后直接使用，无需再绕回授权中心。</p>
     *
     * @param ossId 文件主键
     * @return 文件 URL；{@code ossId} 为空或服务端未找到时返回空串
     */
    public static String getOssUrlById(Long ossId) {
        if (ossId == null) {
            return "";
        }
        return remoteCache("getOssUrlById", "U" + ossId, String.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getOssUrlByIds");
            message.set("ossIds", String.valueOf(ossId));
            Object data = getRemoteSaResult(message);
            String urls = Convert.toStr(data);
            // 多值返回时取第一个（与入参单 ID 一一对应）
            if (CharSequenceUtil.isBlank(urls)) {
                return "";
            }
            int idx = urls.indexOf(',');
            return idx < 0 ? urls : urls.substring(0, idx);
        });
    }

    /**
     * 根据 ossId 串查询多个文件 URL（按入参顺序返回，逗号分隔；参考数据档 300s）
     *
     * @param ossIds 逗号分隔的 ossId 串（如 {@code "1,2,3"}），为空时返回空串
     * @return 逗号分隔的 URL 串，顺序与入参一一对应；未找到时该位为空串
     */
    public static String getOssUrlByIds(String ossIds) {
        if (CharSequenceUtil.isBlank(ossIds)) {
            return "";
        }
        return remoteCache("getOssUrlByIds", "K" + ossIds, String.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getOssUrlByIds");
            message.set("ossIds", ossIds);
            Object data = getRemoteSaResult(message);
            return Convert.toStr(data);
        });
    }

    /**
     * 根据 ossId 串查询多个文件完整信息（按入参顺序返回，参考数据档 300s）
     *
     * @param ossIds 逗号分隔的 ossId 串（如 {@code "1,2,3"}），为空时返回空列表
     * @return 文件信息列表（顺序与入参一致，未找到时该位缺失而非 null）
     */
    public static List<CloudOssVo> getOssByIds(String ossIds) {
        if (CharSequenceUtil.isBlank(ossIds)) {
            return new ArrayList<>();
        }
        return remoteCacheList("getOssByIds", "K" + ossIds, CloudOssVo.class, () -> {
            SaSsoMessage message = new SaSsoMessage();
            message.setType("getOssByIds");
            message.set("ossIds", ossIds);
            Object data = getRemoteSaResult(message);
            if (data instanceof List) {
                return JsonUtils.convertValue(data, JsonUtilsHolder.LIST_OF_OSS_VO);
            }
            return JsonUtils.parseArray(JsonUtils.toJsonString(data), CloudOssVo.class);
        });
    }

    /**
     * 向授权中心注册用户信息
     */
    public static SaResult syncUserInfo(CloudSyncUserVo userInfo) {
        if (userInfo == null) {
            return SaResult.error("用户信息不能为空");
        }
        SaSsoMessage message = new SaSsoMessage();
        message.setType("syncUserInfo");
        String jsonString = JsonUtils.toJsonString(userInfo);
        Dict dict = JsonUtils.parseMap(jsonString);
        return REMOTE_SSO_CLIENT_TEMPLATE.pushMessageAsSaResult(message, dict);
    }

    // ================== 子系统操作日志（单条入口 + 异步批量上报，落库 sys_oper_log） ==================

    /**
     * 写入一条子系统操作日志（唯一入口，自动补齐当前登录用户 / 租户 / 客户端 / IP / 时间）。
     *
     * <p><b>调用开销</b>：O(1) 内存操作（一次有界队列 offer），无网络、无阻塞、
     * 任何异常静默吞掉，可直接放在 Controller / Service / 注解切面的任意位置，
     * 不影响业务链路。</p>
     *
     * <p><b>推荐用法</b>：子系统自有框架的日志注解（如 @Log）切面在环绕通知中
     * 组装 {@link CloudOperLogVo}（method / operParam / jsonResult / costTime 等审计明细），
     * 结束时调用本方法单条提交——SDK 负责异步批量推送与防写入放大。</p>
     *
     * <p><b>防写入放大</b>：SDK 以有界队列（默认 10000）缓冲，后台单线程按周期
     * （默认 5 秒）批量推送到授权中心，复用已有 {@code RemoteLogService.saveLog}
     * 链路写入 {@code sys_oper_log} 表；队列满时丢弃新日志并限频 warn，
     * 绝不反压业务线程。详见
     * {@code io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.CloudOperLogRecorder}。</p>
     *
     * <p>未填充的身份维度字段自动补齐当前登录态（非请求上下文时保持为空，
     * 允许匿名 / 异步场景上报）。</p>
     *
     * @param vo 日志数据（见 {@link CloudOperLogVo}）
     */
    public static void writeOperLog(CloudOperLogVo vo) {
        if (vo == null) {
            return;
        }
        // 未显式指定的身份维度从当前登录态补齐（非请求上下文时为 null，允许匿名上报）
        if (vo.getUserId() == null) {
            vo.setUserId(getUserId());
        }
        if (CharSequenceUtil.isBlank(vo.getOperName())) {
            vo.setOperName(getUsername());
        }
        if (CharSequenceUtil.isBlank(vo.getTenantId())) {
            vo.setTenantId(getTenantId());
        }
        if (CharSequenceUtil.isBlank(vo.getClientId())) {
            vo.setClientId(resolveCurrentClientId());
        }
        if (CharSequenceUtil.isBlank(vo.getOperIp())) {
            vo.setOperIp(currentRequestIp());
        }
        if (vo.getOperTime() == null) {
            vo.setOperTime(new Date());
        }
        CloudOperLogRecorder.record(vo);
    }

    /**
     * 常见反向代理透传头，按优先级尝试（与 ruoyi/hutool getClientIP 一致）
     */
    private static final String[] PROXY_IP_HEADERS = {
        "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
        "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    /**
     * 当前请求来源 IP（非请求上下文 / 异步线程返回 null）
     *
     * <p>sa-token 的 {@link SaRequest} 接口不提供 getClientIP，且 SDK 需同时兼容
     * javax / jakarta 两套 servlet API，不能直接引用 HttpServletRequest。
     * 故按代理透传头链解析（与服务端 ServletUtils.getClientIP 逻辑一致），
     * 无代理头时反射调用源对象的 getRemoteAddr 兜底。</p>
     */
    private static String currentRequestIp() {
        try {
            SaRequest saRequest = SaHolder.getRequest();
            if (saRequest == null) {
                return null;
            }
            String ip = resolveIpFromProxyHeaders(saRequest);
            return ip != null ? ip : resolveRemoteAddr(saRequest);
        } catch (Exception ignored) {
            // 非请求上下文（异步 / 定时任务）SaHolder 不可用，静默降级
            return null;
        }
    }

    /**
     * 按优先级遍历代理透传头，取第一个有效 IP；全部无效时返回 null
     */
    private static String resolveIpFromProxyHeaders(SaRequest saRequest) {
        for (String header : PROXY_IP_HEADERS) {
            String ip = normalizeProxyIp(saRequest.getHeader(header));
            if (ip != null) {
                return ip;
            }
        }
        return null;
    }

    /**
     * 校验并归一化代理头值：
     * 过滤空白 / "unknown"；逗号分隔的多级代理链取第一个；归一化后仍无效则返回 null
     */
    private static String normalizeProxyIp(String ip) {
        if (CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            return null;
        }
        int idx = ip.indexOf(',');
        String candidate = idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        if (CharSequenceUtil.isBlank(candidate) || "unknown".equalsIgnoreCase(candidate)) {
            return null;
        }
        return candidate;
    }

    /**
     * 无代理头（直连场景）：从底层源对象反射取 remoteAddr（javax/jakarta 通用）
     */
    private static String resolveRemoteAddr(SaRequest saRequest) {
        Object source = saRequest.getSource();
        if (source == null) {
            return null;
        }
        try {
            Object remoteAddr = source.getClass().getMethod("getRemoteAddr").invoke(source);
            return remoteAddr == null ? null : remoteAddr.toString();
        } catch (Exception ignored) {
            // 源对象不是标准 servlet request，放弃兜底
            return null;
        }
    }

    /**
     * 操作日志上报总开关（{@code sa-token.oper-log.enabled}，默认 true）
     */
    public static boolean getOperLogEnabled() {
        return BooleanUtil.toBoolean(getValue("sa-token.oper-log.enabled", "true"));
    }

    /**
     * 操作日志数值配置读取（供 Recorder 使用）
     */
    public static int getOperLogIntValue(String key, int defaultValue) {
        try {
            return Integer.parseInt(getValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ================== 远程调用结果缓存 ==================

    /**
     * 单值缓存：按 {@code apiName + inputKey} 在共享 Redis 维度缓存远程返回结果，
     * TTL 由 {@link #getCacheTtlSeconds(String)} 按档位决定（默认档 20s、参考数据档 300s、
     * 用户信息档 60s，最小 5s）。
     *
     * <p>缓存命中时直接返回本地数据，零远程调用；
     * 缓存未命中或已过期时调用 {@code loader} 拉取远程结果并写入缓存。</p>
     *
     * <p>底层由 {@link SharedRemoteCache} 承载：所有用户共享同一份 Redis key，
     * TTL 由 Redis 自动失效，TTL 边界点并发请求通过 per-key ReentrantLock
     * 合并为 1 次远程调用（防击穿）。</p>
     */
    private static <T> T remoteCache(String apiName, String inputKey, Class<T> type, Supplier<T> loader) {
        SharedRemoteCache cache = sharedCache();
        if (cache == null || !cache.isAvailable()) {
            // 降级：未启用共享缓存（无 Redis）→ 每次都调用 loader
            return loader.get();
        }
        long ttl = getCacheTtlSeconds(apiName);
        String fullKey = cache.buildKey(apiName, inputKey);
        String json = cache.getOrLoadJson(fullKey, ttl, () -> {
            T fresh = loader.get();
            return fresh == null ? null : JsonUtils.toJsonString(fresh);
        });
        if (json == null) {
            return null;
        }
        return JsonUtils.parseObject(json, type);
    }

    /**
     * 列表版缓存（参考 {@link #remoteCache}）
     */
    private static <T> List<T> remoteCacheList(String apiName, String inputKey, Class<T> elementType,
                                                Supplier<List<T>> loader) {
        SharedRemoteCache cache = sharedCache();
        if (cache == null || !cache.isAvailable()) {
            return loader.get();
        }
        long ttl = getCacheTtlSeconds(apiName);
        String fullKey = cache.buildKey(apiName, inputKey);
        String json = cache.getOrLoadJson(fullKey, ttl, () -> {
            List<T> fresh = loader.get();
            return fresh == null ? null : JsonUtils.toJsonString(fresh);
        });
        if (json == null) {
            return null;
        }
        return JsonUtils.parseArray(json, elementType);
    }

    /**
     * 登录态类远程结果的缓存 key 后缀：{@code L{loginId}@{归属客户端}}（1.3.9）。
     *
     * <p>服务端按 {@code client + loginId} 定位中心会话并按 client 组装菜单/角色，
     * 同一 loginId 在不同授权客户端下的结果不同；而各子系统共用一份 Redis
     * （无 key-prefix），单 loginId 维度会互相串味，故必须带上归属客户端。</p>
     *
     * <p>{@link SsoClientContext#cacheOwnerLabel()} 在归属未知时回退本服务 client，
     * 与 1.3.8 的 key 相比只是多了一次后缀，不影响正确性。</p>
     */
    private static String loginIdCacheKey(Object loginId) {
        return "L" + loginId + "@" + SsoClientContext.cacheOwnerLabel();
    }

    /**
     * 清空当前用户的远程读取缓存（userInfo / routers / isRemoteLogin）。
     * <p>在 {@link #refreshLoginUser()} 检测到权限变更后调用，保证业务方随后读到的是最新数据。</p>
     * <p>实际动作是删除对应的共享 Redis key，业务方无感知。</p>
     */
    private static void invalidateCurrentUserCaches() {
        if (!StpUtil.isLogin()) {
            return;
        }
        SharedRemoteCache cache = sharedCache();
        if (cache == null || !cache.isAvailable()) {
            return;
        }
        try {
            String suffix = loginIdCacheKey(StpUtil.getLoginId());
            cache.invalidate(
                cache.buildKey("userInfo", suffix),
                cache.buildKey("routers", suffix),
                cache.buildKey("isRemoteLogin", suffix)
            );
        } catch (Exception e) {
            log.debug("[cache] 清除当前用户缓存失败：{}", e.getMessage());
        }
    }

    /**
     * 内部 holder，复用 Jackson TypeFactory 构造的复杂类型，避免重复创建
     */
    private static final class JsonUtilsHolder {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final JavaType LIST_OF_ROUTER_VO =
                MAPPER.getTypeFactory().constructCollectionType(List.class, CloudRouterVo.class);
        private static final JavaType LIST_OF_USER_VO =
                MAPPER.getTypeFactory().constructCollectionType(List.class, CloudUserVo.class);
        private static final JavaType LIST_OF_OSS_VO =
                MAPPER.getTypeFactory().constructCollectionType(List.class, CloudOssVo.class);
    }

}