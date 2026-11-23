package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知公告查询条件 sys_notice
 *
 * <p>用于 {@code CloudPlatformHelper.getNoticeList(bo)} 的可选过滤条件，
 * 所有字段均可空（为空时不过滤）。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加通知公告（sys_notice）模块
 */
@Data
public class CloudNoticeBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 公告ID（可选）
     */
    private Long noticeId;

    /**
     * 公告标题（可选，模糊匹配）
     */
    private String noticeTitle;

    /**
     * 公告类型（可选，1通知 2公告）
     */
    private String noticeType;

    /**
     * 公告状态（可选，0正常 1关闭）
     */
    private String status;

    /**
     * 创建人名称（可选，模糊匹配）
     */
    private String createByName;

    /**
     * 当前页（可选，从 1 开始，默认 1）
     */
    private Integer pageNum;

    /**
     * 每页大小（可选，默认 10）
     */
    private Integer pageSize;

    /**
     * 构建缓存 key 后缀
     */
    public String toCacheKeySuffix() {
        StringBuilder sb = new StringBuilder();
        sb.append(noticeId == null ? "" : noticeId)
                .append('|').append(noticeTitle == null ? "" : noticeTitle)
                .append('|').append(noticeType == null ? "" : noticeType)
                .append('|').append(status == null ? "" : status)
                .append('|').append(createByName == null ? "" : createByName)
                .append('|').append(pageNum == null ? "" : pageNum)
                .append('|').append(pageSize == null ? "" : pageSize);
        return sb.toString();
    }

}
