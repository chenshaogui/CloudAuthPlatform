package io.gitee.chenshaogui.cloudauth.common.log.enums;

/**
 * HTTP 请求方式（落库 sys_oper_log.request_method，存储值即标准动词大写字符串）
 *
 * <p>子系统经 SDK 上报的自定义动词（非标准 HTTP method）原样透传，
 * 不强转为本枚举，避免丢失来源信息。</p>
 *
 * @author sgchen
 */
public enum RequestMethod {

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
    TRACE
}
