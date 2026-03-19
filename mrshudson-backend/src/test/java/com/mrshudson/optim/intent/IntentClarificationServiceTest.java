package com.mrshudson.optim.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 意图澄清服务单元测试
 */
class IntentClarificationServiceTest {

    private IntentClarificationService clarificationService;

    @BeforeEach
    void setUp() {
        clarificationService = new IntentClarificationService();
    }

    @Nested
    @DisplayName("buildClarification - 澄清问题构建测试")
    class BuildClarificationTests {

        @Test
        @DisplayName("null结果应返回null")
        void nullResultShouldReturnNull() {
            String clarification = clarificationService.buildClarification("test", null);
            assertNull(clarification);
        }

        @Test
        @DisplayName("高置信度且无候选应返回null")
        void highConfidenceNoCandidatesShouldReturnNull() {
            IntentResult result = IntentResult.success(IntentType.WEATHER_QUERY, 0.95);
            String clarification = clarificationService.buildClarification("北京天气", result);
            assertNull(clarification, "高置信度不需要澄清");
        }

        @Test
        @DisplayName("模糊意图应返回多选澄清")
        void ambiguousIntentShouldReturnMultipleChoice() {
            IntentResult result = IntentResult.needsClarify(
                IntentType.WEATHER_QUERY, 0.6,
                List.of(IntentType.CALENDAR_QUERY, IntentType.TODO_QUERY)
            );

            String clarification = clarificationService.buildClarification("今天", result);

            assertNotNull(clarification);
            assertTrue(clarification.contains("不太确定"));
        }

        @Test
        @DisplayName("低置信度应返回确认澄清")
        void lowConfidenceShouldReturnConfirmation() {
            // 使用置信度低于0.8的情况
            IntentResult result = IntentResult.success(IntentType.WEATHER_QUERY, 0.5);

            String clarification = clarificationService.buildClarification("天气", result);

            assertNotNull(clarification);
            assertTrue(clarification.contains("天气查询") || clarification.contains("如果不是"));
        }

        @Test
        @DisplayName("意图结果应正确判断是否需要澄清")
        void intentResultShouldCorrectlyJudgeClarification() {
            // 置信度 >= 0.8 是高置信度，不需要澄清
            IntentResult highConfidence = IntentResult.success(IntentType.WEATHER_QUERY, 0.8);
            assertFalse(highConfidence.needsClarification());

            // 置信度 < 0.8 是低置信度，需要澄清
            IntentResult lowConfidence = IntentResult.success(IntentType.WEATHER_QUERY, 0.7);
            assertTrue(lowConfidence.needsClarification());

            // 有候选意图也需要澄清
            IntentResult withCandidates = IntentResult.needsClarify(
                IntentType.WEATHER_QUERY, 0.9,
                List.of(IntentType.CALENDAR_QUERY)
            );
            assertTrue(withCandidates.needsClarification());
        }
    }

    @Nested
    @DisplayName("getIntentCandidatesDescription - 候选意图描述测试")
    class GetIntentCandidatesDescriptionTests {

        @Test
        @DisplayName("null输入应返回空字符串")
        void nullInputShouldReturnEmptyString() {
            String result = clarificationService.getIntentCandidatesDescription(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("空列表应返回空字符串")
        void emptyListShouldReturnEmptyString() {
            String result = clarificationService.getIntentCandidatesDescription(List.of());
            assertEquals("", result);
        }

        @Test
        @DisplayName("应正确格式化候选意图")
        void shouldFormatCandidatesCorrectly() {
            String result = clarificationService.getIntentCandidatesDescription(
                List.of(IntentType.WEATHER_QUERY, IntentType.CALENDAR_QUERY)
            );

            assertTrue(result.contains("1"));
            assertTrue(result.contains("2"));
            assertTrue(result.contains("天气查询"));
            assertTrue(result.contains("日历查询"));
        }
    }
}
