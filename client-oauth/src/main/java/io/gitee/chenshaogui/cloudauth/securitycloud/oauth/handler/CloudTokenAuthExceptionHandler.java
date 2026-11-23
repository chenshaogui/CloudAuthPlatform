package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.handler;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotSafeException;
import cn.dev33.satoken.sso.error.SaSsoErrorCode;
import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 异常处理器
 *
 * <p>统一接管 sa-token 抛出的各类异常，避免业务方重复实现。
 *
 * <p>注意：方法参数**不要**声明 {@code SaRequest request}。{@code SaRequest} 是 sa-token 内部抽象接口，
 * 不是 Spring 标准参数类型（无 {@code @RequestParam} 等标注），Spring 参数解析器无法识别时
 * 会 fallback 到 {@code ModelAttributeMethodProcessor}，抛出
 * {@code IllegalStateException: ModelAttributeMethodProcessor requires WebDataBinderFactory}，
 * 导致整个异常处理链断裂。统一通过 {@link SaHolder#getRequest()} 内部获取请求上下文。
 *
 * @author Lion Li
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CloudTokenAuthExceptionHandler {

    /**
     * 权限码异常
     */
    @ExceptionHandler(NotPermissionException.class)
    public SaResult handleNotPermissionException(NotPermissionException e) {
        String requestURI = SaHolder.getRequest().getUrl();
        log.error("请求地址'{}',权限码校验失败'{}'", requestURI, e.getMessage());
        return new SaResult(HttpStatus.HTTP_FORBIDDEN, "没有访问权限，请联系管理员授权", null);
    }

    /**
     * 角色权限异常
     */
    @ExceptionHandler(NotRoleException.class)
    public SaResult handleNotRoleException(NotRoleException e) {
        String requestURI = SaHolder.getRequest().getUrl();
        log.error("请求地址'{}',角色权限校验失败'{}'", requestURI, e.getMessage());
        return new SaResult(HttpStatus.HTTP_FORBIDDEN, "没有访问权限，请联系管理员授权", null);
    }

    /**
     * 认证失败
     */
    @ExceptionHandler(NotLoginException.class)
    public SaResult handleNotLoginException(NotLoginException e) {
        String requestURI = SaHolder.getRequest().getUrl();
        log.error("请求地址'{}',认证失败'{}',无法访问系统资源", requestURI, e.getMessage());
        return new SaResult(HttpStatus.HTTP_UNAUTHORIZED, "认证失败，无法访问系统资源", null);
    }

    /**
     * 二级认证异常（短信验证、邮箱验证等未通过）
     */
    @ExceptionHandler(NotSafeException.class)
    public SaResult handleNotSafeException(NotSafeException e) {
        String requestURI = SaHolder.getRequest().getUrl();
        log.warn("请求地址'{}',二级认证未通过'{}'", requestURI, e.getMessage());
        return new SaResult(HttpStatus.HTTP_UNAUTHORIZED, "安全认证未通过，请完成二次验证", null);
    }

    /**
     * 服务被封禁异常
     */
    @ExceptionHandler(DisableServiceException.class)
    public SaResult handleDisableServiceException(DisableServiceException e) {
        String requestURI = SaHolder.getRequest().getUrl();
        log.warn("请求地址'{}',服务被封禁'{}'", requestURI, e.getMessage());
        return new SaResult(HttpStatus.HTTP_FORBIDDEN, "账号已被封禁，请联系管理员", null);
    }

    /**
     * BackResultException 由 sa-token 框架主动抛出用于中断过滤器链路，
     * 不应作为异常计入日志。仅在 Controller 调用栈时为真正的业务异常。
     */
    @ExceptionHandler(BackResultException.class)
    public SaResult handleBackResultException(BackResultException e) {
        log.debug("请求地址'{}',BackResultException:{}", SaHolder.getRequest().getUrl(), e.getMessage());
        return new SaResult(HttpStatus.HTTP_INTERNAL_ERROR, e.getMessage(), null);
    }

    /**
     * Sa-Token SSO 流程异常（如 SSO 中心返工 "无效 ticket"、"签名失败" 等）。
     *
     * <p>sa-token-sso 在调用 SSO 中心校验 ticket 时，会把中心返回的 msg 字段
     * 直接作为 {@link SaSsoException} 的 message 抛出（参见
     * {@code SaSsoClientProcessor#_checkTicketByHttp} 第 357 行），异常细分
     * 状态码固定为 {@link SaSsoErrorCode#CODE_30005}。</p>
     *
     * <p>如果这里不接管，异常会向下穿透到业务系统
     * {@code GlobalExceptionHandler#defaultExceptionHandler}，被强制改写为
     * 固定的 "系统异常"，前端无法区分 "ticket 过期 / 签名错 / redirect 不在白名单"
     * 等真实原因。</p>
     *
     * <p>这里直接透传 {@code e.getMessage()} 给调用方，并在日志中记录细分状态码
     * 与 SSO 中心返回的原始报文，方便排障。</p>
     */
    @ExceptionHandler(SaSsoException.class)
    public SaResult handleSaSsoException(SaSsoException e) {
        String requestURI = SaHolder.getRequest().getUrl();
        // 30005 = SSO 中心 checkTicket 校验失败；其他细分状态码多为本地配置缺失
        log.warn("请求地址'{}',SSO认证失败[code={}]'{}'", requestURI, e.getCode(), e.getMessage());
        return new SaResult(HttpStatus.HTTP_INTERNAL_ERROR, e.getMessage(), null);
    }

}