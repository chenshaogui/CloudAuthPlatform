package io.gitee.chenshaogui.cloudauth.resource.dubbo;

import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.json.utils.JsonUtils;
import io.gitee.chenshaogui.cloudauth.common.oss.core.OssClient;
import io.gitee.chenshaogui.cloudauth.common.oss.entity.UploadResult;
import io.gitee.chenshaogui.cloudauth.common.oss.factory.OssFactory;
import io.gitee.chenshaogui.cloudauth.resource.api.RemoteFileService;
import io.gitee.chenshaogui.cloudauth.resource.api.domain.RemoteFile;
import io.gitee.chenshaogui.cloudauth.resource.domain.SysOssExt;
import io.gitee.chenshaogui.cloudauth.resource.domain.bo.SysOssBo;
import io.gitee.chenshaogui.cloudauth.resource.domain.vo.SysOssVo;
import io.gitee.chenshaogui.cloudauth.resource.service.ISysOssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件请求处理
 *
 * @author Lion Li
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DubboService
public class RemoteFileServiceImpl implements RemoteFileService {

    private final ISysOssService sysOssService;

    /**
     * 文件上传请求
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public RemoteFile upload(String name, String originalFilename, String contentType, byte[] file) throws ServiceException {
        try {
            String suffix = StringUtils.substring(originalFilename, originalFilename.lastIndexOf("."), originalFilename.length());
            OssClient storage = OssFactory.instance();
            UploadResult uploadResult = storage.uploadSuffix(file, suffix, contentType);
            // 保存文件信息
            SysOssBo oss = new SysOssBo();
            oss.setUrl(uploadResult.getUrl());
            oss.setFileSuffix(suffix);
            oss.setFileName(uploadResult.getFilename());
            oss.setOriginalName(originalFilename);
            oss.setService(storage.getConfigKey());
            SysOssExt ext1 = new SysOssExt();
            ext1.setFileSize((long) file.length);
            String extStr = JsonUtils.toJsonString(ext1);
            oss.setExt1(extStr);
            sysOssService.insertByBo(oss);
            RemoteFile sysFile = new RemoteFile();
            sysFile.setOssId(oss.getOssId());
            sysFile.setName(uploadResult.getFilename());
            sysFile.setUrl(uploadResult.getUrl());
            sysFile.setOriginalName(originalFilename);
            sysFile.setFileSuffix(suffix);
            sysFile.setExt1(extStr);
            return sysFile;
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new ServiceException("上传文件失败");
        }
    }

    /**
     * 通过ossId查询对应的url
     *
     * @param ossIds ossId串逗号分隔
     * @return url串逗号分隔
     */
    @Override
    public String selectUrlByIds(String ossIds) {
        return sysOssService.selectUrlByIds(ossIds);
    }

    /**
     * 通过ossId查询列表
     *
     * <p>1.3.0 增强：转换后按索引补齐 {@code RemoteFile.name} 字段（取自 {@code SysOssVo.fileName}）。
     * mapstruct-plus 自动按字段名匹配，{@code RemoteFile.name} 与 {@code SysOssVo.fileName} 字段名不同
     * 无法识别；{@code SysOssVo2RemoteFileConvert} 暂不覆盖 {@code BaseMapper} 抽象方法，
     * 故此处手动补齐，避免下传 {@code null}。</p>
     *
     * @param ossIds ossId串逗号分隔
     * @return 列表
     */
    @Override
    public List<RemoteFile> selectByIds(String ossIds){
        List<SysOssVo> sysOssVos = sysOssService.listByIds(StringUtils.splitTo(ossIds, Convert::toLong));
        List<RemoteFile> remoteFiles = MapstructUtils.convert(sysOssVos, RemoteFile.class);
        if (remoteFiles != null && sysOssVos != null && remoteFiles.size() == sysOssVos.size()) {
            // 转换器按列表顺序逐项转换，按索引一一对应补 name
            for (int i = 0; i < remoteFiles.size(); i++) {
                RemoteFile rf = remoteFiles.get(i);
                if (rf != null) {
                    rf.setName(sysOssVos.get(i).getFileName());
                }
            }
        }
        return remoteFiles;
    }
}
