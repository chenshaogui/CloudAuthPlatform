package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作人类别（落库 sys_oper_log.operator_type）
 *
 * <p><b>与服务端 {@code io.gitee.chenshaogui.cloudauth.common.log.enums.OperatorType} 的 code 保持一致</b>。</p>
 *
 * @author sgchen
 * @since 1.2.1
 */
@Getter
@AllArgsConstructor
public enum CloudOperatorType {

    /**
     * 其它
     */
    OTHER(0, "其它"),

    /**
     * 后台用户
     */
    MANAGE(1, "后台用户"),

    /**
     * 手机端用户
     */
    MOBILE(2, "手机端用户");

    /**
     * 上报码（服务端落库值）
     */
    private final Integer code;

    /**
     * 描述
     */
    private final String info;

    /**
     * 按枚举名转换（大小写不敏感，未知 / null 归 {@link #MANAGE}）
     */
    public static CloudOperatorType of(String name) {
        if (name != null) {
            for (CloudOperatorType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }
        return MANAGE;
    }
}
