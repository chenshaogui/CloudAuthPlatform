package io.gitee.chenshaogui.cloudauth.casm.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 密钥类型枚举
 *
 * @author JLL
 * @date 2025-12-03
 */
@Getter
@AllArgsConstructor
public enum KeyTypeEnum {
    KEK("KEK", "密钥加密密钥", false, 1, true, true),
    PEK("PEK", "平台加密密钥", false, 1,  true, true),
    AEK("AEK", "应用端非对称密钥", false, Integer.MAX_VALUE, true, false),
    TEK("TEK", "数据传输密钥", true, 100, false, false),
    DEK("DEK", "数据加密密钥", false, 10, true, false);
    private String type;
    private String desc;
    private Boolean isExpire;
    private Integer defaultPool;
    private Boolean supportRotation;
    private Boolean isSystem;

    public static KeyTypeEnum getByType(String type) {
        if (type == null) {
            return KeyTypeEnum.TEK;
        }
        for (KeyTypeEnum value : values()) {
            if (value.getType().equals(type)) {
                return value;
            }
        }
        return KeyTypeEnum.TEK;
    }

}
