package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 通知公告视图对象 sys_notice
 *
 * <p>Dubbo RPC 透传 VO，对应 {@code io.gitee.chenshaogui.cloudauth.system.domain.vo.SysNoticeVo}。
 * 客户端 SDK 通过 SSO push message 通道经授权中心拉取，避免子系统强依赖 system 内部包结构。</p>
 *
 * <p>公告分页结果统一返回 {@link RemoteNoticePageVo}（仅暴露业务方需要的 {@code rows} / {@code total}）。</p>
 *
 * @author sgchen
 * @since 1.3.0 通知公告（sys_notice）模块暴露
 */
@Data
public class RemoteNoticeVo implements Serializable {

    @Serial
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
