package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户分页查询结果（对应 {@code CloudPlatformHelper.getRoleUserPage} 返回值）
 *
 * <p>结构与 {@link CloudNoticePageVo} 对齐：{@code rows} + {@code total}。
 * 适用于按角色查用户等需要分页的用户列表场景。</p>
 *
 * @author sgchen
 * @since 1.5.6
 */
@Data
public class CloudUserPageVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据
     */
    private List<CloudUserVo> rows;

    /**
     * 总记录数
     */
    private Long total;

}
