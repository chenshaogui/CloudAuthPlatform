package io.gitee.chenshaogui.cloudauth.common.sms.config;

import io.gitee.chenshaogui.cloudauth.common.redis.config.RedisConfiguration;
import io.gitee.chenshaogui.cloudauth.common.sms.core.dao.PlusSmsDao;
import io.gitee.chenshaogui.cloudauth.common.sms.handler.SmsExceptionHandler;
import org.dromara.sms4j.api.dao.SmsDao;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 短信配置类
 *
 * @author Feng
 */
@AutoConfiguration(after = {RedisConfiguration.class})
public class SmsAutoConfiguration {

    @Primary
    @Bean
    public SmsDao smsDao() {
        return new PlusSmsDao();
    }

    /**
     * 异常处理器
     */
    @Bean
    public SmsExceptionHandler smsExceptionHandler() {
        return new SmsExceptionHandler();
    }

}
