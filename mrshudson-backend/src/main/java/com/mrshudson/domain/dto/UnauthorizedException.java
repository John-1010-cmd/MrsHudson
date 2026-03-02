package com.mrshudson.domain.dto;

/**
 * 未授权异常（未登录或无权限）
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
