package io.gitee.chenshaogui.cloudauth.system.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 通知公告分页结果（与客户端 SDK 的 {@code CloudNoticePageVo} 一一对应）
 *
 * <p>仅暴露业务方需要的 {@code rows} / {@code total} 字段，避免引入与 SDK 风格不一致的
 * {@code code/msg} 等冗余字段。</p>
 *
 * @author sgchen
 * @since 1.3.0 通知公告（sys_notice）模块暴露
 */
@Data
public class RemoteNoticePageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据
     */
    private List<RemoteNoticeVo> rows;

    /**
     * 总记录数
     */
    private Long total;

}
