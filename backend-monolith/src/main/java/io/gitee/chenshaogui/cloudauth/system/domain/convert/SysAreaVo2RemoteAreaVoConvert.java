package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteAreaVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysAreaVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 行政地区编码 Dubbo 转换器（{@link SysAreaVo} → {@link RemoteAreaVo}）
 *
 * <p>供 {@code io.gitee.chenshaogui.cloudauth.system.dubbo.RemoteAreaServiceImpl} 经
 * {@code MapstructUtils.convert(SysAreaVo, RemoteAreaVo.class)} 调用，
 * 避免手动 setter。</p>
 *
 * <p>同包另有 {@link SysAreaVoConvert}（{@code SysArea → SysAreaVo}，方向不同），
 * 不可复用，故单独声明本接口。</p>
 *
 * @author sgchen
 * @since 1.3.0 区域编码（sys_area）Dubbo 转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysAreaVo2RemoteAreaVoConvert extends BaseMapper<SysAreaVo, RemoteAreaVo> {

}
