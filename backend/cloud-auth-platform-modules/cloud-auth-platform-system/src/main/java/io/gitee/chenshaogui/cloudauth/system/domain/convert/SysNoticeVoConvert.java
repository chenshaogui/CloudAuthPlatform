package io.gitee.chenshaogui.cloudauth.system.domain.convert;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteNoticeVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysNoticeVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 通知公告转换器
 *
 * <p>将内部 {@link SysNoticeVo} 映射为 Dubbo 对外 VO {@link RemoteNoticeVo}，
 * 供 {@code RemoteNoticeServiceImpl} 经 {@code MapstructUtils.convert} 调用。</p>
 *
 * <p>字段完全一致，无需额外 {@code @Mapping} 配置；与同包的
 * {@code SysConfigVoConvert} / {@code SysDictTypeVoConvert} / {@code SysDictDataVoConvert} 一致。</p>
 *
 * @author sgchen
 * @since 1.3.0 通知公告（sys_notice）Dubbo 转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysNoticeVoConvert extends BaseMapper<SysNoticeVo, RemoteNoticeVo> {

}
