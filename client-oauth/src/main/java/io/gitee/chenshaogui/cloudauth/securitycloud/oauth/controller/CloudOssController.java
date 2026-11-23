package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.controller;

import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.text.CharSequenceUtil;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudOssUploadVo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean.CloudOssVo;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils.CloudPlatformHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 对象存储（sys_oss）客户端 Controller（默认实现，业务方可自行覆盖）
 *
 * <p>提供与统一鉴权平台对接的文件上传 / 文件 URL 解析端点，包括：</p>
 * <ul>
 *     <li>通用文件上传（前端 form-data 单文件上传）</li>
 *     <li>根据 ossId 解析单文件 URL</li>
 *     <li>根据 ossId 串解析多文件 URL（逗号分隔）</li>
 *     <li>根据 ossId 串查询文件列表</li>
 * </ul>
 *
 * <p>类级别 <b>不标注</b> {@code @SaIgnore}，与 {@link CloudAuthCenterController} 一致：
 * 由 SDK 全局鉴权过滤器对端点进行登录校验，<b>仅在用户成功登录后方能调用</b>，
 * 未登录访问将返回 401。</p>
 *
 * <p>业务方若需要自定义返回结构（例如包装成项目内部的 {@code R<T>}）或扩展 Swagger 注解，
 * 可在自身工程中定义同名 Controller，Spring 会优先加载业务方的 Bean。</p>
 *
 * <p><b>多模块注册开关</b>：本类标注
 * {@code @ConditionalOnProperty(prefix = "sa-token", name = "expose-controller",
 * havingValue = "true", matchIfMissing = true)}，业务方可在不需要暴露对象存储接口的
 * 微服务模块（如 infra / openapi / app）显式配置
 * {@code sa-token.expose-controller: false} 来关闭本 Controller 注册，
 * 避免多模块重复暴露同一组文件接口引发的"上传 / 解析 数据来源不一致"问题。
 * 微服务架构下推荐 <b>只在需要文件能力的模块</b> 开启，其他模块关闭。</p>
 *
 * @author sgchen
 * @since 1.3.0 增加文件存储（sys_oss）模块
 */
@Slf4j
@RestController
@RequestMapping("/authCenter/oss")
@ConditionalOnProperty(prefix = "sa-token", name = "expose-controller", havingValue = "true", matchIfMissing = true)
public class CloudOssController {

    /**
     * 上传文件（form-data 单文件）
     *
     * <p>通过 sa-token push message 通道把文件字节（base64）推送到授权中心，
     * 授权中心写入 {@code sys_oss} 表并返回上传后的 URL / ossId / fileName。</p>
     *
     * <p>返回 {@code SaResult.data(CloudOssUploadVo)}，业务方应保存 {@code ossId}
     * 用于后续关联业务数据。</p>
     *
     * @param file 表单文件（字段名必须为 {@code file}）
     * @return SaResult.data(CloudOssUploadVo)
     */
    @PostMapping("/upload")
    public SaResult upload(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return SaResult.error("上传文件不能为空");
        }
        try {
            CloudOssUploadVo vo = CloudPlatformHelper.uploadOss(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType());
            return SaResult.data(vo);
        } catch (IOException e) {
            log.warn("[oss] 读取上传文件失败: {}", e.getMessage());
            return SaResult.error("读取上传文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[oss] 上传文件失败", e);
            return SaResult.error("上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 根据 ossId 获取单个文件 URL
     *
     * <p>按 ossId 在共享远程缓存中查询（参考数据档 300s TTL），命中时直接返回本地缓存，
     * 避免每次渲染都打远程。</p>
     *
     * @param ossId 文件主键
     * @return SaResult.data(url)
     */
    @GetMapping("/getUrlById")
    public SaResult getUrlById(@RequestParam("ossId") Long ossId) {
        if (ossId == null) {
            return SaResult.error("ossId 不能为空");
        }
        String url = CloudPlatformHelper.getOssUrlById(ossId);
        return SaResult.data(url);
    }

    /**
     * 根据 ossId 串（逗号分隔）获取多个文件 URL
     *
     * <p>服务端按入参顺序返回（与 {@code ossIds} 一一对应），未找到时该位为空串。
     * 适用于表单回显 / 列表渲染场景一次性拉取多个文件 URL。</p>
     *
     * @param ossIds 逗号分隔的 ossId 串，如 {@code "1,2,3"}
     * @return SaResult.data(urls) —— 逗号分隔的 URL 串
     */
    @GetMapping("/getUrlByIds")
    public SaResult getUrlByIds(@RequestParam("ossIds") String ossIds) {
        if (CharSequenceUtil.isBlank(ossIds)) {
            return SaResult.data("");
        }
        String urls = CloudPlatformHelper.getOssUrlByIds(ossIds);
        return SaResult.data(urls);
    }

    /**
     * 根据 ossId 串查询文件完整信息列表
     *
     * <p>返回的 {@link CloudOssVo} 列表按入参顺序排列（未找到时该位缺失），
     * 适用于需要展示文件名 / 后缀 / 上传人 / 创建时间等明细的场景。</p>
     *
     * @param ossIds 逗号分隔的 ossId 串
     * @return SaResult.data(List&lt;CloudOssVo&gt;)
     */
    @GetMapping("/listByIds")
    public SaResult listByIds(@RequestParam("ossIds") String ossIds) {
        if (CharSequenceUtil.isBlank(ossIds)) {
            return SaResult.data(new ArrayList<>());
        }
        List<CloudOssVo> list = CloudPlatformHelper.getOssByIds(ossIds);
        return SaResult.data(list);
    }

}
