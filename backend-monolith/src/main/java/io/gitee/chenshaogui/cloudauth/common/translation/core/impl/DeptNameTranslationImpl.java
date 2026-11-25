package io.gitee.chenshaogui.cloudauth.common.translation.core.impl;
import org.springframework.beans.factory.annotation.Autowired;

import io.gitee.chenshaogui.cloudauth.common.translation.annotation.TranslationType;
import io.gitee.chenshaogui.cloudauth.common.translation.constant.TransConstant;
import io.gitee.chenshaogui.cloudauth.common.translation.core.TranslationInterface;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteDeptService;
import lombok.AllArgsConstructor;
/**
 * 部门翻译实现
 *
 * @author Lion Li
 */
@AllArgsConstructor
@TranslationType(type = TransConstant.DEPT_ID_TO_NAME)
public class DeptNameTranslationImpl implements TranslationInterface<String> {

    @Autowired
    private RemoteDeptService remoteDeptService;

    @Override
    public String translation(Object key, String other) {
        return remoteDeptService.selectDeptNameByIds(key.toString());
    }
}
