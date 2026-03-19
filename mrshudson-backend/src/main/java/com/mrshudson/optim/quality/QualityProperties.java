package com.mrshudson.optim.quality;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 质量优化配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.quality")
public class QualityProperties {

    /**
     * 质量模式
     */
    private Mode mode = Mode.BALANCED;

    /**
     * 最大 token 数
     */
    private int maxTokens = 800;

    /**
     * 温度参数（创造性程度）
     */
    private double temperature = 0.3;

    /**
     * 是否启用完整上下文
     */
    private boolean enableFullContext = true;

    /**
     * 复杂问题阈值（字符数）
     */
    private int complexQuestionThreshold = 200;

    /**
     * 质量模式枚举
     */
    public enum Mode {
        /**
         * 速度优先
         */
        SPEED,
        /**
         * 平衡模式（默认）
         */
        BALANCED,
        /**
         * 质量优先
         */
        QUALITY
    }

    /**
     * 应用指定模式
     */
    public void applyMode(Mode mode) {
        this.mode = mode;
        switch (mode) {
            case SPEED -> {
                this.maxTokens = 400;
                this.temperature = 0.2;
                this.enableFullContext = false;
            }
            case BALANCED -> {
                this.maxTokens = 800;
                this.temperature = 0.3;
                this.enableFullContext = true;
            }
            case QUALITY -> {
                this.maxTokens = 2000;
                this.temperature = 0.7;
                this.enableFullContext = true;
            }
        }
        QualityModeHolder.setMode(mode);
    }

    /**
     * 获取模式名称
     */
    public String getModeName() {
        return mode.name();
    }

    /**
     * 线程本地模式持有器（用于在异步环境中传递模式）
     */
    public static class QualityModeHolder {
        private static final ThreadLocal<Mode> MODE_HOLDER = new ThreadLocal<>();

        public static void setMode(Mode mode) {
            MODE_HOLDER.set(mode);
        }

        public static Mode getMode() {
            return MODE_HOLDER.get();
        }

        public static void clear() {
            MODE_HOLDER.remove();
        }
    }
}
