package com.mrshudson.util;

/**
 * JWT Token 上下文（基于 ThreadLocal）
 * 用于在请求线程中传递当前 Token
 */
public class JwtContext {

    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前 Token
     *
     * @param token JWT Token
     */
    public static void setCurrentToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    /**
     * 获取当前 Token
     *
     * @return JWT Token
     */
    public static String getCurrentToken() {
        return TOKEN_HOLDER.get();
    }

    /**
     * 清除当前 Token
     */
    public static void clear() {
        TOKEN_HOLDER.remove();
    }
}
