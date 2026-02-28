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

    /**
     * 从User实体创建响应
     */
    public static LoginResponse fromUser(User user) {
        LoginResponse response = new LoginResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        return response;
    }
}
