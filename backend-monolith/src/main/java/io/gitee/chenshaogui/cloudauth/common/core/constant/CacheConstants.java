package io.gitee.chenshaogui.cloudauth.common.core.constant;

/**
 * 缓存的key 常量
 *
 * @author Lion Li
 */
public interface CacheConstants {

    /**
     * 在线用户 redis key
     */
    String ONLINE_TOKEN_KEY = "online_tokens:";

    /**
     * 参数管理 cache key
     */
    String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    String SYS_DICT_KEY = "sys_dict:";

    /**
     * 登录账户密码错误次数 redis key
     */
    String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    /**
     * 权限变更标记 redis key 前缀（权限同步机制）
     *
     * <p>完整 key 结构统一由 {@code io.gitee.chenshaogui.cloudauth.common.redis.utils.PermissionChangeMarkUtils} 定义，
     * 严禁在业务代码中手工拼装，否则极易出现"写入 key ≠ 读取 key"导致同步静默失效：</p>
     * <ul>
     *   <li>{@code perm_changed:u:{userId}} —— 精确用户（注意是 userId，不是 loginId）</li>
     *   <li>{@code perm_changed:t:{tenantId}} —— 租户维度</li>
     *   <li>{@code perm_changed:_ALL_} —— 全网广播</li>
     * </ul>
     * <p>标记的值为"变更时间戳（毫秒）"而非布尔存在性，TTL 120 秒。
     * 服务端只写不删，客户端按时间戳去重，从而同时避免"丢通知"与"重复刷新"</p>
     */
    String PERMISSION_CHANGED_KEY_PREFIX = "perm_changed:";

    /**
     * 短信验证码发送冷却锁 redis key 前缀（按手机号限流，发送成功后短期锁定）
     */
    String SMS_SEND_LIMIT_KEY = "sms_send_limit:";

}
