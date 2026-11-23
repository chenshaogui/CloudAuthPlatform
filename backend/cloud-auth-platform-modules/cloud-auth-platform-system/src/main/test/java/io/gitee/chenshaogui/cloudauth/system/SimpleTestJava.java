package io.gitee.chenshaogui.cloudauth.system;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "jasypt.encryptor.password=etc@2023!@#"
})
public class SimpleTestJava extends BaseTest {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Test
    public void splitMoney() throws Exception {
        JSONObject itemContent = new JSONObject().fluentPut("inOpTime", "2024-02-08 15:49:00");
        Date firstFlowAddTime = new Date();
        long inOpTime = DateUtil.between(firstFlowAddTime, itemContent.getDate("inOpTime"), DateUnit.DAY);
        System.out.println(inOpTime);
    }

    /**
     * 修改用户姓名
     */
    @Test
    public void testUserName() {
        System.out.println(123);
    }

}
