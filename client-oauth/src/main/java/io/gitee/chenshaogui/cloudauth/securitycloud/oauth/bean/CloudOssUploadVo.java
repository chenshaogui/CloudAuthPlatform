package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * OSS 文件上传响应对象
 *
 * <p>对应 {@code /oss/upload} 接口返回值，
 * 业务方拿到后即可在表单 / 列表中展示已上传的文件，
 * 需保存 {@code ossId} 用于后续关联业务数据。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加文件存储（sys_oss）模块
 */
@Data
public class CloudOssUploadVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * URL 地址
     */
    private String url;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 对象存储主键
     */
    private String ossId;

}
