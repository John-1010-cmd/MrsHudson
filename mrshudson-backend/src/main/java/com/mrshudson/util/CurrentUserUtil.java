package com.mrshudson.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 当前用户工具类
 * 从 JWT Token 上下文中获取当前登录用户ID
 */
@Slf4j
@Component
public class CurrentUserUtil {

    private final JwtTokenUtil jwtTokenUtil;

    public CurrentUserUtil(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID，如果未登录则返回 null
     */
    public Long getCurrentUserId() {
        String token = JwtContext.getCurrentToken();
        if (token == null) {
            log.debug("当前请求无 JWT Token");
            return null;
        }

        try {
            if (jwtTokenUtil.validateToken(token) && jwtTokenUtil.isAccessToken(token)) {
                return jwtTokenUtil.extractUserId(token);
            }
        } catch (Exception e) {
            log.warn("解析 JWT Token 失败: {}", e.getMessage());
        }

        return null;
    }
}
