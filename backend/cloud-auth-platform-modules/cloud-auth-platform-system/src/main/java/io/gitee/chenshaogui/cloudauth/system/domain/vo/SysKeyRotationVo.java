package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.gitee.chenshaogui.cloudauth.common.excel.annotation.ExcelDictFormat;
import io.gitee.chenshaogui.cloudauth.common.excel.convert.ExcelDictConvert;
import io.gitee.chenshaogui.cloudauth.system.domain.SysKeyRotation;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;


/**
 * 密钥轮换信息管理视图对象 sys_key_rotation
 *
 * @author JLL
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SysKeyRotation.class)
public class SysKeyRotationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 租户ID
     */
    @ExcelProperty(value = "租户ID")
    private String tenantId;

    /**
     * 原密钥ID
     */
    @ExcelProperty(value = "原密钥ID")
    private Long oldKeyId;

    /**
     * 新密钥ID
     */
    @ExcelProperty(value = "新密钥ID")
    private Long newKeyId;

    /**
     * 轮换类型
     * 轮换类型：1 - 定期轮换、2 - 手动轮换、3 - 触发式轮换（密钥泄露 / 人员离职）
     */
    @ExcelProperty(value = "轮换类型")
    @ExcelDictFormat(readConverterExp = "1=定期轮换,2=手动轮换,3=触发式轮换（密钥泄露 / 人员离职）")
    private String rotationType;

    /**
     * 轮换触发条件（如 “90 天周期到期”“检测到异常访问”“员工离职”）
     */
    @ExcelProperty(value = "轮换触发条件")
    private String rotationTrigger;

    /**
     * 轮换执行时间
     */
    @ExcelProperty(value = "轮换执行时间")
    private Date rotationTime;

    /**
     * 操作人ID
     */
    @ExcelProperty(value = "操作人ID")
    private Long operatorId;

    /**
     * 同步状态
     * 关联资源同步状态：0 - 未同步1 - 同步中2 - 同步成功3 - 同步失败
     */
    @ExcelProperty(value = "同步状态")
    @ExcelDictFormat(readConverterExp = "0=未同步,1=同步中,2=同步成功,3=同步失败")
    private String syncStatus;

    /**
     * 同步失败原因（sync_status=3 时填写，如 “部分物联网设备离线，同步超时”）
     */
    @ExcelProperty(value = "同步失败原因")
    private String syncFailedReason;

    /**
     * 回滚状态：0 - 未回滚、1 - 回滚中、2 - 回滚成功、3 - 回滚失败
     */
    @ExcelProperty(value = "回滚状态")
    @ExcelDictFormat(readConverterExp = "0=未回滚,1=回滚中,2=回滚成功,3=回滚失败")
    private String rollbackStatus;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 状态（0正常 1停用）
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

}
