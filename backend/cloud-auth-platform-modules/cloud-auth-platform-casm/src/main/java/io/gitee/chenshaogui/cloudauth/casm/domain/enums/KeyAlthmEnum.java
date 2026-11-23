package io.gitee.chenshaogui.cloudauth.casm.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 密钥算法类型枚举
 *
 * @author JLL
 * @date 2025-12-03
 */
@Getter
@AllArgsConstructor
public enum KeyAlthmEnum {

    AES("AES", "AES对称密钥"),
    SM2("SM2", "SM2国密非对称密钥"),
    SM4("SM4", "SM4国密对称密钥"),
    RSA("RSA", "RSA非对称密钥");

    private String type;
    private String desc;

    // 根据type返回对应的枚举值
    public static KeyAlthmEnum getByType(String type) {
        if (type == null) {
            return KeyAlthmEnum.SM4;
        }
        for (KeyAlthmEnum value : KeyAlthmEnum.values()) {
            if (value.getType().equals(type)) {
                return value;
            }
        }
        return KeyAlthmEnum.SM4;
    }

}
