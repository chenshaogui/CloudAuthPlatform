package io.gitee.chenshaogui.cloudauth.common.satoken.utils;

/**
 * 浏览器维度 ID 上下文（线程级）
 * <p>
 * 由 BrowserIdFilter（auth server / 客户端 SDK）在请求入口处调用
 * {@link #set(String)}，业务代码通过 {@link #get()} 读取当前请求的 browserId。
 * </p>
 *
 * <p>使用场景：写入 sa-token token extra，登出时按 browserId 过滤实现
 * "同浏览器多 tab 一起退出"。</p>
 *
 * @author sgchen
 */
public final class BrowserIdContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private BrowserIdContext() {
    }

    /**
     * 设置当前请求线程的 browserId（Filter 在 finally 中负责清理）。
     */
    public static void set(String browserId) {
        CURRENT.set(browserId);
    }

    /**
     * 取当前请求线程的 browserId。未设置时返回 {@code null}（调用方决定如何 fallback）。
     */
    public static String get() {
        return CURRENT.get();
    }

    /**
     * 清理当前线程的 ThreadLocal（Filter 在 finally 中调用）。
     */
    public static void clear() {
        CURRENT.remove();
    }
}