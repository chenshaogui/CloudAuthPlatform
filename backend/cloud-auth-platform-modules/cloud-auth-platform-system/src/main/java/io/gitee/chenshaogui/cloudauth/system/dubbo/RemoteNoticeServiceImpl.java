package io.gitee.chenshaogui.cloudauth.system.dubbo;

import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteNoticeService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteNoticePageVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteNoticeVo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysNoticeBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysNoticeVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 通知公告服务 Dubbo 实现
 *
 * <p>将内部 {@link ISysNoticeService}（sys_notice）按需转换为 {@link RemoteNoticeVo} /
 * {@link RemoteNoticePageVo} 暴露给授权中心，由授权中心通过 SSO push message 通道转发到客户端 SDK。</p>
 *
 * <p>分页参数统一收敛：pageNum 小于 1 时按 1 处理，pageSize 小于 1 时按 10 处理，
 * 与客户端 SDK {@code CloudPlatformHelper.getNoticeList} 行为保持一致。</p>
 *
 * @author sgchen
 * @since 1.3.0 通知公告（sys_notice）模块暴露
 */
@Slf4j
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteNoticeServiceImpl implements RemoteNoticeService {

    private final ISysNoticeService noticeService;

    /**
     * 分页查询通知公告列表
     */
    @Override
    public RemoteNoticePageVo listNotice(Long noticeId, String noticeTitle, String noticeType, String status,
                                          String createByName, Integer pageNum, Integer pageSize) {
        int pn = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;

        SysNoticeBo bo = new SysNoticeBo();
        bo.setNoticeId(noticeId);
        bo.setNoticeTitle(noticeTitle);
        bo.setNoticeType(noticeType);
        bo.setStatus(status);
        bo.setCreateByName(createByName);

        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(pn);
        pageQuery.setPageSize(ps);

        // 复用已有 IService 的分页（内部按 BO 条件过滤 + createBy 名称映射）
        TableDataInfo<SysNoticeVo> td = noticeService.selectPageNoticeList(bo, pageQuery);

        RemoteNoticePageVo result = new RemoteNoticePageVo();
        result.setRows(MapstructUtils.convert(td.getRows(), RemoteNoticeVo.class));
        result.setTotal(td.getTotal());
        return result;
    }

    /**
     * 根据公告 ID 查询公告详情
     */
    @Override
    public RemoteNoticeVo getByNoticeId(Long noticeId) {
        if (noticeId == null) {
            return null;
        }
        SysNoticeVo vo = noticeService.selectNoticeById(noticeId);
        return MapstructUtils.convert(vo, RemoteNoticeVo.class);
    }

}
