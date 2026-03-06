package com.mrshudson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Firebase配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "firebase")
public class FirebaseProperties {

    /**
     * Firebase项目ID
     * 例如：mrshudson-xxx
     */
    private String projectId;

    /**
     * 服务账号JSON文件路径
     * 例如：classpath:firebase-service-account.json
     */
    private String credentialsPath = "classpath:firebase-service-account.json";

    /**
     * FCM HTTP v1 API 基础URL
     */
    private String fcmBaseUrl = "https://fcm.googleapis.com";
}
