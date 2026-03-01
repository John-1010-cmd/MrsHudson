package com.mrshudson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 天气API配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {

    /**
     * API密钥（高德地图Key）
     */
    private String apiKey;

    /**
     * API基础URL（高德地图）
     */
    private String baseUrl = "https://restapi.amap.com/v3";
}
