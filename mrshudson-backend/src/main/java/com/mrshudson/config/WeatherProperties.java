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
     * API密钥
     */
    private String apiKey;

    /**
     * API基础URL（使用和风天气）
     */
    private String baseUrl = "https://devapi.qweather.com/v7";

    /**
     * Geo API URL（城市搜索）
     */
    private String geoUrl = "https://geoapi.qweather.com/v2";
}
