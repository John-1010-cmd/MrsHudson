package com.mrshudson.optim.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 意图置信度评估器
 * 计算意图识别的置信度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentConfidenceEvaluator {

    /**
     * 高置信度阈值
     */
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;

    /**
     * 低置信度阈值
     */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.5;

    /**
     * 意图关键词映射
     */
    private static final Map<IntentType, Set<String>> INTENT_KEYWORDS = new HashMap<>();

    static {
        INTENT_KEYWORDS.put(IntentType.WEATHER_QUERY, Set.of("天气", "温度", "下雨", "下雪", "刮风", "晴", "阴", "多云", "气温"));
        INTENT_KEYWORDS.put(IntentType.CALENDAR_QUERY, Set.of("日程", "会议", "安排", "日历", "今天", "明天", "下周", "这周", "有什么"));
        INTENT_KEYWORDS.put(IntentType.TODO_QUERY, Set.of("待办", "任务", "todo", "提醒", "未完成", "要做", "完成"));
        INTENT_KEYWORDS.put(IntentType.ROUTE_QUERY, Set.of("路线", "怎么去", "怎么走", "导航", "地图", "从", "到", "距离"));
        INTENT_KEYWORDS.put(IntentType.SMALL_TALK, Set.of("你好", "您好", "在吗", "hello", "hi", "hey", "早上好", "下午好", "晚上好"));
        INTENT_KEYWORDS.put(IntentType.CALENDAR_CREATE, Set.of("创建", "添加", "新建", "安排", "会议", "日程"));
        INTENT_KEYWORDS.put(IntentType.TODO_CREATE, Set.of("创建", "添加", "新建", "待办", "任务", "提醒"));
    }

    /**
     * 计算意图置信度
     *
     * @param message 用户消息
     * @param intent 意图类型
     * @return 置信度 (0.0 - 1.0)
     */
    public double calculateConfidence(String message, IntentType intent) {
        if (message == null || message.isEmpty()) {
            return 0.0;
        }

        // 1. 关键词匹配得分
        double keywordScore = calculateKeywordScore(message, intent);

        // 2. 上下文相关性得分（简化实现）
        double contextScore = 0.8;

        // 3. 综合得分
        return keywordScore * 0.7 + contextScore * 0.3;
    }

    /**
     * 计算关键词匹配得分
     */
    private double calculateKeywordScore(String message, IntentType intent) {
        Set<String> keywords = INTENT_KEYWORDS.get(intent);
        if (keywords == null || keywords.isEmpty()) {
            return 0.0;
        }

        String lowerMessage = message.toLowerCase();
        int matchCount = 0;

        for (String keyword : keywords) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }

        // 匹配越多，置信度越高
        if (matchCount == 0) {
            return 0.0;
        }

        // 归一化：每个关键词贡献0.2，最高1.0
        return Math.min(1.0, matchCount * 0.2);
    }

    /**
     * 评估并返回完整结果
     *
     * @param message 用户消息
     * @param intent 意图类型
     * @return 意图识别结果
     */
    public IntentResult evaluate(String message, IntentType intent) {
        return evaluate(message, intent, null);
    }

    /**
     * 评估并返回完整结果（考虑参数提取结果）
     *
     * @param message 用户消息
     * @param intent 意图类型
     * @param extractedParams 提取到的参数（可选）
     * @return 意图识别结果
     */
    public IntentResult evaluate(String message, IntentType intent, Map<String, Object> extractedParams) {
        double confidence = calculateConfidence(message, intent);

        // 如果成功提取到关键参数，提高置信度
        if (extractedParams != null && !extractedParams.isEmpty()) {
            boolean hasKeyParam = false;
            switch (intent) {
                case TODO_CREATE:
                    hasKeyParam = extractedParams.containsKey("title") && 
                                 extractedParams.get("title") != null &&
                                 !extractedParams.get("title").toString().isEmpty();
                    break;
                case CALENDAR_CREATE:
                    hasKeyParam = extractedParams.containsKey("title");
                    break;
                case WEATHER_QUERY:
                    hasKeyParam = extractedParams.containsKey("city");
                    break;
                case ROUTE_QUERY:
                    hasKeyParam = extractedParams.containsKey("destination");
                    break;
                default:
                    hasKeyParam = !extractedParams.isEmpty();
            }
            
            if (hasKeyParam) {
                // 成功提取关键参数，置信度提升到 0.85 以上
                confidence = Math.max(confidence, 0.85);
                log.debug("成功提取关键参数，提升置信度至: {}", confidence);
            }
        }

        // 检查是否需要返回候选意图
        List<IntentType> candidates = null;
        if (confidence < HIGH_CONFIDENCE_THRESHOLD) {
            candidates = findCandidateIntents(message, intent);
        }

        return IntentResult.builder()
            .type(intent)
            .confidence(confidence)
            .candidates(candidates)
            .build();
    }

    /**
     * 查找候选意图
     */
    private List<IntentType> findCandidateIntents(String message, IntentType primaryIntent) {
        List<IntentType> candidates = new ArrayList<>();
        double primaryConfidence = calculateConfidence(message, primaryIntent);

        for (IntentType type : IntentType.values()) {
            if (type == primaryIntent || type == IntentType.UNKNOWN) {
                continue;
            }

            double confidence = calculateConfidence(message, type);
            // 如果置信度接近主要意图（差距在0.2以内），加入候选
            if (confidence > 0.3 && Math.abs(confidence - primaryConfidence) < 0.2) {
                candidates.add(type);
            }
        }

        return candidates.isEmpty() ? null : candidates;
    }

    /**
     * 判断是否是模糊意图
     */
    public boolean isAmbiguous(String message) {
        for (IntentType type : IntentType.values()) {
            if (type == IntentType.UNKNOWN) {
                continue;
            }
            double confidence = calculateConfidence(message, type);
            if (confidence >= LOW_CONFIDENCE_THRESHOLD && confidence < HIGH_CONFIDENCE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }
}
