package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作状态（落库 sys_oper_log.status）
 *
 * <p><b>与服务端 {@code io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessStatus} 的 code 保持一致</b>。</p>
 *
 * @author sgchen
 * @since 1.2.1
 */
@Getter
@AllArgsConstructor
public enum CloudBusinessStatus {

    /**
     * 成功
     */
    SUCCESS(0, "成功"),

    /**
     * 失败
     */
    FAIL(1, "失败");

    /**
     * 上报码（服务端落库值）
     */
    private final Integer code;

    /**
     * 描述
     */
    private final String info;

    /**
     * 按枚举名转换（大小写不敏感，未知 / null 归 {@link #SUCCESS}）
     */
    public static CloudBusinessStatus of(String name) {
        if (name != null) {
            for (CloudBusinessStatus status : values()) {
                if (status.name().equalsIgnoreCase(name)) {
                    return status;
                }
            }
        }
        return SUCCESS;
    }
}
