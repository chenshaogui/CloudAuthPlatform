package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.domain.SysArea;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysAreaVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 区域转换器
 * @author zhujie
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysAreaVoConvert extends BaseMapper<SysArea, SysAreaVo> {

}
