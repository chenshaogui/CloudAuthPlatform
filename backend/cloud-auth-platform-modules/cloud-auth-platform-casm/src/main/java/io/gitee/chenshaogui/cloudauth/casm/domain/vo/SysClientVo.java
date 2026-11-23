package io.gitee.chenshaogui.cloudauth.casm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.gitee.chenshaogui.cloudauth.casm.domain.SysClient;
import io.gitee.chenshaogui.cloudauth.common.excel.annotation.ExcelDictFormat;
import io.gitee.chenshaogui.cloudauth.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


/**
 * 授权管理视图对象 sys_client
 *
 * @author Michelle.Chung
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SysClient.class)
public class SysClientVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @ExcelProperty(value = "id")
    private Long id;

    /**
     * 客户端id
     */
    @ExcelProperty(value = "客户端id")
    private String clientId;

    /**
     * 客户端key
     */
    @ExcelProperty(value = "客户端key")
    private String clientKey;

    /**
     * 客户端秘钥
     */
    @ExcelProperty(value = "客户端秘钥")
    private String clientSecret;

    /**
     * 允许授权地址
     */
    private String allowUrl;

    /**
     * 授权类型
     */
    @ExcelProperty(value = "授权类型")
    private List<String> grantTypeList;

    /**
     * 授权类型
     */
    private String grantType;

    /**
     * 设备类型
     */
    private String deviceType;



    /**
     * token活跃超时时间
     */
    @ExcelProperty(value = "token活跃超时时间")
    private Long activeTimeout;

    /**
     * token固定超时时间
     */
    @ExcelProperty(value = "token固定超时时间")
    private Long timeout;

    /**
     * 状态（0正常 1停用）
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;


    /**
     * appKeyId
     */
    private Long appKeyId;

    /**
     * 平台KeyId
     */
    private Long platformKeyId;

    /**
     * appIpList
     */
    private String appIpList;


    /**
     * 签名密钥
     */
    private String signKey;

    /**
     * key池大小
     */
    private Integer keyPoolSize;


}
