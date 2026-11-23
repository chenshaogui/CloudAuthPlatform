package io.gitee.chenshaogui.cloudauth.auth.template;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.message.handle.server.SaSsoMessageCheckTicketHandle;
import cn.dev33.satoken.sso.model.TicketModel;
import cn.dev33.satoken.sso.name.ParamName;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;

public class RemoteSaSsoMessageCheckTicketHandle extends SaSsoMessageCheckTicketHandle {

    public Object handle(SaSsoTemplate ssoTemplate, SaSsoMessage message) {
        SaSsoServerTemplate ssoServerTemplate = (SaSsoServerTemplate)ssoTemplate;
        ParamName paramName = ssoServerTemplate.paramName;
        StpLogic stpLogic = ssoServerTemplate.getStpLogicOrGlobal();
        String client = message.getString(paramName.client);
        String ticket = message.getValueNotNull(paramName.ticket).toString();
        String sloCallback = message.getString(paramName.ssoLogoutCall);
        TicketModel ticketModel = ssoServerTemplate.checkTicketParamAndDelete(ticket, client);
        Object loginId = ticketModel.getLoginId();
        ssoServerTemplate.registerSloCallbackUrl(loginId, client, sloCallback);
        SaResult result = SaResult.ok();
        String clientLoginToken = StpUtil.getTokenValueByLoginId(loginId);
        LoginUser loginUser = LoginHelper.getLoginUser(clientLoginToken);
        result.setData(loginUser);
        result.set(paramName.loginId, loginId);
        result.set(paramName.tokenValue, ticketModel.getTokenValue());
        result.set(paramName.deviceId, stpLogic.getLoginDeviceIdByToken(ticketModel.getTokenValue()));
        result.set(paramName.remainTokenTimeout, stpLogic.getTokenTimeout(ticketModel.getTokenValue()));
        result.set(paramName.remainSessionTimeout, stpLogic.getTokenActiveTimeoutByToken(ticketModel.getTokenValue()));
        result = ssoServerTemplate.strategy.checkTicketAppendData.apply(loginId, result);
        return result;
    }

}
