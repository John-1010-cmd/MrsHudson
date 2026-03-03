package com.mrshudson.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 工具类
 */
@Slf4j
@Component
public class JwtTokenUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration:3600000}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private Long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return Access Token
     */
    public String generateAccessToken(Long userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 生成 Refresh Token
     *
     * @param userId 用户ID
     * @return Refresh Token
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 从 Token 中提取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从 Token 中提取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String extractUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期");
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的 Token");
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Token 格式错误");
            return false;
        } catch (SignatureException e) {
            log.warn("Token 签名验证失败");
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Token 为空或非法");
            return false;
        }
    }

    /**
     * 验证 Token 是否为 Refresh Token
     *
     * @param token JWT Token
     * @return 是否为 Refresh Token
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "refresh".equals(claims.get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 Token 是否为 Access Token
     *
     * @param token JWT Token
     * @return 是否为 Access Token
     */
    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "access".equals(claims.get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 Token 获取 Claims
     *
     * @param token JWT Token
     * @return Claims
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取 Token 过期时间
     *
     * @param token JWT Token
     * @return 过期时间
     */
    public Date getExpirationDate(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 检查 Token 是否即将过期（5分钟内）
     *
     * @param token JWT Token
     * @return 是否即将过期
     */
    public boolean isTokenAboutToExpire(String token) {
        Date expiration = getExpirationDate(token);
        Date now = new Date();
        // 如果剩余时间小于 5 分钟，则认为即将过期
        return (expiration.getTime() - now.getTime()) < 300000;
    }
}
