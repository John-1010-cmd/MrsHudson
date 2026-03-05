package com.mrshudson.optim;

import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.optim.cache.SemanticCacheService;
import com.mrshudson.optim.cache.ToolCacheManager;
import com.mrshudson.optim.compress.CompressionConfig;
import com.mrshudson.optim.compress.ConversationSummarizer;
import com.mrshudson.optim.intent.IntentRouter;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.optim.monitor.CostMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优化层集成测试
 * 验证各优化层协同工作
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.redis.host=localhost",
    "spring.redis.port=6379",
    "optim.semantic-cache.enabled=true",
    "optim.tool-cache.enabled=true",
    "optim.intent-router.enabled=true",
    "optim.compression.enabled=true",
    "optim.cost-monitor.enabled=true"
})
public class OptimIntegrationTest {

    @Autowired
    private SemanticCacheService semanticCacheService;

    @Autowired
    private ToolCacheManager toolCacheManager;

    @Autowired
    private IntentRouter intentRouter;

    @Autowired
    private CostMonitorService costMonitorService;

    @Autowired
    private ConversationSummarizer conversationSummarizer;

    @Autowired
    private CompressionConfig compressionConfig;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final Long TEST_USER_ID = 999L;
    private static final String TEST_CONVERSATION_ID = "test-conversation-123";

    @BeforeEach
    void setUp() {
        // 清理测试数据
        semanticCacheService.clearAll(TEST_USER_ID);
        toolCacheManager.invalidate(null, null);

        // 重置统计
        if (semanticCacheService instanceof com.mrshudson.optim.cache.impl.SemanticCacheServiceImpl) {
            ((com.mrshudson.optim.cache.impl.SemanticCacheServiceImpl) semanticCacheService).resetStats();
        }
    }

    /**
     * 测试语义缓存命中
     * 相同查询第二次应该命中缓存
     */
    @Test
    void testSemanticCacheHit() {
        String query = "北京天气怎么样";
        String response = "北京今天晴，温度25°C，适合出行。";

        // 第一次查询 - 缓存未命中
        Optional<String> firstResult = semanticCacheService.get(TEST_USER_ID, query);
        assertTrue(firstResult.isEmpty(), "首次查询应该缓存未命中");

        // 存入缓存
        semanticCacheService.put(TEST_USER_ID, query, response);

        // 第二次查询 - 应该命中缓存
        Optional<String> secondResult = semanticCacheService.get(TEST_USER_ID, query);
        assertTrue(secondResult.isPresent(), "第二次查询应该命中缓存");
        assertEquals(response, secondResult.get(), "缓存响应应该与存入的一致");

        // 验证统计
        SemanticCacheService.CacheStats stats = semanticCacheService.getStats(TEST_USER_ID);
        assertTrue(stats.getHitRate() > 0, "缓存命中率应该大于0");
    }

    /**
     * 测试意图路由 - 天气查询
     */
    @Test
    void testIntentRouter_WeatherQuery() {
        String query = "北京天气怎么样";

        RouteResult result = intentRouter.route(TEST_USER_ID, query);

        assertNotNull(result, "路由结果不应为空");
        assertEquals(IntentType.WEATHER_QUERY, result.getIntentType(),
                "天气查询应该被识别为WEATHER_QUERY");

        // 如果是规则层处理，应该直接返回结果
        if ("rule".equals(result.getRouterLayer())) {
            assertTrue(result.isHandled() || result.getResponse() != null,
                    "规则层处理应该返回响应或标记为已处理");
        }
    }

    /**
     * 测试意图路由 - 日历查询
     */
    @Test
    void testIntentRouter_CalendarQuery() {
        String query = "我今天有什么日程";

        RouteResult result = intentRouter.route(TEST_USER_ID, query);

        assertNotNull(result, "路由结果不应为空");
        assertEquals(IntentType.CALENDAR_QUERY, result.getIntentType(),
                "日历查询应该被识别为CALENDAR_QUERY");
    }

    /**
     * 测试意图路由 - 待办查询
     */
    @Test
    void testIntentRouter_TodoQuery() {
        String query = "我有什么待办事项";

        RouteResult result = intentRouter.route(TEST_USER_ID, query);

        assertNotNull(result, "路由结果不应为空");
        assertEquals(IntentType.TODO_QUERY, result.getIntentType(),
                "待办查询应该被识别为TODO_QUERY");
    }

    /**
     * 测试意图路由 - 闲聊问候
     */
    @Test
    void testIntentRouter_SmallTalk() {
        String query = "你好";

        RouteResult result = intentRouter.route(TEST_USER_ID, query);

        assertNotNull(result, "路由结果不应为空");
        assertEquals(IntentType.SMALL_TALK, result.getIntentType(),
                "问候应该被识别为SMALL_TALK");
        assertTrue(result.isHandled(), "闲聊应该被直接处理");
        assertNotNull(result.getResponse(), "闲聊应该返回响应");
    }

    /**
     * 测试对话压缩触发
     * 消息数超过阈值时触发压缩
     */
    @Test
    void testConversationCompression() {
        // 创建超过阈值的消息列表
        int triggerThreshold = compressionConfig.getTriggerThreshold();
        List<Message> messages = new ArrayList<>();

        // 添加超过阈值的消息
        for (int i = 0; i < triggerThreshold + 5; i++) {
            if (i % 2 == 0) {
                messages.add(Message.user("用户消息 " + i));
            } else {
                messages.add(Message.assistant("助手回复 " + i));
            }
        }

        // 验证需要压缩
        assertTrue(conversationSummarizer.needsCompression(messages),
                "消息数超过阈值时应该需要压缩");

        // 执行压缩
        List<Message> compressed = conversationSummarizer.compress(messages);

        // 验证压缩结果
        assertNotNull(compressed, "压缩结果不应为空");
        assertTrue(compressed.size() < messages.size(),
                "压缩后消息数应该减少");
    }

    /**
     * 测试对话不压缩
     * 消息数未超过阈值时不触发压缩
     */
    @Test
    void testConversationNoCompression() {
        int triggerThreshold = compressionConfig.getTriggerThreshold();
        List<Message> messages = new ArrayList<>();

        // 添加未超过阈值的消息
        for (int i = 0; i < triggerThreshold - 2; i++) {
            if (i % 2 == 0) {
                messages.add(Message.user("用户消息 " + i));
            } else {
                messages.add(Message.assistant("助手回复 " + i));
            }
        }

        // 验证不需要压缩
        assertFalse(conversationSummarizer.needsCompression(messages),
                "消息数未超过阈值时不应该需要压缩");
    }

    /**
     * 测试工具缓存
     * 重复查询同一城市天气命中缓存
     */
    @Test
    void testToolCache() {
        String toolName = "weather";
        String paramsHash = "beijing";
        Object result = Map.of(
                "city", "北京",
                "temperature", "25°C",
                "weather", "晴"
        );

        // 第一次查询 - 缓存未命中
        Object firstResult = toolCacheManager.get(toolName, paramsHash);
        assertNull(firstResult, "首次查询应该缓存未命中");

        // 存入缓存
        toolCacheManager.put(toolName, paramsHash, result, 10);

        // 第二次查询 - 应该命中缓存
        Object secondResult = toolCacheManager.get(toolName, paramsHash);
        assertNotNull(secondResult, "第二次查询应该命中缓存");
        assertEquals(result, secondResult, "缓存结果应该与存入的一致");

        // 验证缓存存在
        assertTrue(toolCacheManager.exists(toolName, paramsHash),
                "缓存应该存在");
    }

    /**
     * 测试成本监控 - 记录AI调用
     */
    @Test
    void testCostMonitoring_RecordAiCall() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();

        // 记录一次AI调用
        costMonitorService.recordAiCall(
                TEST_USER_ID,
                123L,
                100,  // inputTokens
                50,   // outputTokens
                new BigDecimal("0.015"),
                "kimi-for-coding",
                "chat",
                "full_ai"
        );

        // 获取今日成本
        BigDecimal todayCost = costMonitorService.getTodayCost(TEST_USER_ID);
        assertNotNull(todayCost, "今日成本不应为空");
        assertTrue(todayCost.compareTo(BigDecimal.ZERO) >= 0,
                "今日成本应该大于等于0");
    }

    /**
     * 测试成本监控 - 记录缓存命中
     */
    @Test
    void testCostMonitoring_RecordCacheHit() {
        // 记录缓存命中
        costMonitorService.recordCacheHit(
                TEST_USER_ID,
                123L,
                "kimi-for-coding",
                "semantic_cache"
        );

        // 获取统计信息
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> stats = costMonitorService.getUserStatistics(
                TEST_USER_ID,
                now.minusDays(1),
                now.plusDays(1)
        );

        assertNotNull(stats, "统计信息不应为空");
    }

    /**
     * 测试成本监控 - 获取统计
     */
    @Test
    void testCostMonitoring_GetStatistics() {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(7);

        // 获取系统统计
        Map<String, Object> systemStats = costMonitorService.getStatistics(startTime, endTime);
        assertNotNull(systemStats, "系统统计不应为空");

        // 获取用户统计
        Map<String, Object> userStats = costMonitorService.getUserStatistics(TEST_USER_ID, startTime, endTime);
        assertNotNull(userStats, "用户统计不应为空");
    }

    /**
     * 测试完整优化流程
     * 模拟一次完整的对话流程，验证各优化层协同工作
     */
    @Test
    void testCompleteOptimizationFlow() {
        String query = "上海天气如何";

        // 1. 检查语义缓存
        Optional<String> cachedResponse = semanticCacheService.get(TEST_USER_ID, query);

        if (cachedResponse.isEmpty()) {
            // 2. 意图路由
            RouteResult routeResult = intentRouter.route(TEST_USER_ID, query);
            assertNotNull(routeResult, "路由结果不应为空");

            // 3. 如果不是直接处理，模拟AI调用并记录成本
            if (!routeResult.isHandled()) {
                costMonitorService.recordAiCall(
                        TEST_USER_ID,
                        null,
                        50,
                        100,
                        new BigDecimal("0.01"),
                        "kimi-for-coding",
                        "chat",
                        routeResult.getRouterLayer()
                );

                // 4. 模拟响应并存入语义缓存
                String aiResponse = "上海今天多云，温度22°C。";
                semanticCacheService.put(TEST_USER_ID, query, aiResponse);

                // 5. 验证缓存已存入
                Optional<String> cached = semanticCacheService.get(TEST_USER_ID, query);
                assertTrue(cached.isPresent(), "响应应该已存入缓存");
                assertEquals(aiResponse, cached.get(), "缓存内容应该一致");
            } else {
                // 记录缓存命中（意图层处理）
                costMonitorService.recordCacheHit(
                        TEST_USER_ID,
                        null,
                        "kimi-for-coding",
                        "intent_router"
                );
            }
        } else {
            // 记录语义缓存命中
            costMonitorService.recordCacheHit(
                    TEST_USER_ID,
                    null,
                    "kimi-for-coding",
                    "semantic_cache"
            );
        }

        // 验证统计数据
        SemanticCacheService.CacheStats cacheStats = semanticCacheService.getStats(TEST_USER_ID);
        assertNotNull(cacheStats, "缓存统计不应为空");
    }

    /**
     * 测试工具缓存失效
     */
    @Test
    void testToolCacheInvalidation() {
        String toolName = "calendar";
        String paramsHash = "user_123_today";
        Object result = Map.of("events", List.of("会议1", "会议2"));

        // 存入缓存
        toolCacheManager.put(toolName, paramsHash, result, 10);
        assertTrue(toolCacheManager.exists(toolName, paramsHash), "缓存应该存在");

        // 使缓存失效
        toolCacheManager.invalidateEntry(toolName, paramsHash);

        // 验证缓存已清除
        assertFalse(toolCacheManager.exists(toolName, paramsHash), "缓存应该已清除");
    }

    /**
     * 测试意图分类
     */
    @Test
    void testIntentClassification() {
        // 测试天气查询分类
        IntentRouter.IntentClassification weatherClass = intentRouter.classify("北京天气");
        assertEquals(IntentType.WEATHER_QUERY, weatherClass.getIntentType(),
                "应该识别为天气查询");
        assertTrue(weatherClass.getConfidence() > 0.5,
                "置信度应该大于0.5");

        // 测试日历查询分类
        IntentRouter.IntentClassification calendarClass = intentRouter.classify("今天有什么安排");
        assertEquals(IntentType.CALENDAR_QUERY, calendarClass.getIntentType(),
                "应该识别为日历查询");

        // 测试待办查询分类
        IntentRouter.IntentClassification todoClass = intentRouter.classify("我的待办事项");
        assertEquals(IntentType.TODO_QUERY, todoClass.getIntentType(),
                "应该识别为待办查询");
    }
}
