package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteKeyInfoVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyInfoVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 密钥信息数据转换器
 *
 * @author JLL
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysKeyInfoVoConvert extends BaseMapper<RemoteKeyInfoVo, SysKeyInfoVo> {
}
