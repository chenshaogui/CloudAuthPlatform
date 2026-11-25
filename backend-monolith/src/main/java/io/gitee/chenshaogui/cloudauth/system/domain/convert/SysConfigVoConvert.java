package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteConfigVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysConfigVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 参数配置转换器
 *
 * @author sgchen
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysConfigVoConvert extends BaseMapper<SysConfigVo, RemoteConfigVo> {

}
