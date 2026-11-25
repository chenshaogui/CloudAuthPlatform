package io.gitee.chenshaogui.cloudauth.system.domain.bo;


import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMailTemplate;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 邮箱模板 分页请求参数
 *
 * @author wdh
 * @since 2024-01-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysMailTemplate.class, reverseConvertGenerate = false)
public class SysMailTemplateBo extends BaseEntity {

    /**
     * 编号
     */
    @Schema(description = "主键")
    private Long id;

    /**
     * 模板类型  1=验证码
     */
    @Schema(description = "模板类型  1=验证码")
    private Integer type;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称")
    private String name;

    /**
     * 模板编码
     */
    @Schema(description = "模板编码")
    private String code;

    /**
     * 发送的邮箱账号编码
     */
    @Schema(description = "发送的邮箱账号编码 惠生活=HSH")
    private String accountCode;

    /**
     * 模板标题
     */
    @Schema(description = "模板标题")
    private String title;

    /**
     * 模板内容
     */
    @Schema(description = "模板内容")
    private String content;

    /**
     * 状态 0=停用,1=启用
     */
    @Schema(description = "状态 0=停用,1=启用")
    private Integer status;


}
