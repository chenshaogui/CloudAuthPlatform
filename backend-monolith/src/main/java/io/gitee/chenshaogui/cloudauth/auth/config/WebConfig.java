package io.gitee.chenshaogui.cloudauth.auth.config;

import io.gitee.chenshaogui.cloudauth.auth.filter.BrowserIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * auth server Web 配置
 */
@Configuration
public class WebConfig {

    /**
     * 注册 BrowserIdFilter：为每个浏览器分配稳定的 browserId（cookie 持久化），
     * 配合 sa-token token extra 中的 browserId 实现"同浏览器多 tab 一起登出"。
     * <p>
     * 优先级最高，确保在 sa-token 过滤器之前执行。
     * </p>
     */
    @Bean
    public FilterRegistrationBean<BrowserIdFilter> browserIdFilter() {
        FilterRegistrationBean<BrowserIdFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new BrowserIdFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}