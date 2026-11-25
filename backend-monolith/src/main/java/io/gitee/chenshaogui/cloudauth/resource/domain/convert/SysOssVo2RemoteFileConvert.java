package io.gitee.chenshaogui.cloudauth.resource.domain.convert;

import io.gitee.chenshaogui.cloudauth.resource.api.domain.RemoteFile;
import io.gitee.chenshaogui.cloudauth.resource.domain.vo.SysOssVo;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * OSS 对象存储 Dubbo 转换器（{@link SysOssVo} → {@link RemoteFile}）
 *
 * <p>供 {@code io.gitee.chenshaogui.cloudauth.resource.dubbo.RemoteFileServiceImpl.selectByIds}
 * 经 {@code MapstructUtils.convert(SysOssVo, RemoteFile.class)} 调用。</p>
 *
 * <p>字段名差异说明（{@code RemoteFile} 比 {@code SysOssVo} 少 {@code createTime / createBy /
 * createByName / service}，多一个 {@code name} 字段对应 {@code fileName}）：</p>
 * <ul>
 *     <li>{@code RemoteFile.name} ← {@code SysOssVo.fileName}：
 *         {@code BaseMapper} 的方法参数名固定为 {@code arg0/arg1}，无法用 {@code @Mapping(source=...)}
 *         覆盖；改在 {@code RemoteFileServiceImpl.selectByIds} 转换后手动 {@code setName} 补齐。</li>
 *     <li>{@code unmappedTargetPolicy = IGNORE}：{@code RemoteFile} 上没有的字段（{@code createBy} 等）忽略，不影响 Dubbo 透传</li>
 * </ul>
 *
 * @author sgchen
 * @since 1.3.0 文件存储（sys_oss）Dubbo 转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysOssVo2RemoteFileConvert extends BaseMapper<SysOssVo, RemoteFile> {

}
