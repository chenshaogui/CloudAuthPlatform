package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.template;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.sso.strategy.SaSsoClientStrategy;
import cn.dev33.satoken.util.SaResult;
import com.dtflys.forest.Forest;
import io.gitee.chenshaogui.cloudauth.securitycloud.oauth.BrowserIdContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 SSO Client 策略：
 * <p>
 * 1. 覆盖 {@code sendRequest}：sa-token 默认实现走 {@code SaHttpTemplate.get(url)}，
 * 不支持自定义 header；这里改用 Forest（已在 SDK pom 中）发 GET，自动注入 X-Browser-Id。
 * </p>
 * <p>
 * 2. 新增 POST 版 {@code requestAsSaResult}：向 auth server 推送业务消息时使用，
 * 同样自动带上 X-Browser-Id header。
 * </p>
 */
public class RemoteSaSsoClientStrategy extends SaSsoClientStrategy {

    public RemoteSaSsoClientStrategy() {
        // 覆盖默认 sendRequest：Forest GET + X-Browser-Id header
        sendRequest = url -> Forest.get(url)
                .addHeader(BrowserIdContext.HEADER_NAME, BrowserIdContext.current())
                .executeAsString();
    }

    /**
     * 发送 POST 请求到 auth server，自动带上 X-Browser-Id header。
     *
     * @param url    请求地址
     * @param params form 表单参数
     * @return 响应结果
     */
    public SaResult requestAsSaResult(String url, Map<String, Object> params) {
        // Map<String,Object> → Map<String,String>（Forest form 期望 String）
        Map<String, String> formMap = new HashMap<>();
        if (params != null) {
            params.forEach((k, v) -> formMap.put(k, v == null ? "" : String.valueOf(v)));
        }

        String body = Forest.post(url)
                .addHeader(BrowserIdContext.HEADER_NAME, BrowserIdContext.current())
                .addBody(formMap, "application/x-www-form-urlencoded")
                .executeAsString();

        Map<String, Object> map = SaManager.getSaJsonTemplate().jsonToMap(body);
        return new SaResult(map);
    }

}
