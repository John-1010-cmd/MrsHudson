package com.mrshudson.ai;

/**
 * AI提供者枚举
 * 当前支持: Kimi
 * 备选: 智谱GLM4, 通义千问 - 暂未实现
 */
public enum AIProvider {
    /**
     * 月之暗面Kimi
     */
    KIMI("kimi", "Kimi");

    private final String code;
    private final String name;

    AIProvider(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static AIProvider fromCode(String code) {
        for (AIProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return KIMI;
    }
}
