package com.mrshudson.optim.intent.handler;

import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 闲聊问候处理器
 * 识别问候语，返回预设的友好回复
 * 零AI调用，纯本地处理
 */
@Slf4j
@Component
public class SmallTalkHandler extends AbstractIntentHandler {

    private final Random random = new Random();

    // 问候关键词（中英文）
    private static final List<String> GREETING_KEYWORDS = Arrays.asList(
            "你好", "您好", "在吗", "在么", "在不在",
            "hello", "hi", "hey", "howdy",
            "早上好", "上午好", "中午好", "下午好", "晚上好",
            "早安", "午安", "晚安",
            "有人吗", "在不在线", "忙吗"
    );

    // 友好回复列表
    private static final List<String> FRIENDLY_RESPONSES = Arrays.asList(
            "你好呀！我是哈德森夫人，很高兴为你服务。有什么我可以帮你的吗？",
            "在的呢！有什么需要我帮忙的吗？",
            "你好！今天过得怎么样？需要我帮你查天气、看日程或者规划路线吗？",
            "嗨！我是你的AI管家助手。有什么我可以协助你的吗？",
            "你好！我随时待命。需要查询天气、管理待办事项还是查看日历安排？",
            "在的！很高兴见到你。有什么我可以帮你的吗？",
            "你好呀！有什么我可以为你效劳的吗？",
            "嗨！准备好帮你处理各种事务了。今天有什么计划？",
            "你好！我是哈德森夫人，你的贴心助手。需要我做什么吗？",
            "在的呢！无论是查天气、看日程还是闲聊，我都在这里。"
    );

    @Override
    public IntentType getIntentType() {
        return IntentType.SMALL_TALK;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        log.debug("处理闲聊问候: userId={}, query={}", userId, query);

        // 从预设列表中随机选择一条回复
        String response = getRandomResponse();

        // 构建参数Map
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("greetingType", detectGreetingType(query));
        resultParams.put("query", query);

        // 返回闲聊结果（固定置信度1.0）
        return RouteResult.smallTalk(response);
    }

    /**
     * 获取随机回复
     */
    private String getRandomResponse() {
        int index = random.nextInt(FRIENDLY_RESPONSES.size());
        return FRIENDLY_RESPONSES.get(index);
    }

    /**
     * 检测问候类型
     */
    private String detectGreetingType(String query) {
        if (query == null || query.isEmpty()) {
            return "unknown";
        }

        String lowerQuery = query.toLowerCase();

        if (lowerQuery.contains("早上") || lowerQuery.contains("早安") || lowerQuery.contains("上午")) {
            return "morning";
        }
        if (lowerQuery.contains("中午") || lowerQuery.contains("午安")) {
            return "noon";
        }
        if (lowerQuery.contains("下午")) {
            return "afternoon";
        }
        if (lowerQuery.contains("晚上") || lowerQuery.contains("晚安")) {
            return "evening";
        }
        if (lowerQuery.startsWith("hello") || lowerQuery.startsWith("hi") || lowerQuery.startsWith("hey")) {
            return "english";
        }
        if (lowerQuery.contains("在吗") || lowerQuery.contains("在不在") || lowerQuery.contains("有人")) {
            return "availability";
        }

        return "general";
    }

    @Override
    public double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        String lowerQuery = query.toLowerCase().trim();

        // 检查是否匹配问候关键词
        for (String keyword : GREETING_KEYWORDS) {
            if (lowerQuery.contains(keyword.toLowerCase())) {
                // 如果是纯问候（短句），返回高置信度
                if (lowerQuery.length() <= 10) {
                    return 1.0;
                }
                // 如果包含问候但较长，返回中等置信度
                return 0.9;
            }
        }

        return 0.0;
    }

    @Override
    public boolean needParameterExtraction() {
        // 闲聊不需要参数提取
        return false;
    }
}
