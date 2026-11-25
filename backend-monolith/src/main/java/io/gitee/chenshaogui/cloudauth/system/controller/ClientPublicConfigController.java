package io.gitee.chenshaogui.cloudauth.system.controller;

import cn.hutool.core.util.ObjectUtil;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenu;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.ClientPublicConfigVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysMenuMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端公共配置（公开免登录）
 *
 * <p>供鉴权中心前端在用户进入登录/注册/重置密码页面时按 clientId 拉取，
 * 据此选择页面模板（template）并填充主题、文案、字段等配置。</p>
 *
 * <p>路径前缀：{@code /client/config/public}，部署时需将该路径加入网关白名单
 * （Nacos gateway-{profile}.yml 的 ignore-white.whites），参考
 * {@code /auth/code}、{@code /auth/login}、{@code /auth/register} 的处理方式。</p>
 *
 * @author CodeBuddy
 */
@Slf4j
@RestController
@RequestMapping("/client/config/public")
@RequiredArgsConstructor
public class ClientPublicConfigController {

    private final ISysClientService sysClientService;
    private final SysMenuMapper sysMenuMapper;

    /**
     * 按 clientId 拉取公共配置
     *
     * <p>异常一律返回 200 + 业务失败码（code!=200），避免泄露"客户端是否存在"；
     * 真正不可用客户端一律视为"未配置"返回脱敏结果，前端回退默认模板。</p>
     *
     * @param clientId 客户端ID（必填，URL 查询参数）
     * @return 客户端公共配置
     */
    @GetMapping
    public R<ClientPublicConfigVo> getPublicConfig(@RequestParam("clientId") String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return R.fail("客户端ID不能为空");
        }
        try {
            SysClientVo client = sysClientService.queryByClientId(clientId);
            if (ObjectUtil.isNull(client)) {
                return R.fail("客户端不存在或已停用");
            }
            if (!SystemConstants.NORMAL.equals(client.getStatus())) {
                return R.fail("客户端已停用");
            }

            ClientPublicConfigVo vo = new ClientPublicConfigVo();
            vo.setClientId(client.getClientId());
            vo.setClientKey(client.getClientKey());
            vo.setStatus(client.getStatus());
            vo.setDeviceType(client.getDeviceType());
            vo.setLayoutConfig(client.getLayoutConfig());
            vo.setRegisterEnabled(client.getRegisterEnabled());
            vo.setResetPwdEnabled(client.getResetPwdEnabled());
            // 客户端绑定的应用菜单名作为系统名称返回
            if (client.getMenuId() != null) {
                SysMenu menu = sysMenuMapper.selectById(client.getMenuId());
                if (menu != null) {
                    vo.setClientName(menu.getMenuName());
                }
            }
            return R.ok(vo);
        } catch (Exception e) {
            log.warn("拉取客户端公共配置异常 clientId={}, err={}", clientId, e.getMessage());
            return R.fail("客户端配置不可用");
        }
    }
}
