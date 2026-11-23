package io.gitee.chenshaogui.cloudauth.casm.runner;

import io.gitee.chenshaogui.cloudauth.casm.service.IHSMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
@EnableScheduling
@RequiredArgsConstructor
// @ConditionalOnProperty(value = "casm.isSingle", havingValue = "1")
public class CommonRunnSchedule {

    public ExecutorService threadPool = Executors.newFixedThreadPool(50);

    private final IHSMService hsmService;

    @Scheduled(cron = "0/50 * * * * *")
    public void test() {
        log.info("CommonRunnSchedule测试时间：{}", new Date());
    }

    @Scheduled(cron = "0/30 * * * * *")
    public void expireKeys() {
        log.info("执行过期密钥处理：{}", LocalDateTime.now());

        threadPool.execute(() -> {
            try {
                hsmService.expireKeys();
            } catch (Exception e) {
                log.error("updateBlacklist error:", e);
            }
        });

    }

}
