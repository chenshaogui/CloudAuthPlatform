package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * OSS 对象存储视图对象 sys_oss
 *
 * <p>对应授权中心 {@code sys_oss} 表的客户端 VO，
 * 业务方通过 {@code CloudPlatformHelper.getOssByIds} 远程拉取。
 * 单个文件 URL 走 {@code CloudPlatformHelper.getOssUrlById}（轻量、纯字符串）。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加文件存储（sys_oss）模块
 */
@Data
public class CloudOssVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 对象存储主键
     */
    private Long ossId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 原名
     */
    private String originalName;

    /**
     * 文件后缀名
     */
    private String fileSuffix;

    /**
     * URL 地址
     */
    private String url;

    /**
     * 扩展字段
     */
    private String ext1;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 上传人
     */
    private Long createBy;

    /**
     * 上传人名称
     */
    private String createByName;

    /**
     * 服务商
     */
    private String service;

}
