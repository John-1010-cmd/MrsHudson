package com.mrshudson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.dto.LoginRequest;
import com.mrshudson.domain.dto.UnauthorizedException;
import com.mrshudson.domain.entity.User;
import com.mrshudson.mapper.UserMapper;
import com.mrshudson.service.AuthService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final HttpSession httpSession;

    // Session中存储用户ID的key
    private static final String USER_ID_KEY = "user_id";

    @Override
    public User login(LoginRequest request) {
        log.info("用户登录: {}", request.getUsername());

        // 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // Demo阶段：明文密码比较
        if (!request.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 将用户ID存入session
        httpSession.setAttribute(USER_ID_KEY, user.getId());
        log.info("用户登录成功: {}", user.getUsername());

        return user;
    }

    @Override
    public void logout() {
        Long userId = (Long) httpSession.getAttribute(USER_ID_KEY);
        log.info("用户登出: {}", userId);
        httpSession.invalidate();
    }

    @Override
    public User getCurrentUser() {
        Long userId = (Long) httpSession.getAttribute(USER_ID_KEY);
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }
        return user;
    }

    /**
     * 获取当前登录用户ID
     */
    public Long getCurrentUserId() {
        Long userId = (Long) httpSession.getAttribute(USER_ID_KEY);
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return userId;
    }
}
