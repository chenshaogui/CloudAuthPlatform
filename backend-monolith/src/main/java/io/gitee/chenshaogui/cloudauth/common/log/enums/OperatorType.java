package io.gitee.chenshaogui.cloudauth.common.log.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作人类别（落库 sys_oper_log.operator_type）
 *
 * <p>{@code code} 与历史数据（旧版 {@code ordinal()} 落库）保持一致，
 * 新增值只能尾部追加，禁止插入中间。</p>
 *
 * @author ruoyi
 */
@Getter
@AllArgsConstructor
public enum OperatorType {

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
     * 数据库存储值（与旧版 ordinal 一致）
     */
    private final Integer code;

    /**
     * 描述
     */
    private final String info;

    /**
     * 按 code 反查枚举
     *
     * @param code 操作类别编码（可为 null）
     * @return 对应枚举；code 为 null 或未匹配时返回 {@link #MANAGE}（子系统上报默认后台用户）
     */
    public static OperatorType valueOfCode(Integer code) {
        if (code != null) {
            for (OperatorType type : values()) {
                if (type.code.intValue() == code) {
                    return type;
                }
            }
        }
        return MANAGE;
    }
}
