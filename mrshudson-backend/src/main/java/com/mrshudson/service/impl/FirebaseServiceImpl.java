package com.mrshudson.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.mrshudson.config.FirebaseProperties;
import com.mrshudson.domain.entity.DeviceToken;
import com.mrshudson.mapper.DeviceTokenMapper;
import com.mrshudson.service.PushService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Firebase推送服务实现
 * 使用 Firebase Admin SDK
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseServiceImpl implements PushService {

    private final DeviceTokenMapper deviceTokenMapper;
    private final FirebaseProperties firebaseProperties;
    private final ResourceLoader resourceLoader;

    private FirebaseMessaging firebaseMessaging;

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource(firebaseProperties.getCredentialsPath());
            InputStream serviceAccountStream = resource.getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                    .build();

            this.firebaseMessaging = FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options));
            log.info("Firebase Admin SDK 初始化成功");
        } catch (Exception e) {
            log.warn("Firebase 初始化失败: {}. 推送功能将不可用。", e.getMessage());
            this.firebaseMessaging = null;
        }
    }

    @Override
    public boolean registerDevice(Long userId, String deviceToken, String platform) {
        log.info("注册设备Token: userId={}, platform={}", userId, platform);

        // 先检查是否已存在相同的 token
        DeviceToken existingToken = deviceTokenMapper.selectOne(
                new LambdaQueryWrapper<DeviceToken>()
                        .eq(DeviceToken::getDeviceToken, deviceToken)
        );

        if (existingToken != null) {
            // 更新现有记录
            existingToken.setUserId(userId);
            existingToken.setPlatform(platform);
            existingToken.setIsActive(true);
            deviceTokenMapper.updateById(existingToken);
            log.info("更新已存在的设备Token: id={}", existingToken.getId());
            return true;
        }

        // 创建新记录
        DeviceToken newToken = new DeviceToken();
        newToken.setUserId(userId);
        newToken.setDeviceToken(deviceToken);
        newToken.setPlatform(platform);
        newToken.setIsActive(true);

        deviceTokenMapper.insert(newToken);
        log.info("新增设备Token: id={}", newToken.getId());
        return true;
    }

    @Override
    public int sendNotificationToUser(Long userId, String title, String body) {
        if (firebaseMessaging == null) {
            log.warn("Firebase 未初始化，无法发送推送");
            return 0;
        }

        // 获取用户的所有活跃设备
        List<DeviceToken> devices = deviceTokenMapper.selectList(
                new LambdaQueryWrapper<DeviceToken>()
                        .eq(DeviceToken::getUserId, userId)
                        .eq(DeviceToken::getIsActive, true)
        );

        if (devices.isEmpty()) {
            log.debug("用户 {} 没有活跃设备", userId);
            return 0;
        }

        int successCount = 0;
        for (DeviceToken device : devices) {
            if (sendNotificationToDevice(device.getDeviceToken(), title, body)) {
                successCount++;
            }
        }

        log.info("向用户 {} 发送推送，成功 {} 个设备", userId, successCount);
        return successCount;
    }

    @Override
    public boolean sendNotificationToDevice(String deviceToken, String title, String body) {
        if (firebaseMessaging == null) {
            log.warn("Firebase 未初始化，无法发送推送");
            return false;
        }

        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("推送发送成功: messageId={}", response);
            return true;

        } catch (FirebaseMessagingException e) {
            log.error("推送发送失败: code={}, message={}",
                    e.getMessagingErrorCode(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("推送发送异常: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeDeviceToken(Long userId, String deviceToken) {
        DeviceToken token = deviceTokenMapper.selectOne(
                new LambdaQueryWrapper<DeviceToken>()
                        .eq(DeviceToken::getUserId, userId)
                        .eq(DeviceToken::getDeviceToken, deviceToken)
        );

        if (token != null) {
            token.setIsActive(false);
            deviceTokenMapper.updateById(token);
            log.info("移除设备Token: userId={}, token={}", userId, deviceToken.substring(0, 20) + "...");
            return true;
        }
        return false;
    }
}
