package io.gitee.chenshaogui.cloudauth.auth.template;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.sign.SaSignManager;
import cn.dev33.satoken.sign.config.SaSignConfig;
import cn.dev33.satoken.sign.template.SaSignTemplate;
import cn.dev33.satoken.sso.config.SaSsoClientModel;
import cn.dev33.satoken.sso.error.SaSsoErrorCode;
import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.dev33.satoken.sso.message.handle.server.SaSsoMessageSignoutHandle;
import cn.dev33.satoken.sso.model.TicketModel;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheConstants;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteClientService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.SysUserOnline;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteClientVo;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SSO 模板方法类 （Server端）
 *
 * @author click33
 * @since 1.38.0
 */
@Component
public class RemoteSsoServerTemplate extends SaSsoServerTemplate {

    public RemoteSsoServerTemplate() {
        super.messageHolder.addHandle(new RemoteSaSsoMessageCheckTicketHandle());
        super.messageHolder.addHandle(new SaSsoMessageSignoutHandle());
    }

    // ---------------------- Ticket 操作 ----------------------

    /**
     * 保存 Ticket
     * @param ticketModel /
     */
    public void saveTicket(TicketModel ticketModel) {
        RemoteClientService clientService = SpringUtils.getBean(RemoteClientService.class);
        RemoteClientVo remoteClientVo = clientService.queryByClientId(ticketModel.getClient());
        long ticketTimeout = remoteClientVo.getActiveTimeout();
        SaManager.getSaTokenDao().setObject(splicingTicketModelSaveKey(ticketModel.getTicket()), ticketModel, ticketTimeout);
    }

    /**
     * 保存 Ticket 索引 （id 反查 ticket）
     *
     * @param client 应用端
     * @param ticket ticket码
     * @param loginId 账号id
     */
    public void saveTicketIndex(String client, Object loginId, String ticket) {
        RemoteClientService clientService = SpringUtils.getBean(RemoteClientService.class);
        RemoteClientVo remoteClientVo = clientService.queryByClientId(client);
        long ticketTimeout = remoteClientVo.getActiveTimeout();
        SaManager.getSaTokenDao().set(splicingTicketIndexKey(client, loginId), String.valueOf(ticket), ticketTimeout);
    }


    // ---------------------- Client 信息获取 ----------------------

    /**
     * 根据 clientId 和 loginId 过滤 SysUserOnline，返回最后一个（无则返回 null）
     */
    private List<SysUserOnline> getOnlineInfoByClient(String clientId, String loginId) {
        // 1. 入参校验：clientId/ loginId
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(loginId)) {
            return Collections.emptyList();
        }

        // 2. 根据 loginId 获取该用户的所有 Token 列表
        List<String> tokenIds;
        try {
            tokenIds = StpUtil.getTokenValueListByLoginId(loginId);
        } catch (Exception e) {
            // 无登录态/Token 列表为空时，返回 null
            return Collections.emptyList();
        }

        // 3. 过滤：有效 Token → 转换为 SysUserOnline → 按 clientId 匹配 → 收集列表
        List<SysUserOnline> userOnlineList = tokenIds.stream()
            // 过滤：Token 未过期（-1 表示永不过期，>=0 表示剩余有效期）
            .filter(token -> StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) >= -1)
            .map(token -> {
                LoginUser loginUser = LoginHelper.getLoginUser(token);
                if(loginUser == null){
                    return null;
                }
                return TenantHelper.dynamic(loginUser.getTenantId(), () -> {
                    String redisKey = CacheConstants.ONLINE_TOKEN_KEY + token;
                    return (SysUserOnline) RedisUtils.getCacheObject(redisKey);
                });
            })
            .filter(Objects::nonNull)
            .filter(online -> clientId.equals(online.getClientKey()))
            .toList();
       return userOnlineList;
    }

    /**
     * 获取所有 Client
     *
     * @return /
     */
    public List<SaSsoClientModel> getClients() {
//        SaRequest req = SaHolder.getRequest();
//        String client = req.getParam(paramName.client);
//        if(!StringUtils.hasText(client) && StpUtil.isLogin()){
//            client = LoginHelper.getClientId();
//        }
//        String loginId = req.getParam(paramName.loginId);
//        if(!StringUtils.hasText(loginId) && StpUtil.isLogin()){
//            loginId = StpUtil.getLoginIdAsString();
//        }
//        RemoteClientService clientService = SpringUtils.getBean(RemoteClientService.class);
//        List<SysUserOnline> sysUserOnlineList = getOnlineInfoByClient(client, loginId);
//        List<SaSsoClientModel> saSsoClientModels = new ArrayList<>();
//        for (SysUserOnline online : sysUserOnlineList) {
//            RemoteClientVo remoteClientVo = clientService.queryByClientId(online.getClientKey());
//            SaSsoClientModel saSsoClientModel = new SaSsoClientModel();
//            saSsoClientModel.setClient(remoteClientVo.getClientId());
//            saSsoClientModel.setSecretKey(remoteClientVo.getClientSecret());
//            saSsoClientModel.setAllowUrl(remoteClientVo.getAllowUrl());
//            saSsoClientModel.setIsPush(true);
//            saSsoClientModels.add(saSsoClientModel);
//        }
//        return saSsoClientModels;
        return Collections.emptyList();
    }

    /**
     * 获取应用信息，无效 client 返回 null
     *
     * @param client /
     * @return /
     */
    public SaSsoClientModel getClient(String client) {
        RemoteClientService clientService = SpringUtils.getBean(RemoteClientService.class);
        RemoteClientVo remoteClientVo = clientService.queryByClientId(client);
        SaSsoClientModel saSsoClientModel = new SaSsoClientModel();
        saSsoClientModel.setClient(remoteClientVo.getClientId());
        saSsoClientModel.setSecretKey(remoteClientVo.getClientSecret());
        saSsoClientModel.setAllowUrl(remoteClientVo.getAllowUrl());
        return saSsoClientModel;
    }

    /**
     * 获取应用信息，无效 client 则抛出异常
     *
     * @param client /
     * @return /
     */
    public SaSsoClientModel getClientNotNull(String client) {
        if(SaFoxUtil.isEmpty(client)) {
            throw new SaSsoException("client 标识不可为空");
        } else {
            SaSsoClientModel scm = getClient(client);
            if(scm == null) {
                throw new SaSsoException("未能获取应用信息，client=" + client).setCode(SaSsoErrorCode.CODE_30013);
            }
            return scm;
        }
    }


    // ------------------- 重定向 URL 构建与校验 -------------------

    // 保留 buildRedirectUrl、encodeBackParam、checkRedirectUrl、checkAllowUrlList、checkAllowUrlListStaticMethod 方法（与父类有差异或必要实现）


    // ------------------- Bean 获取 -------------------

    /**
     * 获取底层使用的 API 签名对象
     * @param client 指定客户端标识，填 null 代表获取默认的
     * @return /
     */
    public SaSignTemplate getSignTemplate(String client) {
        SaSignConfig signConfig = SaSignManager.getSaSignTemplate().getSignConfigOrGlobal().copy();
        SaSsoClientModel clientModel = getClientNotNull(client);

        // 使用 secretKey 的优先级：client 单独配置 > SSO 模块全局配置 > sign 模块默认配置
        String secretKey = clientModel.getSecretKey();
        signConfig.setSecretKey(secretKey);

        return new SaSignTemplate(signConfig);
    }


    // ------------------- 返回相应key -------------------

    // 保留 splicingTicketModelSaveKey、splicingTicketIndexKey 方法（与父类有差异或必要实现）

}
