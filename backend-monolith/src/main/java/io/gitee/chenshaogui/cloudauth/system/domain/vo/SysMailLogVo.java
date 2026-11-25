package io.gitee.chenshaogui.cloudauth.system.domain.vo;


import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ColumnWidth;
import io.gitee.chenshaogui.cloudauth.common.excel.annotation.ExcelDictFormat;
import io.gitee.chenshaogui.cloudauth.common.excel.convert.ExcelDictConvert;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 邮箱发送日志视图对象
 *
 * @author wdh
 * @since 2024-01-03
 */
@Data
@ColumnWidth(value = 20)
@ExcelIgnoreUnannotated
public class SysMailLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 编号
     */
    @Schema(description = "编号")
    @ExcelProperty(value = "编号")
    private Long id;
    /**
     * 接收邮箱地址
     */
    @Schema(description = "接收邮箱地址")
    @ExcelProperty(value = "接收邮箱地址")
    private String toMail;

    /**
     * 发送邮箱编码
     */
    @Schema(description = "发送邮箱编码")
    @ExcelProperty(value = "发送邮箱编码")
    private String accountCode;

    /**
     * 发送邮箱地址
     */
    @Schema(description = "发送邮箱地址")
    @ExcelProperty(value = "发送邮箱地址")
    private String fromMail;

    /**
     * 模板id
     */
    @Schema(description = "模板id")
    @ExcelProperty(value = "模板id")
    private Long templateId;

    /**
     * 邮件标题
     */
    @Schema(description = "邮件标题")
    @ExcelProperty(value = "邮件标题")
    private String title;

    /**
     * 邮件内容
     */
    @Schema(description = "邮件内容")
    @ExcelProperty(value = "邮件内容")
    private String content;

    /**
     * 邮件参数
     */
    @Schema(description = "邮件参数")
    @ExcelProperty(value = "邮件参数")
    private String templateParams;

    /**
     * 发送时间
     */
    @Schema(description = "发送时间")
    @ExcelProperty(value = "发送时间")
    private LocalDateTime sendTime;

    /**
     * 发送返回的消息 ID
     */
    @Schema(description = "发送返回的消息 ID")
    @ExcelProperty(value = "发送返回的消息 ID")
    private String sendMessageId;

    /**
     * 发送异常
     */
    @Schema(description = "发送异常信息")
    @ExcelProperty(value = "发送异常")
    private String sendException;

    /**
     * 发送状态 0=初始,1=发送成功,2=发送失败,3=模板未启用
     */
    @Schema(description = "发送状态 0=初始,1=发送成功,2=发送失败,3=模板未启用")
    @ExcelProperty(value = "发送状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=初始,1=发送成功,2=发送失败,3=模板未启用")
    private Integer status;

}
