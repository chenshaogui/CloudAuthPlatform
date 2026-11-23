package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 邮箱发送日志对象 sys_mail_log
 *
 * @author wdh
 * @since 2024-01-03
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("sys_mail_log")
public class SysMailLog extends BaseEntity {

    /**
     * 主键
     */
    @TableId
    private Long id;

    /**
     * 接收邮箱地址
     */
    private String toMail;
    /**
     * 发送邮箱编码
     */
    private String accountCode;
    /**
     * 发送邮箱地址
     */
    private String fromMail;
    /**
     * 模板id
     */
    private Long templateId;
    /**
     * 邮件标题
     */
    private String title;
    /**
     * 邮件内容
     */
    private String content;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;
    /**
     * 发送返回的消息 ID
     */
    private String sendMessageId;
    /**
     * 发送异常
     */
    private String sendException;

    @Schema(description = "发送状态 0=初始,1=发送成功,2=发送失败,3=模板未启用", hidden = true)
    private Integer status;

}
