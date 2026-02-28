package com.mrshudson.service;

import com.mrshudson.domain.dto.LoginRequest;
import com.mrshudson.domain.entity.User;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录成功的用户
     */
    User login(LoginRequest request);

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
}
