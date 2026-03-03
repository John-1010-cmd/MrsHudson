package com.mrshudson.service;

import com.mrshudson.domain.dto.LoginRequest;
import com.mrshudson.domain.dto.LoginResponse;
import com.mrshudson.domain.entity.User;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（包含Token）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出
     */
    void logout();

    /**
     * 获取当前登录用户
     *
     * @return 当前用户
     */
    User getCurrentUser();

    /**
     * 刷新 Access Token
     *
     * @param refreshToken Refresh Token
     * @return 新的登录响应（包含新Token）
     */
    LoginResponse refreshToken(String refreshToken);

    /**
     * 获取当前登录用户ID（从 Token 中）
     *
     * @return 当前用户ID
     */
    Long getCurrentUserId();
}
