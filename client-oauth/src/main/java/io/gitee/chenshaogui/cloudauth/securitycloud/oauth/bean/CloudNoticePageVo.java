package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通知公告分页结果（对应 {@code CloudPlatformHelper.getNoticeList} 返回值）
 *
 * <p>对齐服务端 RuoYi-Vue-Plus 的 {@code TableDataInfo} 简化结构：
 * 仅暴露业务方需要的 {@code rows} / {@code total} 字段，
 * 避免引入 {@code code/msg/total} 等与 sa-token SDK 风格不一致的字段。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加通知公告（sys_notice）模块
 */
@Data
public class CloudNoticePageVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据
     */
    private List<CloudNoticeVo> rows;

    /**
     * 总记录数
     */
    private Long total;

}
