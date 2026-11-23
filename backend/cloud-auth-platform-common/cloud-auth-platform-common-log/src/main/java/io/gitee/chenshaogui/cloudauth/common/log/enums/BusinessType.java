package io.gitee.chenshaogui.cloudauth.common.log.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务操作类型（落库 sys_oper_log.business_type）
 *
 * <p>{@code code} 与历史数据（旧版 {@code ordinal()} 落库）保持一致，
 * <b>新增枚举值只能追加在尾部，禁止插入中间或修改已有 code</b>，
 * 否则存量日志的业务类型含义会被污染。</p>
 *
 * @author ruoyi
 */
@Getter
@AllArgsConstructor
public enum BusinessType {

    /**
     * 其它
     */
    OTHER(0, "其它"),

    /**
     * 新增
     */
    INSERT(1, "新增"),

    /**
     * 修改
     */
    UPDATE(2, "修改"),

    /**
     * 删除
     */
    DELETE(3, "删除"),

    /**
     * 授权
     */
    GRANT(4, "授权"),

    /**
     * 导出
     */
    EXPORT(5, "导出"),

    /**
     * 导入
     */
    IMPORT(6, "导入"),

    /**
     * 强退
     */
    FORCE(7, "强退"),

    /**
     * 生成代码
     */
    GENCODE(8, "生成代码"),

    /**
     * 清空数据
     */
    CLEAN(9, "清空数据");

    /**
     * 数据库存储值（与旧版 ordinal 一致）
     */
    private final Integer code;

    /**
     * 描述（对应字典 sys_oper_type 的 label）
     */
    private final String info;

    /**
     * 按 code 反查枚举（SDK 上报等外部数字入参转换用）
     *
     * @param code 业务类型编码（可为 null）
     * @return 对应枚举；code 为 null 或未匹配时返回 {@link #OTHER}
     */
    public static BusinessType valueOfCode(Integer code) {
        if (code != null) {
            for (BusinessType type : values()) {
                if (type.code.intValue() == code) {
                    return type;
                }
            }
        }
        return OTHER;
    }
}
