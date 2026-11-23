package io.gitee.chenshaogui.cloudauth.casm.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 密钥级别枚举
 *
 * @author JLL
 * @date 2025-12-03
 */
@Getter
@AllArgsConstructor
public enum KeyLevelEnum {
    CORE(1, "核心-HSM"),
    NORMAL(2, "普通-MYSQL");

    private Integer type;
    private String desc;

}
