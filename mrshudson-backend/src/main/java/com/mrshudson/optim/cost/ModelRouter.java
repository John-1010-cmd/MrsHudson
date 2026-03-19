package com.mrshudson.optim.cost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 模型路由器
 * 根据问题复杂度选择合适的模型
 */
@Slf4j
@Component
public class ModelRouter {

    /**
     * 小模型（用于简单问题）
     */
    @Value("${ai.model.small:moonshot-v1-8k}")
    private String smallModel;

    /**
     * 大模型（用于复杂问题）
     */
    @Value("${ai.model.large:moonshot-v1-32k}")
    private String largeModel;

    /**
     * 简单问题阈值（字符数）
     */
    @Value("${ai.model.simple_threshold:20}")
    private int simpleThreshold;

    /**
     * 问候语模式
     */
    private static final Set<String> GREETING_PATTERNS = Set.of(
        "你好", "您好", "在吗", "hello", "hi", "hey", 
        "早上好", "下午好", "晚上好", "再见", "拜拜",
        "吗", "么"
    );

    /**
     * 复杂问题关键词
     */
    private static final Set<String> COMPLEX_PATTERNS = Set.of(
        "为什么", "因为", "所以", "但是", "然而",
        "首先", "然后", "最后", "总之",
        "详细", "具体", "解释", "分析"
    );

    /**
     * 判断是否使用小模型
     *
     * @param message 用户消息
     * @return boolean 是否使用小模型
     */
    public boolean shouldUseSmallModel(String message) {
        if (message == null || message.isEmpty()) {
            return true;
        }

        // 1. 简单问题用小模型（短消息）
        if (message.length() <= simpleThreshold) {
            log.debug("消息长度 {} <= {}，使用小模型", message.length(), simpleThreshold);
            return true;
        }

        // 2. 问候类问题用小模型
        if (isGreeting(message)) {
            log.debug("检测到问候语，使用小模型");
            return true;
        }

        // 3. 简单问答模式（如"是什么"、"有没有"）
        if (isSimpleQuestion(message)) {
            log.debug("简单问答，使用小模型");
            return true;
        }

        // 4. 复杂问题用大模型
        if (isComplexQuestion(message)) {
            log.debug("复杂问题，使用大模型");
            return false;
        }

        // 默认使用小模型
        return true;
    }

    /**
     * 判断是否是问候语
     */
    private boolean isGreeting(String message) {
        String lowerMessage = message.toLowerCase().trim();
        
        // 检查是否以问候语结尾（大多数问候语较短）
        for (String pattern : GREETING_PATTERNS) {
            if (lowerMessage.contains(pattern) && message.length() <= 10) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否是简单问答
     */
    private boolean isSimpleQuestion(String message) {
        String[] simplePatterns = {"吗", "么", "有没有", "是不是", "能不能", "可不可以"};
        for (String pattern : simplePatterns) {
            if (message.contains(pattern)) {
                // 确保不是复杂问题
                return !isComplexQuestion(message);
            }
        }
        return false;
    }

    /**
     * 判断是否是复杂问题
     */
    private boolean isComplexQuestion(String message) {
        for (String pattern : COMPLEX_PATTERNS) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        
        // 多轮对话特征
        if (message.contains("?") && message.split("\\?").length > 1) {
            return true;
        }
        
        // 长消息可能是复杂问题
        if (message.length() > 100) {
            return true;
        }
        
        return false;
    }

    /**
     * 获取合适的模型
     *
     * @param message 用户消息
     * @return 模型名称
     */
    public String getModel(String message) {
        return shouldUseSmallModel(message) ? smallModel : largeModel;
    }

    /**
     * 获取小模型名称
     */
    public String getSmallModel() {
        return smallModel;
    }

    /**
     * 获取大模型名称
     */
    public String getLargeModel() {
        return largeModel;
    }
}
