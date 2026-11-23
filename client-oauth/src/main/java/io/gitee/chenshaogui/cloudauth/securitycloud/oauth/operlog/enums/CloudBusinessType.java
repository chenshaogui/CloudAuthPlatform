package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.operlog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务操作类型（落库 sys_oper_log.business_type）
 *
 * <p><b>与服务端 {@code io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType} 的 code 保持一致</b>，
 * 子系统上报时请使用本枚举赋值；服务端新增类型时本 SDK 需同步升级。</p>
 *
 * <p>新增枚举值只能尾部追加，禁止插入中间或修改已有 code。</p>
 *
 * @author sgchen
 * @since 1.2.1
 */
@Getter
@AllArgsConstructor
public enum CloudBusinessType {

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
     * 上报码（服务端落库值）
     */
    private final Integer code;

    /**
     * 描述
     */
    private final String info;

    /**
     * 按枚举名转换（大小写不敏感，未知 / null 归 {@link #OTHER}）
     *
     * <p>适配子系统自有注解枚举的名称转译：{@code CloudBusinessType.of(otherEnum.name())}</p>
     */
    public static CloudBusinessType of(String name) {
        if (name != null) {
            for (CloudBusinessType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }
        return OTHER;
    }
}
