package io.gitee.chenshaogui.cloudauth.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.gitee.chenshaogui.cloudauth.common.excel.annotation.ExcelDictFormat;
import io.gitee.chenshaogui.cloudauth.common.excel.convert.ExcelDictConvert;
import io.gitee.chenshaogui.cloudauth.system.domain.SysKeyInfo;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;


/**
 * 密钥信息管理视图对象 sys_key_info
 *
 * @author JLL
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SysKeyInfo.class)
public class SysKeyInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @ExcelProperty(value = "id")
    private Long id;

    /**
     * 密钥标识
     */
    @ExcelProperty(value = "密钥标识")
    private String keyId;

    /**
     * 租户ID
     */
    @ExcelProperty(value = "租户ID")
    private Long tenantId;

    /**
     * 应用端ID
     */
    @ExcelProperty(value = "应用端ID")
    private String clientId;

    /**
     * 密钥名称
     */
    @ExcelProperty(value = "密钥名称")
    private String keyName;

    /**
     * 密钥级别
     */
    @ExcelProperty(value = "密钥级别")
    private String keyLevel;

    /**
     * 密钥类型
     */
    @ExcelProperty(value = "密钥类型")
    private String keyType;

    /**
     * 密钥算法
     */
    private String keyAlthm;

    /**
     * 密钥过期时间
     */
    @ExcelProperty(value = "密钥过期时间")
    private Date expireTime;

    /**
     * 密钥轮换次数
     */
    @ExcelProperty(value = "密钥轮换次数")
    private String rotateCount;

    /**
     * 密钥最后轮换时间
     */
    @ExcelProperty(value = "密钥最后轮换时间")
    private Date lastRotateTime;

    /**
     * 状态（0正常 1停用）
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;


    /**
     * 应用端名称
     */
    @ExcelProperty(value = "应用端名称")
    private String clientName;

    /**
     * 明文密钥
     */
    private String unkeys;

}
