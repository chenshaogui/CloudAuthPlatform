package io.gitee.chenshaogui.cloudauth.system.api.domain.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 子系统操作日志（SDK 批量上报数据体，最终落库 {@code sys_oper_log}）
 *
 * <p>由客户端 SDK（cloud-auth-client-oauth 的 CloudOperLogRecorder）
 * 有界队列缓冲后批量推送，字段与 SDK 的 {@code CloudOperLogVo} 及
 * {@link RemoteOperLogBo} 一一对应（{@code userId} 仅上报用，不落库）。
 * 服务端转换为 {@link RemoteOperLogBo} 后复用已有
 * {@code RemoteLogService.saveLog} 链路写入 {@code sys_oper_log} 表。</p>
 *
 * @author sgchen
 */
@Data
public class RemoteOperLogReportBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户编号
     */
    private String tenantId;

    /**
     * 客户端 ID（子系统标识，服务端按消息来源覆盖）
     */
    private String clientId;

    /**
     * 用户 ID（可选，仅上报用，不落库）
     */
    private Long userId;

    /**
     * 操作模块（落库 title）
     */
    private String title;

    /**
     * 业务类型（0其它 1新增 2修改 3删除）
     */
    private Integer businessType;

    /**
     * 方法名称
     */
    private String method;

    /**
     * 请求方式（GET / POST / PUT / DELETE）
     */
    private String requestMethod;

    /**
     * 操作类别（0其它 1后台用户 2手机端用户）
     */
    private Integer operatorType;

    /**
     * 操作人员（落库 oper_name）
     */
    private String operName;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 请求 URL
     */
    private String operUrl;

    /**
     * 操作来源 IP（落库 oper_ip）
     */
    private String operIp;

    /**
     * 操作地点
     */
    private String operLocation;

    /**
     * 请求参数（JSON 串）
     */
    private String operParam;

    /**
     * 返回参数（JSON 串）
     */
    private String jsonResult;

    /**
     * 操作状态（0正常 1异常）
     */
    private Integer status;

    /**
     * 错误消息
     */
    private String errorMsg;

    /**
     * 操作时间（落库 oper_time）
     */
    private Date operTime;

    /**
     * 消耗时间（毫秒，落库 cost_time）
     */
    private Long costTime;
}
