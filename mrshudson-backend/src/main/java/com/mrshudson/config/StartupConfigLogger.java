package com.mrshudson.config;

import com.mrshudson.optim.config.OptimProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

/**
 * 启动配置信息打印
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StartupConfigLogger {

    private final Environment environment;
    private final AIProperties aiProperties;
    private final WeatherProperties weatherProperties;
    private final OptimProperties optimProperties;
    private final VoiceProperties voiceProperties;

    @PostConstruct
    public void printStartupConfig() {
        log.info("========================================");
        log.info("       MrsHudson 启动配置信息");
        log.info("========================================");

        // AI 配置
        log.info("【AI 模型配置】");
        log.info("  AI Provider: {}", aiProperties.getProvider());
        if ("kimi".equals(aiProperties.getProvider())) {
            log.info("  Kimi Model: {}", aiProperties.getKimi().getModel());
            log.info("  Kimi API Key: ***{}",
                    aiProperties.getKimi().getApiKey() != null && aiProperties.getKimi().getApiKey().length() > 8
                            ? aiProperties.getKimi().getApiKey().substring(aiProperties.getKimi().getApiKey().length() - 8)
                            : "未配置");
        } else if ("minimax".equals(aiProperties.getProvider())) {
            log.info("  MiniMax Model: {}", aiProperties.getMiniMax().getModel());
            log.info("  MiniMax API Key: ***{}",
                    aiProperties.getMiniMax().getApiKey() != null && aiProperties.getMiniMax().getApiKey().length() > 8
                            ? aiProperties.getMiniMax().getApiKey().substring(aiProperties.getMiniMax().getApiKey().length() - 8)
                            : "未配置");
        }

        // 天气 API 配置
        log.info("【天气 API 配置】");
        log.info("  Weather API Key: {}",
                weatherProperties.getApiKey() != null && !weatherProperties.getApiKey().isEmpty()
                        ? "***" + weatherProperties.getApiKey().substring(Math.max(0, weatherProperties.getApiKey().length() - 4))
                        : "未配置");

        // 语义缓存配置
        log.info("【语义缓存配置】");
        log.info("  启用状态: {}", optimProperties.getSemanticCache().isEnabled() ? "已启用" : "已禁用");
        log.info("  相似度阈值: {}", optimProperties.getSemanticCache().getSimilarityThreshold());
        log.info("  缓存TTL: {} 小时", optimProperties.getSemanticCache().getTtlHours());

        // 意图路由配置
        log.info("【意图路由配置】");
        log.info("  路由模式: {}", optimProperties.getIntentRouter().getMode());
        log.info("  规则层: {}", optimProperties.getIntentRouter().getRule().isEnabled() ? "已启用" : "已禁用");
        log.info("  轻量AI层: {}", optimProperties.getIntentRouter().getLightweightAi().isEnabled() ? "已启用" : "已禁用");
        log.info("  完整AI层: {}", optimProperties.getIntentRouter().getFullAi().isEnabled() ? "已启用" : "已禁用");

        // 工具缓存配置
        log.info("【工具缓存配置】");
        log.info("  启用状态: {}", optimProperties.getToolCache().isEnabled() ? "已启用" : "已禁用");
        log.info("  天气缓存TTL: {} 分钟", optimProperties.getToolCache().getWeatherTtlMinutes());
        log.info("  日历缓存TTL: {} 分钟", optimProperties.getToolCache().getCalendarTtlMinutes());
        log.info("  待办缓存TTL: {} 分钟", optimProperties.getToolCache().getTodoTtlMinutes());

        // 向量存储配置
        log.info("【向量存储配置】");
        log.info("  存储类型: {}", optimProperties.getVectorStore().getType());

        // 成本监控
        log.info("【成本监控配置】");
        log.info("  启用状态: {}", optimProperties.getCostMonitor().isEnabled() ? "已启用" : "已禁用");
        log.info("  每日告警阈值: {} 元", optimProperties.getCostMonitor().getDailyCostAlertThreshold());

        // 讯飞语音配置
        log.info("【讯飞语音配置】");
        log.info("  讯飞 App ID: {}", voiceProperties.getXfyunAppId());
        log.info("  讯飞 API Secret: ***{}",
                voiceProperties.getXfyunApiSecret() != null && voiceProperties.getXfyunApiSecret().length() > 4
                        ? voiceProperties.getXfyunApiSecret().substring(voiceProperties.getXfyunApiSecret().length() - 4)
                        : "未配置");
        log.info("  讯飞 API Key: ***{}",
                voiceProperties.getXfyunApiKey() != null && voiceProperties.getXfyunApiKey().length() > 4
                        ? voiceProperties.getXfyunApiKey().substring(voiceProperties.getXfyunApiKey().length() - 4)
                        : "未配置");
        log.info("  TTS发音人: {}", voiceProperties.getXfyunTtsVoice());
        log.info("  TTS启用状态: {}", voiceProperties.isEnableTts() ? "已启用" : "已禁用");
        log.info("  模拟模式: {}", voiceProperties.isMockMode() ? "开启" : "关闭");

        // 当前环境
        log.info("【运行环境】");
        log.info("  Spring Profiles: {}", Arrays.toString(environment.getActiveProfiles()));
        log.info("  服务端口: {}", environment.getProperty("server.port", "8080"));

        log.info("========================================");
    }
}
