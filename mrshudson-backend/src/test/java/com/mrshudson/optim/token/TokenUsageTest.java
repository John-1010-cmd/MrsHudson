package com.mrshudson.optim.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token使用量单元测试
 */
class TokenUsageTest {

    @Nested
    @DisplayName("TokenUsage 创建和计算测试")
    class TokenUsageTests {

        @Test
        @DisplayName("应该正确计算总Token数")
        void shouldCalculateTotalTokens() {
            TokenUsage usage = TokenUsage.builder()
                .inputTokens(100)
                .outputTokens(50)
                .build();

            assertEquals(150, usage.getTotalTokens());
        }

        @Test
        @DisplayName("应该正确计算成本")
        void shouldCalculateCost() {
            TokenUsage usage = TokenUsage.builder()
                .inputTokens(1000)
                .outputTokens(500)
                .build();

            // 价格单位是 元/1M tokens
            BigDecimal inputPrice = new BigDecimal("1.0");  // 1元/1M
            BigDecimal outputPrice = new BigDecimal("2.0");  // 2元/1M

            // 1000 * 1.0 / 1_000_000 + 500 * 2.0 / 1_000_000 = 0.001 + 0.001 = 0.002
            BigDecimal cost = usage.calculateCost(inputPrice, outputPrice);

            assertEquals(0, new BigDecimal("0.002").compareTo(cost), "成本计算应该正确");
        }

        @Test
        @DisplayName("零Token应返回零成本")
        void zeroTokensShouldReturnZeroCost() {
            TokenUsage usage = TokenUsage.builder()
                .inputTokens(0)
                .outputTokens(0)
                .build();

            BigDecimal cost = usage.calculateCost(new BigDecimal("1.0"), new BigDecimal("2.0"));

            assertEquals(BigDecimal.ZERO, cost);
        }

        @Test
        @DisplayName("null Token值应视为零")
        void nullTokenValuesShouldBeTreatedAsZero() {
            TokenUsage usage = TokenUsage.builder()
                .inputTokens(null)
                .outputTokens(null)
                .build();

            assertEquals(0, usage.getTotalTokens());
        }

        @Test
        @DisplayName("只设置输入Token时应正常计算")
        void onlyInputTokensShouldCalculateCorrectly() {
            TokenUsage usage = TokenUsage.builder()
                .inputTokens(1000)
                .outputTokens(0)
                .build();

            BigDecimal cost = usage.calculateCost(new BigDecimal("1.0"), new BigDecimal("2.0"));

            // 1000 * 1.0 / 1_000_000 = 0.001
            assertEquals(0, new BigDecimal("0.001").compareTo(cost), "成本计算应该正确");
        }

        @Test
        @DisplayName("应该正确设置模型名称")
        void shouldSetModelName() {
            TokenUsage usage = TokenUsage.builder()
                .model("moonshot-v1-8k")
                .build();

            assertEquals("moonshot-v1-8k", usage.getModel());
        }

        @Test
        @DisplayName("应该正确设置时长")
        void shouldSetDuration() {
            TokenUsage usage = TokenUsage.builder()
                .duration(1500L)
                .build();

            assertEquals(1500L, usage.getDuration());
        }
    }
}
