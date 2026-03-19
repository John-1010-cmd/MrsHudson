package com.mrshudson.optim.quality;

import com.mrshudson.mcp.kimi.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 质量优化器
 * 根据问题复杂度自动调整 AI 参数，优化响应质量
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityOptimizer {

    private final QualityProperties qualityProperties;

    /**
     * 复杂问题特征词
     */
    private static final Set<String> COMPLEX_PATTERNS = Set.of(
        "首先", "然后", "最后", "总之", "总结",
        "详细", "具体", "解释", "分析",
        "为什么", "因为", "所以", "但是", "然而",
        "对比", "比较", "差异"
    );

    /**
     * 需要创意回答的特征词
     */
    private static final Set<String> CREATIVITY_PATTERNS = Set.of(
        "创意", "想象", "看法", "建议", "写一首", "编一个",
        "故事", "诗歌", "幽默", "笑话"
    );

    /**
     * 自动检测并优化请求参数
     *
     * @param message 用户消息
     * @param request 基础请求对象
     * @return 优化后的请求对象
     */
    public ChatRequest optimizeRequest(String message, ChatRequest request) {
        if (message == null || message.isEmpty()) {
            return request;
        }

        int originalMaxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : qualityProperties.getMaxTokens();
        double originalTemperature = request.getTemperature() != null ? request.getTemperature() : qualityProperties.getTemperature();

        Integer newMaxTokens = originalMaxTokens;
        Double newTemperature = originalTemperature;

        // 1. 检测问题复杂度
        if (isComplexQuestion(message)) {
            // 复杂问题，提升质量
            newMaxTokens = Math.min(originalMaxTokens * 2, 2000);
            newTemperature = Math.min(originalTemperature + 0.2, 0.9);
            log.debug("复杂问题检测到，maxTokens: {} -> {}, temperature: {} -> {}",
                originalMaxTokens, newMaxTokens, originalTemperature, newTemperature);
        }

        // 2. 检测是否需要创意回答
        if (needsCreativity(message)) {
            newTemperature = 0.8;
            log.debug("需要创意回答，设置 temperature = 0.8");
        }

        // 3. 应用配置模式
        qualityProperties.applyMode(qualityProperties.getMode());
        newMaxTokens = qualityProperties.getMaxTokens();
        newTemperature = qualityProperties.getTemperature();

        // 创建优化后的请求
        return ChatRequest.builder()
            .model(request.getModel())
            .messages(request.getMessages())
            .tools(request.getTools())
            .temperature(newTemperature)
            .maxTokens(newMaxTokens)
            .stream(request.getStream())
            .build();
    }

    /**
     * 检测是否是复杂问题
     */
    private boolean isComplexQuestion(String message) {
        // 长文本可能是复杂问题
        if (message.length() > qualityProperties.getComplexQuestionThreshold()) {
            return true;
        }

        // 多个问题
        if (message.contains("?") && message.split("\\?").length > 2) {
            return true;
        }

        // 多步骤问题
        if (message.contains("首先") && message.contains("然后")) {
            return true;
        }

        // 包含复杂特征词
        for (String pattern : COMPLEX_PATTERNS) {
            if (message.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测是否需要创意回答
     */
    private boolean needsCreativity(String message) {
        for (String pattern : CREATIVITY_PATTERNS) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前质量模式
     */
    public QualityProperties.Mode getCurrentMode() {
        return qualityProperties.getMode();
    }

    /**
     * 设置质量模式
     */
    public void setMode(QualityProperties.Mode mode) {
        qualityProperties.applyMode(mode);
        log.info("质量模式已切换为: {}", mode);
    }

    /**
     * 判断是否为复杂问题
     */
    public boolean isComplex(String message) {
        return isComplexQuestion(message);
    }
}
