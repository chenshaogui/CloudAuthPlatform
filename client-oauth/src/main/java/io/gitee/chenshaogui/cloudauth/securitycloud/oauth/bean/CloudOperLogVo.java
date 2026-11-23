package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums.CloudBusinessStatus;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums.CloudBusinessType;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums.CloudOperatorType;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums.CloudRequestMethod;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 子系统操作日志（SDK 上报数据体，最终落库授权中心的 {@code sys_oper_log} 表）
 *
 * <p>业务系统（或子系统自有框架的日志注解切面）通过
 * {@code CloudPlatformHelper.writeOperLog(vo)} 单条提交，SDK 异步批量推送到授权中心，
 * 复用统一平台的操作日志表 {@code sys_oper_log}，与后台管理操作审计同表查询。</p>
 *
 * <p>字段与 {@code RemoteOperLogBo} / {@code sys_oper_log} 一一对应：
 * 子系统框架的注解切面负责填充 method / operParam / jsonResult 等审计明细，
 * 未填充的身份维度（operName / tenantId / clientId / operIp）由 SDK 自动补齐当前登录态。</p>
 *
 * @author sgchen
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudOperLogVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户编号
     */
    private String tenantId;

    /**
     * 客户端 ID（子系统标识，落库 sys_oper_log.client_id）
     */
    private String clientId;

    /**
     * 用户 ID（可选，仅上报用，不落库）
     */
    private Long userId;

    /**
     * 操作模块（如：订单管理）
     */
    private String title;

    /**
     * 业务类型（推荐用 {@link CloudBusinessType} 枚举 setter 赋值；本字段为对应 code）
     */
    private Integer businessType;

    /**
     * 方法名称（如：com.xxx.OrderController.export()，自由文本非枚举）
     */
    private String method;

    /**
     * 请求方式（推荐用 {@link CloudRequestMethod} 枚举 setter 赋值）
     */
    private String requestMethod;

    /**
     * 操作类别（推荐用 {@link CloudOperatorType} 枚举 setter 赋值；本字段为对应 code）
     */
    private Integer operatorType;

    /**
     * 操作人员（为空时自动填充当前登录账号）
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
     * 操作来源 IP（为空时自动填充当前请求 IP）
     */
    private String operIp;

    /**
     * 操作地点
     */
    private String operLocation;

    /**
     * 请求参数（JSON 串，建议由子系统切面序列化后填入）
     */
    private String operParam;

    /**
     * 返回参数（JSON 串）
     */
    private String jsonResult;

    /**
     * 操作状态（推荐用 {@link CloudBusinessStatus} 枚举 setter 赋值；本字段为对应 code）
     */
    private Integer status;

    /**
     * 错误消息
     */
    private String errorMsg;

    /**
     * 操作时间（为空时自动填充当前时间）
     */
    private Date operTime;

    /**
     * 消耗时间（毫秒）
     */
    private Long costTime;

    // ================== 枚举便捷 setter（与 lombok 生成的 Integer/String 版本重载共存） ==================
    // 字段本体保持 Integer / String：上报 JSON 序列化与反序列化（服务端 RemoteOperLogReportBo）均按数字码 / 字符串流转，
    // 枚举 setter 仅是调用方的类型安全入口，序列化不经过枚举。

    /**
     * 业务类型枚举赋值（等价于 setBusinessType(type.getCode())）
     */
    public void setBusinessType(CloudBusinessType type) {
        this.businessType = type == null ? null : type.getCode();
    }

    /**
     * 操作类别枚举赋值（等价于 setOperatorType(type.getCode())）
     */
    public void setOperatorType(CloudOperatorType type) {
        this.operatorType = type == null ? null : type.getCode();
    }

    /**
     * 操作状态枚举赋值（等价于 setStatus(status.getCode())）
     */
    public void setStatus(CloudBusinessStatus status) {
        this.status = status == null ? null : status.getCode();
    }

    /**
     * 请求方式枚举赋值（等价于 setRequestMethod(method.name())）
     */
    public void setRequestMethod(CloudRequestMethod method) {
        this.requestMethod = method == null ? null : method.name();
    }
}
