package com.mrshudson.controller;

import com.mrshudson.domain.dto.LoginRequest;
import com.mrshudson.domain.dto.LoginResponse;
import com.mrshudson.domain.dto.Result;
import com.mrshudson.domain.entity.User;
import com.mrshudson.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("登录请求: {}", request.getUsername());
        try {
            User user = authService.login(request);
            return Result.success(LoginResponse.fromUser(user));
        } catch (RuntimeException e) {
            log.warn("登录失败: {}", e.getMessage());
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<LoginResponse> getCurrentUser() {
        try {
            User user = authService.getCurrentUser();
            return Result.success(LoginResponse.fromUser(user));
        } catch (RuntimeException e) {
            return Result.error(401, e.getMessage());
        }
    }
}
