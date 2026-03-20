package com.mrshudson.ai;

import com.mrshudson.config.AIProperties;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.minimax.MiniMaxClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI Client工厂
 * 根据配置选择KimiClient或MiniMaxClient
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIClientFactory {

    private final AIProperties aiProperties;
    private final KimiClient kimiClient;
    private final MiniMaxClient miniMaxClient;

    /**
     * 获取当前配置的AI Client
     *
     * @return AI Client实例
     */
    public Object getClient() {
        AIProvider provider = AIProvider.fromCode(aiProperties.getProvider());
        log.debug("获取AI Client，提供者: {}", provider);

        switch (provider) {
            case KIMI:
                return kimiClient;
            case MINI_MAX:
                return miniMaxClient;
            default:
                log.warn("未知的AI提供者: {}, 使用默认Kimi", provider);
                return kimiClient;
        }
    }

    /**
     * 获取KimiClient
     *
     * @return KimiClient实例
     */
    public KimiClient getKimiClient() {
        return kimiClient;
    }

    /**
     * 获取MiniMaxClient
     *
     * @return MiniMaxClient实例
     */
    public MiniMaxClient getMiniMaxClient() {
        return miniMaxClient;
    }

    /**
     * 获取当前AI提供者
     *
     * @return AI提供者
     */
    public AIProvider getCurrentProvider() {
        return AIProvider.fromCode(aiProperties.getProvider());
    }
}