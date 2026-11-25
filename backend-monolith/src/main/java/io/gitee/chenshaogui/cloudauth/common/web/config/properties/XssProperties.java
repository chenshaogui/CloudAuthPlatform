package io.gitee.chenshaogui.cloudauth.common.web.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * XSS跨站脚本配置
 *
 * @author Lion Li
 */
@Data

@ConfigurationProperties(prefix = "xss")
public class XssProperties {

    /**
     * Xss开关
     */
    private Boolean enabled;

    /**
     * 排除路径
     */
    private List<String> excludeUrls = new ArrayList<>();

}
