package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 操作日志记录表 oper_log
 *
 * @author Lion Li
 */

@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志主键
     */
    @TableId(value = "oper_id")
    private Long operId;

    /**
     * 租户编号
     */
    private String tenantId;

    /**
     * 客户端 ID（来源子系统标识）
     */
    private String clientId;

    /**
     * 操作模块
     */
    private String title;

    /**
     * 业务类型（{@link io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType}，按 code 落库）
     */
    private Integer businessType;

    /**
     * 请求方法（如：io.gitee.chenshaogui.cloudauth.system.controller.SysMenuController.add()，自由文本非枚举）
     */
    private String method;

    /**
     * 请求方式（{@link io.gitee.chenshaogui.cloudauth.common.log.enums.RequestMethod}，标准 HTTP 动词大写）
     */
    private String requestMethod;

    /**
     * 操作类别（{@link io.gitee.chenshaogui.cloudauth.common.log.enums.OperatorType}，按 code 落库）
     */
    private Integer operatorType;

    /**
     * 操作人员
     */
    private String operName;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 请求url
     */
    private String operUrl;

    /**
     * 操作地址
     */
    private String operIp;

    /**
     * 操作地点
     */
    private String operLocation;

    /**
     * 请求参数
     */
    private String operParam;

    /**
     * 返回参数
     */
    private String jsonResult;

    /**
     * 操作状态（{@link io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessStatus}，按 code 落库）
     */
    private Integer status;

    /**
     * 错误消息
     */
    private String errorMsg;

    /**
     * 操作时间
     */
    private Date operTime;

    /**
     * 消耗时间
     */
    private Long costTime;

}
