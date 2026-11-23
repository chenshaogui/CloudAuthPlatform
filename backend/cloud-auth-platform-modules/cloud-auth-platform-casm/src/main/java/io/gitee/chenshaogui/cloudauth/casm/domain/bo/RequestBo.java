package io.gitee.chenshaogui.cloudauth.casm.domain.bo;

import io.gitee.chenshaogui.cloudauth.common.core.validate.AddGroup;
import io.gitee.chenshaogui.cloudauth.common.core.validate.QueryGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 密钥申请请求对象
 *
 * @author JLL
 */
@Data
public class RequestBo {

    /**
     * clientId
     */
    @NotNull(message = "clientId不能为空", groups = { AddGroup.class, QueryGroup.class })
    private String clientId;


    /**
     * 签名
     */
    @NotNull(message = "sign不能为空", groups = { AddGroup.class, QueryGroup.class })
    private String sign;


    /**
     * 时间戳
     */
    @NotNull(message = "timestamp不能为空", groups = { AddGroup.class, QueryGroup.class })
    private Long timestamp;

    /**
     * 随机字符串
     */
    @NotBlank(message = "nonceStr不能为空", groups = { AddGroup.class, QueryGroup.class })
    private String nonceStr;


    /**
     * 数据对象
     */
    @NotBlank(message = "数据据对象不能为空", groups = { AddGroup.class, QueryGroup.class })
    private String data;

    /**
     * 请求id，用于跟踪请求日志
     */
    private String requestId;


}
