package io.gitee.chenshaogui.cloudauth.system.mapper;


import io.gitee.chenshaogui.cloudauth.common.mybatis.core.mapper.BaseMapperPlus;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMailTemplate;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailTemplateBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailTemplateVo;

import java.util.List;

/**
 * 邮箱模板Mapper接口
 *
 * @author wdh
 * @since 2024-01-02
 */
public interface SysMailTemplateMapper extends BaseMapperPlus<SysMailTemplate, SysMailTemplateVo> {

    List<SysMailTemplateVo> selectListByParam(SysMailTemplateBo param);

    int batchInsert(List<SysMailTemplate> list);
}
