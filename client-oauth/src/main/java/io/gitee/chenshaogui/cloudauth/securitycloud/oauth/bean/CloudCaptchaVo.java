package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * 图形验证码信息（认证中心 {@code /code} 生成，前端登录时携带 {@link #uuid} + 用户输入码）
 *
 * <p>字段与认证中心 {@code CaptchaVo} 对齐：{@link #captchaEnabled} 为 {@code false} 时
 * 表示认证中心未开启图形验证码，前端可跳过验证码输入框。</p>
 *
 * @author sgchen
 * @since 1.3.3
 */
@Data
public class CloudCaptchaVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否开启图形验证码
     */
    private Boolean captchaEnabled = true;

    /**
     * 验证码唯一标识（登录时随 body 一并回传，服务端按此从 Redis 取码校验）
     */
    private String uuid;

    /**
     * 验证码图片（base64，前端可直接 {@code <img src="data:image/png;base64,...">}）
     */
    private String img;

}
