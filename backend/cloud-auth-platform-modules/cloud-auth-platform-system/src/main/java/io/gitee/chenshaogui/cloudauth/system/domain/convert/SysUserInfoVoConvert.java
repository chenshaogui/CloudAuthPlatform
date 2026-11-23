package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteUserInfoVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.UserInfoVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 用户信息转换器
 * @author sgchen
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysUserInfoVoConvert extends BaseMapper<UserInfoVo, RemoteUserInfoVo> {

}
