package com.mrshudson.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI提供者列表响应
 */
@Data
@Builder
public class AIProviderResponse {

    /**
     * 当前使用的AI提供者
     */
    private String currentProvider;

    /**
     * 可用的AI提供者列表
     */
    private List<ProviderInfo> providers;

    @Data
    @Builder
    public static class ProviderInfo {
        private String code;
        private String name;
    }
}
