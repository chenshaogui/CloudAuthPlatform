package io.gitee.chenshaogui.cloudauth.casm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class CustomBean {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(ProjectProperties projectProperties) {
        log.info("SnowflakeProperties is", projectProperties.getSnowflakeProperties());
        return new SnowflakeIdWorker(projectProperties.getSnowflakeProperties().getWorkerId(), projectProperties.getSnowflakeProperties().getDataCenterId());
    }
}
