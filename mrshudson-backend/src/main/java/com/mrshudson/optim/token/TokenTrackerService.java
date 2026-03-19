package com.mrshudson.optim.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 追踪服务实现
 * 线程安全的 Token 统计服务
 */
@Slf4j
@Service
public class TokenTrackerService implements TokenTracker {

    /**
     * Token 价格（单位：元 / 1M tokens），从配置读取，默认 Kimi API 价格
     */
    @Value("${ai.pricing.input:12.00}")
    private BigDecimal inputPrice;

    @Value("${ai.pricing.output:12.00}")
    private BigDecimal outputPrice;

    /**
     * 追踪数据存储
     */
    private final ConcurrentHashMap<String, TokenUsage> trackingStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();

    @Override
    public String startTracking() {
        String trackingId = java.util.UUID.randomUUID().toString();
        startTimes.put(trackingId, System.currentTimeMillis());
        trackingStore.put(trackingId, TokenUsage.builder()
            .timestamp(LocalDateTime.now())
            .build());
        log.debug("开始追踪 Token 使用，追踪ID: {}", trackingId);
        return trackingId;
    }

    @Override
    public void recordInputTokens(String trackingId, int tokens) {
        TokenUsage usage = trackingStore.get(trackingId);
        if (usage != null) {
            usage.setInputTokens(tokens);
            log.debug("记录输入 Token，追踪ID: {}, 数量: {}", trackingId, tokens);
        }
    }

    @Override
    public void recordOutputTokens(String trackingId, int tokens) {
        TokenUsage usage = trackingStore.get(trackingId);
        if (usage != null) {
            usage.setOutputTokens(tokens);
            log.debug("记录输出 Token，追踪ID: {}, 数量: {}", trackingId, tokens);
        }
    }

    @Override
    public void recordDuration(String trackingId, long durationMs) {
        TokenUsage usage = trackingStore.get(trackingId);
        if (usage != null) {
            usage.setDuration(durationMs);
        }
    }

    @Override
    public TokenUsage getUsage(String trackingId) {
        TokenUsage usage = trackingStore.get(trackingId);
        if (usage != null && usage.getDuration() == null) {
            Long startTime = startTimes.get(trackingId);
            if (startTime != null) {
                usage.setDuration(System.currentTimeMillis() - startTime);
            }
        }
        return usage;
    }

    @Override
    public String formatStatistics(TokenUsage usage) {
        if (usage == null) {
            return "";
        }

        int inputTokens = usage.getInputTokens() != null ? usage.getInputTokens() : 0;
        int outputTokens = usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
        int total = inputTokens + outputTokens;
        BigDecimal cost = calculateCost(usage);

        return String.format(
            "\n\n--- 💡 本次对话消耗 ---\n" +
            "📥 输入: %d tokens\n" +
            "📤 输出: %d tokens\n" +
            "📊 总计: %d tokens\n" +
            "💰 预估成本: ¥%s\n" +
            "------------------------",
            inputTokens,
            outputTokens,
            total,
            cost.setScale(4, RoundingMode.HALF_UP)
        );
    }

    @Override
    public BigDecimal calculateCost(TokenUsage usage) {
        if (usage == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal inputCost = BigDecimal.ZERO;
        BigDecimal outputCost = BigDecimal.ZERO;

        if (usage.getInputTokens() != null && usage.getInputTokens() > 0) {
            inputCost = BigDecimal.valueOf(usage.getInputTokens())
                .multiply(inputPrice)
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        }

        if (usage.getOutputTokens() != null && usage.getOutputTokens() > 0) {
            outputCost = BigDecimal.valueOf(usage.getOutputTokens())
                .multiply(outputPrice)
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        }

        return inputCost.add(outputCost);
    }

    /**
     * 估算 Token 数量（基于字符）
     * 中文字符约等于 1 token，英文约等于 4 字符 1 token
     *
     * @param text 文本内容
     * @return 估算的 Token 数量
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long chineseChars = text.chars().filter(c -> c > 127).count();
        int asciiChars = text.length() - (int) chineseChars;
        return (int) chineseChars + (asciiChars / 4);
    }
}
