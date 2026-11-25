package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteNoticePageVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteNoticeVo;

/**
 * 通知公告服务（Dubbo）
 *
 * <p>对外暴露 sys_notice 的查询能力，供授权中心 SSO push message 通道转发到客户端 SDK。</p>
 *
 * @author sgchen
 * @since 1.3.0 通知公告（sys_notice）模块暴露
 */
public interface RemoteNoticeService {

    /**
     * 分页查询通知公告列表（按可选条件过滤）
     *
     * @param noticeId     公告ID（可选）
     * @param noticeTitle  公告标题（可选，模糊匹配）
     * @param noticeType   公告类型（可选，1通知 2公告）
     * @param status       公告状态（可选，0正常 1关闭）
     * @param createByName 创建人名称（可选，模糊匹配）
     * @param pageNum      当前页（从 1 开始；为空或小于 1 时按 1 处理）
     * @param pageSize     每页大小（为空或小于 1 时按 10 处理）
     * @return 分页结果（rows / total）
     */
    RemoteNoticePageVo listNotice(Long noticeId, String noticeTitle, String noticeType, String status,
                                   String createByName, Integer pageNum, Integer pageSize);

    /**
     * 根据公告 ID 查询公告详情
     *
     * @param noticeId 公告ID
     * @return 公告详情；未找到时返回 null
     */
    RemoteNoticeVo getByNoticeId(Long noticeId);

}
