package com.mrshudson.domain.dto;

import com.mrshudson.domain.entity.User;
import lombok.Data;

/**
 * 登录响应DTO
 */
@Data
public class LoginResponse {

    private Long id;
    private String username;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;

    /**
     * 从User实体创建响应（不包含Token）
     */
    public static LoginResponse fromUser(User user) {
        LoginResponse response = new LoginResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        return response;
    }

    /**
     * 从User实体创建响应（包含Token）
     */
    public static LoginResponse fromUser(User user, String accessToken, String refreshToken, Long expiresIn) {
        LoginResponse response = fromUser(user);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(expiresIn);
        return response;
    }
}
