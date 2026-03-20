package com.mrshudson.mcp.minimax;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MiniMax API配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.minimax")
public class MiniMaxProperties {

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * API基础URL
     */
    private String baseUrl = "https://api.minimax.chat/v1";

    /**
     * 模型名称
     */
    private String model = "MiniMax-M2.7";

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