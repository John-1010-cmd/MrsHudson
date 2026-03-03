package com.mrshudson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.dto.LoginRequest;
import com.mrshudson.domain.dto.LoginResponse;
import com.mrshudson.domain.dto.UnauthorizedException;
import com.mrshudson.domain.entity.User;
import com.mrshudson.mapper.UserMapper;
import com.mrshudson.service.AuthService;
import com.mrshudson.util.JwtContext;
import com.mrshudson.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现（JWT 版本）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtTokenUtil jwtTokenUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.access-token-expiration:3600000}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private Long refreshTokenExpiration;

    // Redis key 前缀
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";
    private static final String BLACKLIST_KEY_PREFIX = "token_blacklist:";

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("用户登录: {}", request.getUsername());

        // 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // Demo阶段：明文密码比较（后续应使用 BCrypt）
        if (!request.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 生成 Token 对
        String accessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId());

        // 将 Refresh Token 存入 Redis（用于登出时使 token 失效）
        String redisKey = REFRESH_TOKEN_KEY_PREFIX + user.getId();
        redisTemplate.opsForValue().set(
                redisKey,
                refreshToken,
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );

        log.info("用户登录成功: {}", user.getUsername());

        return LoginResponse.fromUser(user, accessToken, refreshToken, accessTokenExpiration / 1000);
    }

    @Override
    public void logout() {
        try {
            Long userId = getCurrentUserId();
            log.info("用户登出: {}", userId);

            // 删除 Redis 中的 Refresh Token
            String redisKey = REFRESH_TOKEN_KEY_PREFIX + userId;
            redisTemplate.delete(redisKey);
        } catch (UnauthorizedException e) {
            log.warn("登出时用户未登录");
        }
    }

    @Override
    public User getCurrentUser() {
        Long userId = getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }
        return user;
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        log.info("刷新 Token");

        // 验证 Refresh Token
        if (!jwtTokenUtil.validateToken(refreshToken)) {
            throw new UnauthorizedException("无效的 Refresh Token");
        }

        if (!jwtTokenUtil.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("不是 Refresh Token");
        }

        // 从 Token 中提取用户ID
        Long userId = jwtTokenUtil.extractUserId(refreshToken);

        // 检查 Redis 中的 Refresh Token 是否匹配
        String redisKey = REFRESH_TOKEN_KEY_PREFIX + userId;
        String storedToken = redisTemplate.opsForValue().get(redisKey);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new UnauthorizedException("Refresh Token 已失效");
        }

        // 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }

        // 生成新的 Token 对
        String newAccessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUsername());
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(user.getId());

        // 更新 Redis 中的 Refresh Token
        redisTemplate.opsForValue().set(
                redisKey,
                newRefreshToken,
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );

        log.info("Token 刷新成功: {}", user.getUsername());

        return LoginResponse.fromUser(user, newAccessToken, newRefreshToken, accessTokenExpiration / 1000);
    }

    @Override
    public Long getCurrentUserId() {
        // 从 RequestContext 获取当前请求的 Token
        String token = JwtContext.getCurrentToken();
        if (token == null) {
            throw new UnauthorizedException("用户未登录");
        }

        // 验证 Token
        if (!jwtTokenUtil.validateToken(token)) {
            throw new UnauthorizedException("Token 无效或已过期");
        }

        // 检查 Token 是否在黑名单中
        String blacklistKey = BLACKLIST_KEY_PREFIX + jwtTokenUtil.extractUserId(token);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            throw new UnauthorizedException("Token 已被注销");
        }

        return jwtTokenUtil.extractUserId(token);
    }
}
