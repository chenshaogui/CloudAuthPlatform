package io.gitee.chenshaogui.cloudauth.system.domain.bo;


import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMailLog;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮箱发送日志 分页请求参数
 *
 * @author sgchen
 * @since 2024-01-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysMailLog.class, reverseConvertGenerate = false)
public class SysMailLogBo extends BaseEntity {

    /**
     * 编号
     */
    @Schema(description = "主键")
    private Long id;
    /**
     * 接收邮箱地址
     */
    @Schema(description = "接收邮箱地址")
    private String toMail;

    /**
     * 发送邮箱编码
     */
    @Schema(description = "发送邮箱编码")
    private String accountCode;

    /**
     * 发送邮箱地址
     */
    @Schema(description = "发送邮箱地址")
    private String fromMail;

    /**
     * 模板id
     */
    @Schema(description = "模板id")
    private Long templateId;

    /**
     * 邮件标题
     */
    @Schema(description = "邮件标题")
    private String title;

    /**
     * 发送时间
     */
    @Schema(description = "发送时间")
    private LocalDateTime sendTime;

    /**
     * 发送返回的消息 ID
     */
    @Schema(description = "发送返回的消息")
    private String sendMessageId;
    /**
     * 发送状态 0=初始,1=发送成功,2=发送失败,3=模板未启用
     */
    @Schema(description = "发送状态 0=初始,1=发送成功,2=发送失败,3=模板未启用")
    private Integer status;


    /**
     * 发送人ID
     */
    @Schema(description = "发送人ID")
    private Long createUserId;


}
