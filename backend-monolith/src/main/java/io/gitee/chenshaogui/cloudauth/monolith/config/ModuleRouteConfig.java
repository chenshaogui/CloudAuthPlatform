package io.gitee.chenshaogui.cloudauth.monolith.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.function.Predicate;

@Configuration
public class ModuleRouteConfig implements WebMvcConfigurer {

    private static final String MONITOR_PACKAGE = "io.gitee.chenshaogui.cloudauth.system.controller.monitor.";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer
            .addPathPrefix("/auth", basePackage("io.gitee.chenshaogui.cloudauth.auth."))
            .addPathPrefix("/system", controller ->
                basePackage("io.gitee.chenshaogui.cloudauth.system.").test(controller)
                    && !basePackage(MONITOR_PACKAGE).test(controller))
            .addPathPrefix("/monitor", basePackage(MONITOR_PACKAGE))
            .addPathPrefix("/resource", basePackage("io.gitee.chenshaogui.cloudauth.resource."));
    }

    private static Predicate<Class<?>> basePackage(String prefix) {
        return controller -> controller.getName().startsWith(prefix);
    }
}
