package io.gitee.chenshaogui.cloudauth.common.log.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作状态（落库 sys_oper_log.status）
 *
 * <p>{@code code} 与历史数据（旧版 {@code ordinal()} 落库）保持一致。</p>
 *
 * @author ruoyi
 */
@Getter
@AllArgsConstructor
public enum BusinessStatus {

    /**
     * 成功
     */
    SUCCESS(0, "成功"),

    /**
     * 失败
     */
    FAIL(1, "失败");

    /**
     * 数据库存储值（与旧版 ordinal 一致）
     */
    private final Integer code;

    /**
     * 描述
     */
    private final String info;
}
