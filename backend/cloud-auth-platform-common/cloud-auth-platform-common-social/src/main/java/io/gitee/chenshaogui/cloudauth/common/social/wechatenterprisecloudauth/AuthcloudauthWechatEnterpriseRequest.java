package io.gitee.chenshaogui.cloudauth.common.social.wechatenterprisecloudauth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.enums.AuthResponseStatus;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.log.Log;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.GlobalAuthUtils;
import me.zhyd.oauth.utils.StringUtils;
import me.zhyd.oauth.utils.UrlBuilder;
import me.zhyd.oauth.utils.UuidUtils;

/**
 * @author sgchen
 */
@Slf4j
public class AuthcloudauthWechatEnterpriseRequest implements AuthRequest {

    public static final String SERVER_URL = SpringUtils.getProperty("justauth.type.wechat_enterprise_cloudauth.server-url");

    protected AuthConfig config;

    protected AuthStateCache authStateCache;

    public AuthcloudauthWechatEnterpriseRequest(AuthConfig config, AuthStateCache authStateCache){
        this.config = config;
        this.authStateCache = authStateCache;
    }

    public String authorize(String state) {
        return UrlBuilder.fromBaseUrl(SERVER_URL+"/wechat/qrcode")
            .queryParam("appId", this.config.getClientId())
            .queryParam("state", this.getRealState(state))
            .build();
    }

    @Override
    public AuthToken getAccessToken(AuthCallback authCallback) {
        return null;
    }

    @Override
    public AuthUser getUserInfo(AuthToken authToken) {
        String newUrl = SERVER_URL+"/prod-api/wechat/getUserInfo";
        JSONObject reqParam = new JSONObject();
        reqParam.put("UserId", authToken.getOpenId());
        reqParam.put("appId", config.getClientId());
        HttpRequest request = HttpRequest.post(newUrl)
            .body(reqParam.toJSONString());
        HttpResponse response = request.execute();
        String result = response.body();
        log.info("获取到企微用户信息：{}", result);
        JSONObject resultObj = JSONObject.parseObject(result);
        int code = resultObj.getIntValue("code");
        if (code != 200) {
            log.error("[企微获取用户信息] 错误。uri:{};param:{};res:{}", newUrl, reqParam.toJSONString(), result);
            String msg = resultObj.getString("msg");
            if (StrUtil.isBlank(msg)) {
                msg = "[企微获取用户信息] 错误信息为空";
            }
            if (msg.contains("获取用户的详细信息错误")){
                msg = "[企微获取用户信息] 获取用户的详细信息错误,请先检查是否登录企业微信";
            }
            throw new AuthException(msg);
        }
        JSONObject resultData = resultObj.getJSONObject("data");
        return AuthUser.builder()
            .uuid(resultData.getString("userid"))
            .username(resultData.getString("name"))
            .nickname(resultData.getString("name"))
            .build();
    }

    @Override
    public AuthResponse<AuthUser> login(AuthCallback authCallback) {
        try {
            String newUrl = SERVER_URL+"/prod-api/wechat/getLoginUserId";
            JSONObject reqParam = new JSONObject();
            reqParam.put("code", authCallback.getCode());
            reqParam.put("appId", config.getClientId());
            HttpRequest request = HttpRequest.post(newUrl)
                .body(reqParam.toJSONString());
            HttpResponse response = request.execute();
            String result = response.body();
            log.info("根据授权码获取企微用户信息：{}", result);
            JSONObject resultObj = JSONObject.parseObject(result);
            int code = resultObj.getIntValue("code");
            if (code != 200) {
                log.error("[企微授权登录] 错误。uri:{};param:{};res:{}", newUrl, reqParam.toJSONString(), result);
                String msg = resultObj.getString("msg");
                if (StrUtil.isBlank(msg)) {
                    msg = "[企微授权登录] 错误信息为空";
                }
                if (msg.contains("获取用户的详细信息错误")){
                    msg = "[企微授权登录] 获取用户的详细信息错误,请先检查是否登录企业微信";
                }
                throw new AuthException(msg);
            }
            JSONObject resultData = resultObj.getJSONObject("data");
            AuthToken authToken = new AuthToken();
            authToken.setAccessToken(authCallback.getCode());
            AuthUser authUser = AuthUser.builder()
                .uuid(resultData.getString("UserId"))
                .token(authToken)
                .source("wechat_enterprise_cloudauth")
                .build();

            return AuthResponse.<AuthUser>builder().
                code(AuthResponseStatus.SUCCESS.getCode())
                .data(authUser).build();
        } catch (Exception e) {
            Log.error("Failed to login with oauth authorization.", e);
            throw new AuthException(e.getMessage());
        }
    }

    protected String getRealState(String state) {
        if (StringUtils.isEmpty(state)) {
            state = UuidUtils.getUUID();
        }

        this.authStateCache.cache(state, state);
        return state;
    }

}
