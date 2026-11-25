package io.gitee.chenshaogui.cloudauth.system.domain;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author sgchen
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysSmsSendRecord extends BaseEntity {

    /**
     * 模版id
     */
    private Long sysTemplateId;


    /**
     * 模版类型
     */
    private Integer templateType;

    /**
     * 签名id
     */
    private Long sysSignId;

    /**
     * app用户id
     */
    private Long appMemberId;

    /**
     * 手机号
     */
    private String phoneNumber;

    /**
     * 短信内容
     */
    private String noteContent;

    /**
     * 短信参数
     */
    private String noteParams;

    /**
     * 发送类型，1-系统，2-租户
     */
    private Integer sendUserType;

    /**
     * 发送人id
     */
    private Long sendSysUserId;

    /**
     * 发送人id
     */
    private Long sendAppUserId;

    /**
     * 发送门店id
     */
    private Long sendStoreId;

    /**
     * 发送状态，1-已发送，2-发送失败，3-回执发送成功，4-回执发送失败
     */
    private Integer sendStatus;

    /** 备注 */
    private String remark;

    private String errorCode;
    private String errorMessage;
    private String requestId;
    private String serialNo;
}
