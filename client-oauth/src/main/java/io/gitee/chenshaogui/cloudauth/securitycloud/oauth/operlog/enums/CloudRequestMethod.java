package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums;

/**
 * HTTP 请求方式（落库 sys_oper_log.request_method，上报值即标准动词大写字符串）
 *
 * <p>与服务端 {@code io.gitee.chenshaogui.cloudauth.common.log.enums.RequestMethod} 语义一致；
 * 子系统网关自定义动词（非标准 HTTP method）可直接以字符串透传，
 * 不强转为本枚举，避免丢失来源信息。</p>
 *
 * @author sgchen
 * @since 1.2.1
 */
public enum CloudRequestMethod {

    /**
     * 查询
     */
    GET,

    /**
     * 新增 / 提交
     */
    POST,

    /**
     * 修改
     */
    PUT,

    /**
     * 删除
     */
    DELETE,

    /**
     * 局部修改
     */
    PATCH,

    /**
     * 请求头探测
     */
    HEAD,

    /**
     * 跨域探测
     */
    OPTIONS,

    /**
     * 回环测试
     */
    TRACE;

    /**
     * 按请求动词转换（大小写不敏感）
     *
     * <p>非标准 HTTP 动词（网关自定义等）返回 {@code null}，
     * 调用方可改用 {@code setRequestMethod(String)} 原样透传。</p>
     */
    public static CloudRequestMethod of(String method) {
        if (method != null) {
            for (CloudRequestMethod m : values()) {
                if (m.name().equalsIgnoreCase(method)) {
                    return m;
                }
            }
        }
        return null;
    }
}
