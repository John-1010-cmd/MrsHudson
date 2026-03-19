package com.mrshudson.optim.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Token 格式化工具
 * 将 Token 使用统计格式化为友好文本
 */
@Component
public class TokenFormatter {

    private final TokenTrackerService tokenTrackerService;

    @Autowired
    public TokenFormatter(TokenTrackerService tokenTrackerService) {
        this.tokenTrackerService = tokenTrackerService;
    }

    /**
     * 格式化 Token 统计为友好文本
     *
     * @param usage Token 使用统计
     * @return 格式化的字符串
     */
    public String format(TokenUsage usage) {
        return tokenTrackerService.formatStatistics(usage);
    }

    /**
     * 格式化 Token 统计为简洁文本（无边框）
     *
     * @param usage Token 使用统计
     * @return 简洁格式字符串
     */
    public String formatSimple(TokenUsage usage) {
        if (usage == null) {
            return "";
        }

        int input = usage.getInputTokens() != null ? usage.getInputTokens() : 0;
        int output = usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
        int total = input + output;
        BigDecimal cost = tokenTrackerService.calculateCost(usage);

        return String.format("💡 本次消耗 %d tokens（输入%d/输出%d），预估成本 ¥%s",
            total, input, output, cost.setScale(4, java.math.RoundingMode.HALF_UP));
    }

    /**
     * 生成带统计的完整响应
     *
     * @param originalResponse 原始响应
     * @param usage Token 使用统计
     * @return 添加了 Token 统计的响应
     */
    public String appendStatistics(String originalResponse, TokenUsage usage) {
        if (originalResponse == null) {
            originalResponse = "";
        }
        return originalResponse + format(usage);
    }
}
