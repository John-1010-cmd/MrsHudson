package com.mrshudson.optim.fallback;

import com.mrshudson.optim.intent.IntentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 兜底决策策略
 * 判断何时需要触发兜底机制
 */
@Slf4j
@Component
public class FallbackDecisionStrategy {

    /**
     * 通用知识问答关键词
     */
    private static final String[] GENERAL_KNOWLEDGE_PATTERNS = {
        "什么是", "为什么", "怎么", "如何", "解释", "你怎么看",
        "原理", "概念", "定义", "意思是"
    };

    /**
     * 判断是否需要兜底
     *
     * @param message 用户消息
     * @param intent 识别的意图类型
     * @param toolResult 工具执行结果（可能为 null 或失败）
     * @return boolean 是否需要兜底
     */
    public boolean needFallback(String message, IntentType intent, Object toolResult) {
        // 1. 非工具类意图（如通用对话、闲聊），直接兜底
        if (intent == IntentType.GENERAL_CHAT || intent == IntentType.UNKNOWN) {
            log.debug("意图 {} 为非工具类意图，触发兜底", intent);
            return true;
        }

        // 2. 工具类但执行失败
        if (toolResult != null && isToolFailure(toolResult)) {
            log.debug("工具执行失败，触发兜底");
            return true;
        }

        // 3. 工具不可用或参数提取失败（toolResult 为 null 但意图是工具类）
        if (intent.isToolQuery() && toolResult == null) {
            log.debug("工具查询但工具结果为null，触发兜底");
            return true;
        }

        // 4. 用户明确要求 AI 回答（如"你怎么看"、"解释一下"）
        if (isGeneralKnowledgeQuestion(message)) {
            log.debug("检测到通用知识问答，触发兜底");
            return true;
        }

        return false;
    }

    /**
     * 判断工具结果是否表示失败
     */
    private boolean isToolFailure(Object toolResult) {
        if (toolResult == null) {
            return false;
        }
        String resultStr = toolResult.toString().toLowerCase();
        return resultStr.contains("error")
            || resultStr.contains("失败")
            || resultStr.contains("无法")
            || resultStr.contains("未找到")
            || resultStr.contains("null")
            || resultStr.contains("exception")
            || resultStr.contains("不存在")
            || resultStr.contains("invalid");
    }

    /**
     * 判断是否是通用知识问答
     */
    private boolean isGeneralKnowledgeQuestion(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (String pattern : GENERAL_KNOWLEDGE_PATTERNS) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取兜底原因描述
     */
    public String getFallbackReason(String message, IntentType intent, Object toolResult) {
        if (intent == IntentType.GENERAL_CHAT) {
            return "通用对话意图";
        }
        if (intent == IntentType.UNKNOWN) {
            return "未知意图";
        }
        if (isToolFailure(toolResult)) {
            return "工具执行失败";
        }
        if (intent.isToolQuery() && toolResult == null) {
            return "工具不可用";
        }
        if (isGeneralKnowledgeQuestion(message)) {
            return "通用知识问答";
        }
        return "其他原因";
    }
}
