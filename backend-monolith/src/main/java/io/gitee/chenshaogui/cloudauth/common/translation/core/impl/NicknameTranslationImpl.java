package io.gitee.chenshaogui.cloudauth.common.translation.core.impl;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.AllArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.translation.annotation.TranslationType;
import io.gitee.chenshaogui.cloudauth.common.translation.constant.TransConstant;
import io.gitee.chenshaogui.cloudauth.common.translation.core.TranslationInterface;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteUserService;

/**
 * 用户昵称翻译实现
 *
 * @author may
 */
@AllArgsConstructor
@TranslationType(type = TransConstant.USER_ID_TO_NICKNAME)
public class NicknameTranslationImpl implements TranslationInterface<String> {

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    public String translation(Object key, String other) {
        if (key instanceof Long id) {
            return remoteUserService.selectNicknameByIds(id.toString());
        } else if (key instanceof String ids) {
            return remoteUserService.selectNicknameByIds(ids);
        }
        return null;
    }
}
