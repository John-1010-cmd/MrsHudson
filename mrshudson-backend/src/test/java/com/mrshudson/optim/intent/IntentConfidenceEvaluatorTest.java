package com.mrshudson.optim.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 意图置信度评估器单元测试
 */
class IntentConfidenceEvaluatorTest {

    private IntentConfidenceEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new IntentConfidenceEvaluator();
    }

    @Nested
    @DisplayName("calculateConfidence - 置信度计算测试")
    class CalculateConfidenceTests {

        @Test
        @DisplayName("天气查询应返回一定置信度")
        void weatherQueryShouldReturnSomeConfidence() {
            double confidence = evaluator.calculateConfidence("北京天气怎么样", IntentType.WEATHER_QUERY);
            assertTrue(confidence > 0, "天气查询应返回大于0的置信度");
        }

        @Test
        @DisplayName("日历查询应返回一定置信度")
        void calendarQueryShouldReturnSomeConfidence() {
            double confidence = evaluator.calculateConfidence("今天有什么日程", IntentType.CALENDAR_QUERY);
            assertTrue(confidence > 0, "日历查询应返回大于0的置信度");
        }

        @Test
        @DisplayName("待办查询应返回一定置信度")
        void todoQueryShouldReturnSomeConfidence() {
            double confidence = evaluator.calculateConfidence("我有什么待办事项", IntentType.TODO_QUERY);
            assertTrue(confidence > 0, "待办查询应返回大于0的置信度");
        }

        @Test
        @DisplayName("闲聊问候应返回一定置信度")
        void smallTalkShouldReturnSomeConfidence() {
            double confidence = evaluator.calculateConfidence("你好", IntentType.SMALL_TALK);
            assertTrue(confidence > 0, "闲聊应返回大于0的置信度");
        }

        @Test
        @DisplayName("空消息应返回零置信度")
        void emptyMessageShouldReturnZeroConfidence() {
            double confidence = evaluator.calculateConfidence("", IntentType.WEATHER_QUERY);
            assertEquals(0.0, confidence, "空消息应返回零置信度");
        }

        @Test
        @DisplayName("null消息应返回零置信度")
        void nullMessageShouldReturnZeroConfidence() {
            double confidence = evaluator.calculateConfidence(null, IntentType.WEATHER_QUERY);
            assertEquals(0.0, confidence, "null消息应返回零置信度");
        }

        @Test
        @DisplayName("不匹配的意图应返回较低置信度")
        void mismatchedIntentShouldReturnLowerConfidence() {
            double confidence = evaluator.calculateConfidence("今天天气不错", IntentType.CALENDAR_QUERY);
            assertTrue(confidence < 0.8, "不匹配的意图置信度应低于0.8");
        }
    }

    @Nested
    @DisplayName("evaluate - 完整评估结果测试")
    class EvaluateTests {

        @Test
        @DisplayName("评估结果应包含置信度")
        void evaluateResultShouldContainConfidence() {
            IntentResult result = evaluator.evaluate("天气查询", IntentType.WEATHER_QUERY);
            assertTrue(result.getConfidence() > 0, "评估结果应包含置信度");
        }

        @Test
        @DisplayName("评估应返回意图类型")
        void evaluateShouldReturnIntentType() {
            IntentResult result = evaluator.evaluate("天气查询", IntentType.WEATHER_QUERY);
            assertEquals(IntentType.WEATHER_QUERY, result.getType());
        }
    }

    @Nested
    @DisplayName("isAmbiguous - 模糊意图判断测试")
    class IsAmbiguousTests {

        @Test
        @DisplayName("模糊消息应被识别为模糊意图")
        void ambiguousMessageShouldReturnTrue() {
            boolean ambiguous = evaluator.isAmbiguous("今天");
            assertNotNull(ambiguous);
        }

        @Test
        @DisplayName("明确消息不应被识别为模糊意图")
        void clearMessageShouldReturnFalse() {
            boolean ambiguous = evaluator.isAmbiguous("北京今天天气怎么样");
            assertFalse(ambiguous, "明确的天气查询不应是模糊意图");
        }
    }
}
