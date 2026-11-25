package io.gitee.chenshaogui.cloudauth.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "io.gitee.chenshaogui.cloudauth.monolith",
    "io.gitee.chenshaogui.cloudauth.auth",
    "io.gitee.chenshaogui.cloudauth.system",
    "io.gitee.chenshaogui.cloudauth.resource"
})
public class CloudAuthMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudAuthMonolithApplication.class, args);
    }
}
