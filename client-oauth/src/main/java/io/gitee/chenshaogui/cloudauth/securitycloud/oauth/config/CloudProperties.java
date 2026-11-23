package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cloud-auth-client-oauth 全局配置项（{@code @ConfigurationProperties}）。
 *
 * <p>业务方可在 {@code application.yml} / nacos 中按需配置，所有项均有默认值，
 * 不配置时行为不变（向后兼容）。</p>
 *
 * <p><b>典型配置示例</b>：
 * <pre>{@code
 * sa-token:
 *   # 全局登录白名单（追加到 SDK 硬默认白名单之后）
 *   # 路径写法遵循 Ant-style：/admin-api/system/sms/callback/**
 *   permit-paths:
 *     - /admin-api/system/sms/callback/**
 *     - /admin-api/openapi/webhook/**
 *     - /webhook/**
 *   # 注册自动绑定角色标识（sys_role.role_key，可配多个；留空不绑定）
 *   register-role-keys:
 *     - car-user
 *   # 自定义业务参数（Map 形式，key 建议用小写或 kebab-case，值统一按字符串接收后由 SDK 按需转型）
 *   custom-properties:
 *     xxxx: "123"
 *     xxx1: qweer
 *   # SDK Controller（/sso/**、/authCenter/**）注册开关
 *   # 微服务架构下推荐只在登录入口模块开启，其他模块关闭
 *   expose-controller: false
 * }</pre>
 * </p>
 *
 * <p><b>关于 prefix</b>：使用 {@code sa-token} 与 sa-token 官方的 {@code SaTokenConfig}
 * 共享同一份 yaml 命名空间，两者 bean 名不同（{@code sa-token-cn.dev33.satoken.config.SaTokenConfig}
 * 与 {@code sa-token-io.gitee.chenshaogui.cloudauth.securitycloud.oauth.config.CloudProperties}）不冲突。
 * sa-token 官方未提供 permit-paths 字段，本 SDK 在该 prefix 下扩展，互不影响。</p>
 *
 * @author sgchen
 * @since 1.2.0
 */
@Data
@ConfigurationProperties(prefix = "sa-token")
public class CloudProperties {

    /**
     * 全局登录白名单（路径模式）。
     *
     * <p>追加到 SDK 硬默认白名单（{@code /sso/*}、{@code /favicon.ico}）之后，
     * 用于业务模块新增不需要登录即可访问的接口（如第三方回调、webhook、公共 API 等）。</p>
     *
     * <p>匹配规则由 sa-token 的 {@code SaFoxUtil.vagueMatch} 实现
     * （{@code *} 通配任意字符，{@code /**} 可通配多层路径），与
     * {@code addExclude} 的匹配口径完全一致。</p>
     *
     * <p><b>热更新（配置中心）</b>：本配置项存放在 Nacos / Apollo 等配置中心时，
     * 变更后<b>无需重启服务</b>即可生效 —— 鉴权过滤器每次请求都会实时读取本字段，
     * 而该字段由 Spring Cloud 的 {@code ConfigurationPropertiesRebinder} 在
     * 同一个 {@code CloudProperties} 实例上重新绑定。
     * SDK 自身不依赖任何配置中心，配置源与刷新事件均由业务工程提供。</p>
     */
    private List<String> permitPaths = new ArrayList<>();

    /**
     * SDK Controller（{@code /sso/**}、{@code /authCenter/**}）注册开关。
     *
     * <p>微服务架构下推荐 <b>只在登录入口模块</b>（如 system）开启，
     * 其他模块关闭以避免多模块重复暴露接口引发的 session 错乱。</p>
     *
     * <p>详细说明见
     * {@code io.gitee.chenshaogui.cloudauth.securitycloud.oauth.controller.CloudTokenSsoClientController} / {@code CloudAuthCenterController}
     * 类注释中的"多模块注册开关"段落。</p>
     */
    private Boolean exposeController = Boolean.TRUE;

    /**
     * 注册时自动绑定的角色标识列表（{@code sys_role.role_key}，可配多个）。
     *
     * <p>背景：用户自助注册后没有任何角色/菜单权限，PC 端看不到菜单，
     * 必须等管理员手动配置。配置本项后，SDK 会把角色标识随 {@code register}
     * 总线消息上报认证中心，服务端在新用户<b>尚未绑定任何角色</b>时按
     * {@code role_key} 自动完成绑定；未配置（无值）则不发送、行为不变。</p>
     *
     * <p>生效范围：{@code CloudPlatformHelper.register / registerByCaptcha / registerBySms}
     * 及对应的 {@code /sso/register*} HTTP 端点；业务方在
     * {@code CloudRegisterBo#roleKeys} 显式传值时以业务方为准（不被本配置覆盖）。</p>
     *
     * <p>服务端约束：按标识查不到启用角色时静默跳过（仅记日志）；超级管理员角色
     * 不允许通过本通道自动绑定（防越权提权）。与 {@link #permitPaths} 同样支持
     * Nacos 热更新，无需重启。</p>
     *
     * @since 1.4.0
     */
    private List<String> registerRoleKeys = new ArrayList<>();

    /**
     * 自定义业务参数（Map 形式，key → value，值统一按字符串接收）。
     *
     * <p>供业务方在 {@code sa-token} 命名空间下挂任意自定义键值，随环境/配置中心下发，
     * 在 {@code CloudPlatformHelper.getCustomProperty/getCustomPropertyInt/getCustomPropertyBoolean/getCustomProperties} 中读取：</p>
     * <pre>{@code
     * sa-token:
     *   custom-properties:
     *     xxxx: "123"
     *     xxx1: qweer
     * }</pre>
     *
     * <p><b>key 规范</b>：请使用<b>小写或 kebab-case</b>（如 {@code xxx}、{@code xxx-yyy}），
     * 与 {@code permit-paths} 等既有配置风格一致；Spring Boot 宽松绑定会对 Map key 做规范化，
     * <b>不要依赖驼峰大小写原样保留</b>。业务方主动 set 的整 Map 会整体替换已绑定值。</p>
     *
     * <p><b>热更新</b>：与 {@link #permitPaths} 同样支持 Nacos / Apollo 热更新
     * （{@code ConfigurationPropertiesRebinder} 重新绑定同一实例），取值端每次实时读取，无需重启。</p>
     *
     * @since 1.5.3
     */
    private Map<String, String> customProperties = new LinkedHashMap<>();

    /**
     * 追加若干白名单路径（编程式 API，{@code @Bean} 装配时可链式调用）。
     */
    public CloudProperties addPermitPaths(String... paths) {
        if (paths != null) {
            this.permitPaths.addAll(Arrays.asList(paths));
        }
        return this;
    }
}