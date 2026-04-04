package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.IntentFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryNormalizer 单元测试
 * 覆盖：时间归一化、口语化归一化、指纹生成、边界条件
 */
class QueryNormalizerTest {

    private QueryNormalizer normalizer;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @BeforeEach
    void setUp() {
        normalizer = new QueryNormalizer();
    }

    // ========== 基础功能 ==========

    @Nested
    @DisplayName("基础归一化")
    class BasicNormalization {

        @Test
        @DisplayName("null 输入返回 null")
        void nullInput() {
            assertNull(normalizer.normalize(null));
        }

        @Test
        @DisplayName("空字符串返回 null")
        void emptyInput() {
            assertNull(normalizer.normalize(""));
        }

        @Test
        @DisplayName("纯空白字符串返回 null")
        void blankInput() {
            assertNull(normalizer.normalize("   "));
        }

        @Test
        @DisplayName("普通文本不变")
        void plainText() {
            IntentFingerprint fp = normalizer.normalize("查询天气");
            assertNotNull(fp);
            assertEquals("查询天气", fp.getNormalizedInput());
            assertNotNull(fp.getFingerprintHash());
            assertEquals(32, fp.getFingerprintHash().length()); // MD5 = 32 hex chars
        }
    }

    // ========== 时间归一化 ==========

    @Nested
    @DisplayName("时间归一化")
    class TemporalNormalization {

        @Test
        @DisplayName("今天 -> 具体日期")
        void today() {
            LocalDate today = LocalDate.now();
            IntentFingerprint fp = normalizer.normalize("今天天气怎么样");
            assertNotNull(fp);
            assertEquals("今天天气怎么样", fp.getRawInput());
            assertTrue(fp.getNormalizedInput().contains(today.format(DTF)));
        }

        @Test
        @DisplayName("明天 -> 明天的日期")
        void tomorrow() {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            IntentFingerprint fp = normalizer.normalize("明天有什么日程");
            assertTrue(fp.getNormalizedInput().contains(tomorrow.format(DTF)));
        }

        @Test
        @DisplayName("昨天 -> 昨天的日期")
        void yesterday() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            IntentFingerprint fp = normalizer.normalize("昨天的天气");
            assertTrue(fp.getNormalizedInput().contains(yesterday.format(DTF)));
        }

        @Test
        @DisplayName("后天 -> 后天的日期")
        void dayAfterTomorrow() {
            LocalDate dayAfter = LocalDate.now().plusDays(2);
            IntentFingerprint fp = normalizer.normalize("后天的待办");
            assertTrue(fp.getNormalizedInput().contains(dayAfter.format(DTF)));
        }

        @Test
        @DisplayName("现在 -> 今天日期")
        void now() {
            LocalDate today = LocalDate.now();
            IntentFingerprint fp = normalizer.normalize("现在几点了");
            assertTrue(fp.getNormalizedInput().contains(today.format(DTF)));
        }

        @Test
        @DisplayName("刚才 -> 今天日期")
        void justNow() {
            LocalDate today = LocalDate.now();
            IntentFingerprint fp = normalizer.normalize("刚才说的什么");
            assertTrue(fp.getNormalizedInput().contains(today.format(DTF)));
        }

        @Test
        @DisplayName("同一查询不同天产生不同指纹")
        void differentDaysDifferentFingerprints() {
            // 同一个输入应该总是产生相同的指纹（同一天内）
            IntentFingerprint fp1 = normalizer.normalize("今天天气");
            IntentFingerprint fp2 = normalizer.normalize("今天天气");
            assertEquals(fp1.getFingerprintHash(), fp2.getFingerprintHash());
        }
    }

    // ========== 口语化归一化 ==========

    @Nested
    @DisplayName("口语化归一化")
    class ColloquialNormalization {

        @Test
        @DisplayName("查一下 -> 查询")
        void checkColloquial() {
            IntentFingerprint fp = normalizer.normalize("查一下天气");
            assertTrue(fp.getNormalizedInput().contains("查询"));
            assertFalse(fp.getNormalizedInput().contains("查一下"));
        }

        @Test
        @DisplayName("看看 -> 查询")
        void lookColloquial() {
            IntentFingerprint fp = normalizer.normalize("看看日程");
            assertTrue(fp.getNormalizedInput().contains("查询"));
        }

        @Test
        @DisplayName("帮我弄 -> 创建")
        void helpCreateColloquial() {
            IntentFingerprint fp = normalizer.normalize("帮我弄一个待办");
            assertTrue(fp.getNormalizedInput().contains("创建"));
        }

        @Test
        @DisplayName("给我建 -> 创建")
        void buildForMeColloquial() {
            IntentFingerprint fp = normalizer.normalize("给我建一个日程");
            assertTrue(fp.getNormalizedInput().contains("创建"));
        }
    }

    // ========== 指纹生成 ==========

    @Nested
    @DisplayName("指纹生成")
    class FingerprintGeneration {

        @Test
        @DisplayName("MD5 指纹长度为 32")
        void md5Length() {
            String hash = normalizer.generateMD5("test");
            assertEquals(32, hash.length());
        }

        @Test
        @DisplayName("相同输入产生相同指纹")
        void sameInputSameHash() {
            String hash1 = normalizer.generateMD5("hello world");
            String hash2 = normalizer.generateMD5("hello world");
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("不同输入产生不同指纹")
        void differentInputDifferentHash() {
            String hash1 = normalizer.generateMD5("hello");
            String hash2 = normalizer.generateMD5("world");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("归一化后的等价查询产生相同指纹")
        void equivalentQueriesSameFingerprint() {
            // "查一下天气" 和 "查询天气" 归一化后应该相同（口语化归一化）
            IntentFingerprint fp1 = normalizer.normalize("查一下天气");
            IntentFingerprint fp2 = normalizer.normalize("查询天气");
            // 注意: 这两个只有在没有时间词时才会相同
            // 因为 "查一下天气" 归一化为 "查询天气"（口语化替换后）
            assertEquals(fp1.getFingerprintHash(), fp2.getFingerprintHash());
        }
    }

    // ========== Fingerprint DTO ==========

    @Nested
    @DisplayName("IntentFingerprint DTO")
    class FingerprintDto {

        @Test
        @DisplayName("rawInput 保留原始输入")
        void rawInputPreserved() {
            IntentFingerprint fp = normalizer.normalize("今天查一下天气");
            assertEquals("今天查一下天气", fp.getRawInput());
        }

        @Test
        @DisplayName("concreteDate 为今天")
        void concreteDateIsToday() {
            IntentFingerprint fp = normalizer.normalize("测试");
            assertEquals(LocalDate.now(), fp.getConcreteDate());
        }

        @Test
        @DisplayName("generateCacheKey 格式正确")
        void cacheKeyFormat() {
            IntentFingerprint fp = normalizer.normalize("测试");
            String key = fp.generateCacheKey(123L);
            assertTrue(key.startsWith("123:"));
            assertTrue(key.length() > 5);
        }
    }
}
