package io.gitee.chenshaogui.cloudauth.casm;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.MD5;
import com.alibaba.fastjson.JSONObject;
import io.gitee.chenshaogui.cloudauth.casm.aspect.AuthAspect;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.KeyInfoReqBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoRspVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.casm.service.IHSMService;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysClientService;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysKeyInfoService;
import io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm.SMHelper;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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
    private IHSMService hsmService;

    @Resource
    private ISysKeyInfoService sysKeyInfoService;

    @Resource
    private ISysClientService sysClientService;
    @Test
    public void testQueryClient() throws Exception {
        try {
            SysClientVo sysClientVo = sysClientService.queryById(1996405759187423233L);
            log.info("sysClientVo:{}",sysClientVo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testApplyKey() throws Exception {
        try {
            KeyInfoReqBo reqBo = new KeyInfoReqBo();
            reqBo.setClientId("e5cd7e4891bf95d1d19206ce24a7b32e");
            reqBo.setKeyType("PEK");
            reqBo.setKeyAlthm("SM2");
            KeyInfoRspVo keyInfoVo = sysKeyInfoService.applyKey(reqBo);
            log.info("keyInfoVo:{}",keyInfoVo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testUseKey() throws Exception {
        try {
            KeyInfoReqBo reqBo = new KeyInfoReqBo();
            reqBo.setClientId("8967747377581dacaf78294062d527df");
            reqBo.setKeyType("PEK");
            reqBo.setKeyAlthm("SM2");
            reqBo.setKeyId("1448018038990311424");
            KeyInfoRspVo keyInfoVo = sysKeyInfoService.useKey(reqBo);
            log.info("keyInfoVo:{}",keyInfoVo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void applyWebTest() throws Exception {
        KeyInfoReqBo reqBo = new KeyInfoReqBo();
        reqBo.setClientId("8967747377581dacaf78294062d527df");
        reqBo.setKeyType("PEK");
        reqBo.setKeyAlthm("SM2");
        String reqString  = JSONObject.toJSONString(reqBo);
        String content = mockMvc.perform(MockMvcRequestBuilders.post("/casm/interior/key/apply")
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(reqString))
            .andReturn().getResponse().getContentAsString();
        System.out.println(content);
    }


    @Test
    public void useWebTest() throws Exception {
        KeyInfoReqBo reqBo = new KeyInfoReqBo();
        reqBo.setClientId("e5cd7e4891bf95d1d19206ce24a7b32e");
        reqBo.setKeyType("PEK");
        reqBo.setKeyAlthm("SM2");
        reqBo.setKeyId("1448246419363205120");
        String reqString  = JSONObject.toJSONString(reqBo);
        String content = mockMvc.perform(MockMvcRequestBuilders.post("/hsm/interior/key/use")
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(reqString))
            .andReturn().getResponse().getContentAsString();
        System.out.println(content);
    }

    @Test
    public void testRequest() throws Exception {

        AuthAspect authAspect = SpringUtils.getBean(AuthAspect.class);
        log.info("authAspect:{}",authAspect);
        JSONObject params = new JSONObject();
        params.put("clientId", "8967747377581dacaf78294062d527df");
        params.put("keyType", "PEK");
        params.put("keyId","1448238679974744064");
        String aesData = SMHelper.sm4Encrypt("iRixejKAL4VgbVYLICJVQMXz6qxqt-lcGUyo8_muLd8", params.toJSONString());
        String strTemplate = "clientId=%s&data=%s&signKey=%s&timestamp=%s&nonceStr=%s";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonceStr = IdUtil.fastUUID();
        String waitSignStr = String.format(strTemplate, "8967747377581dacaf78294062d527df", aesData, "a66ff4616a7a4c5ba77bb87519e73659", timestamp, nonceStr);
        String sign = SMHelper.sm3Hash(waitSignStr);
        JSONObject reqBo = new JSONObject();
        reqBo.put("clientId", "8967747377581dacaf78294062d527df");
        reqBo.put("data", aesData);
        reqBo.put("sign", sign);
        reqBo.put("timestamp", timestamp);
        reqBo.put("nonceStr", nonceStr);

        log.info("reqBo:{}",reqBo);

        String reqString  = JSONObject.toJSONString(reqBo);
        String content = mockMvc.perform(MockMvcRequestBuilders.post("/hsm/interior/key/use")
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(reqString))
            .andReturn().getResponse().getContentAsString();
        System.out.println(content);
    }
}
