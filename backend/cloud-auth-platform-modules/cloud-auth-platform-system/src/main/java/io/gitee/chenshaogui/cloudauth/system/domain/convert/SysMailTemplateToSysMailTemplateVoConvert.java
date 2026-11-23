package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.domain.SysMailTemplate;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailTemplateVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 租户转换器
 * @author sgchen
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysMailTemplateToSysMailTemplateVoConvert extends BaseMapper<SysMailTemplate, SysMailTemplateVo> {

}
