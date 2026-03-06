package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.service.PushService;
import com.mrshudson.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 推送控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;
    private final JwtTokenUtil jwtTokenUtil;

    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 注册设备Token
     * 安卓端登录后调用此接口注册 FCM Token
     */
    @PostMapping("/register")
    public Result<Void> registerDevice(@RequestBody RegisterDeviceRequest request, HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        if (userId == null) {
            return Result.error(401, "未登录或Token无效");
        }

        log.info("注册设备: userId={}, platform={}", userId, request.getPlatform());

        boolean success = pushService.registerDevice(userId, request.getDeviceToken(), request.getPlatform());

        if (success) {
            return Result.success(null);
        } else {
            return Result.error("设备注册失败");
        }
    }

    /**
     * 移除设备Token
     * 用户登出时调用
     */
    @DeleteMapping("/unregister")
    public Result<Void> unregisterDevice(@RequestBody RegisterDeviceRequest request, HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        if (userId == null) {
            return Result.error(401, "未登录或Token无效");
        }

        log.info("移除设备: userId={}", userId);

        boolean success = pushService.removeDeviceToken(userId, request.getDeviceToken());

        if (success) {
            return Result.success(null);
        } else {
            return Result.error("设备移除失败");
        }
    }

    /**
     * 发送测试推送
     */
    @PostMapping("/test")
    public Result<Integer> sendTestNotification(@RequestBody TestNotificationRequest request, HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        if (userId == null) {
            return Result.error(401, "未登录或Token无效");
        }

        log.info("发送测试推送: userId={}, title={}", userId, request.getTitle());

        int count = pushService.sendNotificationToUser(userId, request.getTitle(), request.getBody());
        return Result.success(count);
    }

    /**
     * 从HTTP请求中提取用户ID
     */
    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (StringUtils.hasText(token) && jwtTokenUtil.validateToken(token)) {
            return jwtTokenUtil.extractUserId(token);
        }
        return null;
    }

    /**
     * 从HTTP请求中提取Token
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 注册设备请求
     */
    public static class RegisterDeviceRequest {
        private String deviceToken;
        private String platform;

        public String getDeviceToken() {
            return deviceToken;
        }

        public void setDeviceToken(String deviceToken) {
            this.deviceToken = deviceToken;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }
    }

    /**
     * 测试推送请求
     */
    public static class TestNotificationRequest {
        private String title;
        private String body;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
