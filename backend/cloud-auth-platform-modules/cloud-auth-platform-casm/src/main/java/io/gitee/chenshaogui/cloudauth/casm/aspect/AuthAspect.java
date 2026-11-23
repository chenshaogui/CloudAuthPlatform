package io.gitee.chenshaogui.cloudauth.casm.aspect;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.MD5;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.RequestBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysClientService;
import io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm.SMHelper;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ServletUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@Aspect
public class AuthAspect {

    @Resource
    private ISysClientService sysClientService;


    private static final ThreadLocal<String> threadLocal = new ThreadLocal<>();

    /**
     * 定制一个环绕通知
     *
     * @param proceedingJoinPoint
     */
    @Around(value = "@annotation(auth)")
    public Object verify(ProceedingJoinPoint proceedingJoinPoint, Auth auth) {
        try {
            Object[] args = proceedingJoinPoint.getArgs();
            RequestBo requestData = (RequestBo) args[0];
            threadLocal.set(requestData.getClientId());
            args[0] = doVerify(requestData, auth);
            R result = (R) proceedingJoinPoint.proceed(args);
            return encrypt(result);
        } catch (Throwable throwable) {
            log.error("AuthAspect error :", throwable);
            throw new ServiceException("执行目标方法异常，" + throwable.getMessage(), 105);
        } finally {
            threadLocal.remove();
        }
    }

    public RequestBo doVerify(RequestBo requestData, Auth auth) throws Exception {
        String data = requestData.getData();
        String clientId = requestData.getClientId();
        String sign = requestData.getSign();
        Long timestamp = requestData.getTimestamp();
        String nonceStr = requestData.getNonceStr();
        if (ObjectUtil.isEmpty(data)) {
            throw new ServiceException("缺少data参数");
        }
        if (StringUtils.isEmpty(clientId)) {
            throw new ServiceException("缺少clientId参数");
        }
        if (StringUtils.isEmpty(sign)) {
            throw new ServiceException("缺少sign参数");
        }
        if (ObjectUtil.isEmpty(timestamp)) {
            throw new ServiceException("缺少timestamp参数");
        }
        if (StringUtils.isEmpty(nonceStr)) {
            throw new ServiceException("缺少nonceStr参数");
        }
        // 请求的地址
        SysClientVo sysClientVo = sysClientService.queryByClientId(clientId);
        if (sysClientVo == null) {
            throw new ServiceException("未找到该应用");
        }
        if (!"0".equals(sysClientVo.getStatus())) {
            throw new ServiceException("该应用已停用");
        }
        String ip = ServletUtils.getClientIP();
        log.info("请求的IP为：{}", ip);
        if (sysClientVo.getAppIpList() != null &&
            !sysClientVo.getAppIpList().contains(ip)) {
            throw new ServiceException("非法请求");
        }
        String clientSecret = sysClientVo.getClientSecret();
        String signKey = sysClientVo.getSignKey();

        if (StringUtils.isEmpty(clientSecret)) {
            throw new ServiceException("应用未配置加密Key");
        }
        if (StringUtils.isEmpty(signKey)) {
            throw new ServiceException("应用未配置签名Key");
        }

        String strTemplate = "clientId=%s&data=%s&signKey=%s&timestamp=%s&nonceStr=%s";
        String waitSignStr = String.format(strTemplate, clientId, data, signKey, timestamp, nonceStr);
        if ("SM4".equalsIgnoreCase(auth.mode())) {
            // 国产密码算法处理
            String smSign = SMHelper.sm3Hash(waitSignStr);
            // log.info("待签名字符串为：{}，签名类型：{}，签名为：{}", waitSignStr, "SM3", smSign);
            if (!smSign.equalsIgnoreCase(sign)) {
                throw new ServiceException("签名错误");
            }
            data = SMHelper.sm4Decrypt(clientSecret, data);
        }
        if (data == null) {
            throw new ServiceException("数据解密错误");
        }
        requestData.setData(data);
        return requestData;
    }


    public Object encrypt(R result) throws Exception {
        if (result == null || !R.isSuccess(result) || result.getData() == null) {
            return result;
        }
        try {
            Object data = result.getData();
            if (!ObjectUtil.isEmpty(data)) {
                String clientId = threadLocal.get();
                SysClientVo sysClientVo = sysClientService.queryByClientId(clientId);
                if (sysClientVo == null) {
                    throw new ServiceException("未找到该应用");
                }
                if (!"0".equals(sysClientVo.getStatus())) {
                    throw new ServiceException("该应用已停用");
                }
                String clientSecret = sysClientVo.getClientSecret();
                if (!StringUtils.isEmpty(clientSecret)) {
                    data = SMHelper.sm4Encrypt(clientSecret, JSON.toJSONString(data));
                }
            }
            return R.ok(data);
        } catch (ServiceException e) {
            log.error("数据加密 error :{}", e.getMessage(), e);
            return result;
        }

    }

    public static void main(String[] args) {
        JSONObject params = new JSONObject();
        params.put("mobilePhone", "18275371807");
        params.put("exchangeType", 1);
        String aesData = SMHelper.sm4Encrypt("iRixejKAL4VgbVYLICJVQMXz6qxqt-lcGUyo8_muLd8", params.toJSONString());
        String strTemplate = "appId=%s&data=%s&signKey=%s&timestamp=%s&nonceStr=%s";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonceStr = IdUtil.fastUUID();
        System.out.println("nonceStr:"+nonceStr);
        String waitSignStr = String.format(strTemplate, 1379490868580327424L, aesData, "a66ff4616a7a4c5ba77bb87519e73659", timestamp, nonceStr);
        String md5Sign = MD5.create().digestHex(waitSignStr);
        System.out.println("md5Sign"+md5Sign);
    }
}
