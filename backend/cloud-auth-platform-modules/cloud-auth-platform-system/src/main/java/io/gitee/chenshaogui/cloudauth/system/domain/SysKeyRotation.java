package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 密钥轮换信息管理对象 sys_key_rotation
 *
 * @author JLL
 * @date 2025-05-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_key_rotation")
public class SysKeyRotation extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 原密钥ID
     */
    private Long oldKeyId;

    /**
     * 新密钥ID
     */
    private Long newKeyId;

    /**
     * 轮换类型
     * 轮换类型：1 - 定期轮换、2 - 手动轮换、3 - 触发式轮换（密钥泄露 / 人员离职）
     */
    private Integer rotationType;

    /**
     * 轮换触发条件（如 “90 天周期到期”“检测到异常访问”“员工离职”）
     */
    private String rotationTrigger;

    /**
     * 轮换执行时间
     */
    private Date rotationTime;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 同步状态
     * 关联资源同步状态：0 - 未同步1 - 同步中2 - 同步成功3 - 同步失败
     */
    private Integer syncStatus;

    /**
     * 同步失败原因（sync_status=3 时填写，如 “部分物联网设备离线，同步超时”）
     */
    private String syncFailedReason;

    /**
     * 回滚状态：0 - 未回滚、1 - 回滚中、2 - 回滚成功、3 - 回滚失败
     */
    private Integer rollbackStatus;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态：1-正常 0-停用
     */
    private Integer status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
