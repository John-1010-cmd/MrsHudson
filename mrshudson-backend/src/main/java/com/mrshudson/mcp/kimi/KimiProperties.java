package com.mrshudson.mcp.kimi;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Kimi API配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "kimi")
public class KimiProperties {

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * API基础URL
     */
    private String baseUrl = "https://api.moonshot.cn/v1";

    /**
     * 模型名称
     */
    private String model = "moonshot-v1-8k";

    /**
     * 超时时间（毫秒）
     */
    private int timeout = 30000;

    /**
     * 温度参数
     */
    private double temperature = 0.7;

    /**
     * 最大token数
     */
    private int maxTokens = 4096;
}
