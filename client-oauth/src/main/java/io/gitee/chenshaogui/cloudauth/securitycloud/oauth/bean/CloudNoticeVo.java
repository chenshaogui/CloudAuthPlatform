package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 通知公告视图对象 sys_notice
 *
 * <p>对应授权中心 {@code sys_notice} 表的客户端 VO，
 * 业务方通过 {@code CloudPlatformHelper.getNoticeList / getNoticeById} 远程拉取。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加通知公告（sys_notice）模块
 */
@Data
public class CloudNoticeVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 公告ID
     */
    private Long noticeId;

    /**
     * 公告标题
     */
    private String noticeTitle;

    /**
     * 公告类型（1通知 2公告）
     */
    private String noticeType;

    /**
     * 公告内容
     */
    private String noticeContent;

    /**
     * 公告状态（0正常 1关闭）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建者
     */
    private Long createBy;

    /**
     * 创建人名称
     */
    private String createByName;

    /**
     * 创建时间
     */
    private Date createTime;

}
