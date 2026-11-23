package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteRouterVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.RouterVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 菜单路由数据转换器
 *
 * @author Michelle.Chung
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysMenuRoutersVoConvert extends BaseMapper<RouterVo, RemoteRouterVo> {
}
