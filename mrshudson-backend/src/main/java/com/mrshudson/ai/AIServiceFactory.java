package com.mrshudson.ai;

import com.mrshudson.config.AIProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI服务工厂
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIServiceFactory {

    private final AIProperties aiProperties;
    private final List<AIService> aiServices;
    private final Map<AIProvider, AIService> serviceMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (AIService service : aiServices) {
            serviceMap.put(service.getProvider(), service);
            log.info("注册AI服务: {}", service.getProvider().getName());
        }
    }

    /**
     * 获取当前配置的AI服务
     *
     * @return AI服务实例
     */
    public AIService getService() {
        AIProvider provider = AIProvider.fromCode(aiProperties.getProvider());
        AIService service = serviceMap.get(provider);
        if (service == null) {
            log.warn("未找到AI服务: {}, 使用默认Kimi", provider);
            service = serviceMap.get(AIProvider.KIMI);
        }
        return service;
    }

    /**
     * 获取指定提供者的AI服务
     *
     * @param provider AI提供者
     * @return AI服务实例
     */
    public AIService getService(AIProvider provider) {
        AIService service = serviceMap.get(provider);
        if (service == null) {
            throw new RuntimeException("未找到AI服务: " + provider.getName());
        }
        return service;
    }

    /**
     * 获取当前AI提供者
     *
     * @return AI提供者
     */
    public AIProvider getCurrentProvider() {
        return AIProvider.fromCode(aiProperties.getProvider());
    }

    /**
     * 获取所有可用的AI提供者
     *
     * @return AI提供者列表
     */
    public List<AIProvider> getAvailableProviders() {
        return serviceMap.keySet().stream().toList();
    }
}
