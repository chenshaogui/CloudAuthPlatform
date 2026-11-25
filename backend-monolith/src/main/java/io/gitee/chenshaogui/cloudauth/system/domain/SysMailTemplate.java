package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


/**
 * 邮箱模板对象 sys_mail_template
 *
 * @author wdh
 * @since 2024-01-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_mail_template")
public class SysMailTemplate extends BaseEntity {

    /**
     * 主键
     */
    @TableId
    private Long id;

    /**
     * 模板类型 1=验证码
     */
    private Integer type;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 模板编码
     */
    private String code;
    /**
     * 发送的邮箱账号编码
     */
    private String accountCode;
    /**
     * 模板标题
     */
    private String title;
    /**
     * 模板内容
     */
    private String content;

    /**
     * 备注
     */
    private String remark;
    /**
     * 状态 0=停用,1=启用
     */
    private Integer status;

    /**
     * 参数数组(自动根据内容生成)
     */
    private String typeParams;

}
