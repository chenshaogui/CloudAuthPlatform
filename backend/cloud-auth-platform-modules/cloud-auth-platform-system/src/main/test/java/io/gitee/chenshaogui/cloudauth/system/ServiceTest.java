package io.gitee.chenshaogui.cloudauth.system;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysKeyInfoBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyInfoVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysKeyInfoService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;


@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class ServiceTest {

    protected MockMvc mockMvc;

    @Resource
    private WebApplicationContext webApplicationContext;

    @Before
    public void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Resource
    private ISysKeyInfoService sysKeyInfoService;

    @Test
    public void testQueryPage() throws Exception {
        try {
            SysKeyInfoBo bo = new SysKeyInfoBo();
            PageQuery pageQuery = new PageQuery(10, 1);
            TableDataInfo<SysKeyInfoVo> result = sysKeyInfoService.queryPageList(bo, pageQuery);
            log.info("result:{}", result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testApplyKey() throws Exception {
        try {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void applyWebTest() throws Exception {
    }
}
