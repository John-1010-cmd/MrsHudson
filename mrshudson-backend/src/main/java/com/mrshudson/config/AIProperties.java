package com.mrshudson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI模型配置属性 支持模型: kimi, minimax 备选: 智谱GLM4, 通义千问 - 暂未实现
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProperties {

  /**
   * AI提供者: kimi / minimax
   */
  private String provider = "kimi";

  /**
   * Kimi配置
   */
  private KimiConfig kimi = new KimiConfig();

  /**
   * MiniMax配置
   */
  private MiniMaxConfig miniMax = new MiniMaxConfig();

  @Data
  public static class KimiConfig {
    private String apiKey;
    private String baseUrl = "https://api.moonshot.cn/v1";
    private String model = "moonshot-v1-8k";
    private int timeout = 30000;
    private double temperature = 0.7;
    private int maxTokens = 4096;
  }

  @Data
  public static class MiniMaxConfig {
    private String apiKey;
    private String baseUrl = "https://api.minimax.chat/v1";
    private String model = "MiniMax-M2.7";
    private int timeout = 30000;
    private double temperature = 0.7;
    private int maxTokens = 4096;
  }
}
