package com.mrshudson.filter;

import com.mrshudson.util.JwtContext;
import com.mrshudson.util.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.token-prefix:Bearer}")
    private String tokenPrefix;

    @Value("${jwt.header-name:Authorization}")
    private String headerName;

    // 黑名单 key 前缀
    private static final String BLACKLIST_KEY_PREFIX = "token_blacklist:";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. 从请求头中提取 Token
            String token = extractTokenFromRequest(request);

            // 2. 如果存在 Token，进行验证
            if (StringUtils.hasText(token)) {
                // 验证 Token 有效性
                if (jwtTokenUtil.validateToken(token)) {
                    // 检查 Token 是否在黑名单中
                    Long userId = jwtTokenUtil.extractUserId(token);
                    String blacklistKey = BLACKLIST_KEY_PREFIX + userId;

                    if (!Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
                        // 将 Token 设置到上下文中
                        JwtContext.setCurrentToken(token);
                        log.debug("JWT 验证成功，用户ID: {}", userId);
                    } else {
                        log.warn("Token 已在黑名单中，用户ID: {}", userId);
                    }
                } else {
                    log.warn("JWT 验证失败");
                }
            }

            // 3. 继续过滤器链
            filterChain.doFilter(request, response);

        } finally {
            // 4. 清理 ThreadLocal，防止内存泄漏
            JwtContext.clear();
        }
    }

    /**
     * 从请求头中提取 Token
     *
     * @param request HTTP 请求
     * @return Token 字符串
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(headerName);
        log.debug("请求头 {}: {}", headerName, bearerToken);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(tokenPrefix + " ")) {
            return bearerToken.substring(tokenPrefix.length() + 1);
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 登录和刷新 Token 接口不需要 JWT 验证
        if (path.equals("/api/auth/login") && method.equals("POST")) {
            return true;
        }
        if (path.equals("/api/auth/refresh") && method.equals("POST")) {
            return true;
        }

        // OPTIONS 请求不需要验证
        if (method.equals("OPTIONS")) {
            return true;
        }

        return false;
    }
}
