package io.gitee.chenshaogui.cloudauth.common.translation.core.impl;

import io.gitee.chenshaogui.cloudauth.common.core.service.DictService;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.translation.annotation.TranslationType;
import io.gitee.chenshaogui.cloudauth.common.translation.constant.TransConstant;
import io.gitee.chenshaogui.cloudauth.common.translation.core.TranslationInterface;
import lombok.AllArgsConstructor;

/**
 * 字典翻译实现
 *
 * @author Lion Li
 */
@AllArgsConstructor
@TranslationType(type = TransConstant.DICT_TYPE_TO_LABEL)
public class DictTypeTranslationImpl implements TranslationInterface<String> {

    private final DictService dictService;

    @Override
    public String translation(Object key, String other) {
        if (key instanceof String && StringUtils.isNotBlank(other)) {
            return dictService.getDictLabel(other, key.toString());
        }
        return null;
    }
}
