package com.mrshudson.optim.intent.extract;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.ChatResponse;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.optim.config.OptimProperties;
import com.mrshudson.optim.intent.IntentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 轻量AI参数提取器（第2层）
 * 使用轻量级AI调用快速提取参数，限制token和超时时间
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LightweightAiExtractor implements ParameterExtractor {

    private final KimiClient kimiClient;
    private final OptimProperties optimProperties;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private static final String EXTRACTOR_NAME = "lightweight_ai";
    private static final String SYSTEM_PROMPT = """
        你是一个参数提取助手。从用户输入中提取结构化参数，只返回JSON格式。

        规则：
        1. 只返回JSON对象，不要任何解释
        2. 提取所有相关参数
        3. 无法确定的参数值设为null
        4. 不要添加markdown格式

        示例：
        用户："北京明天天气怎么样"
        输出：{"city": "北京", "date": "明天"}

        用户："帮我创建一个明天下午3点的会议"
        输出：{"title": "会议", "date": "明天", "time": "15:00"}
        """;

    @Override
    public ExtractionResult extract(String query, IntentType intentType) {
        if (!optimProperties.getIntentRouter().getLightweightAi().isEnabled()) {
            return ExtractionResult.failure("轻量AI提取器已禁用", EXTRACTOR_NAME);
        }

        int timeoutMs = optimProperties.getIntentRouter().getLightweightAi().getTimeoutMs();

        try {
            // 使用Future实现超时控制
            Future<ExtractionResult> future = executorService.submit(() -> doExtract(query, intentType));
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("轻量AI参数提取超时，查询: {}", query);
            return ExtractionResult.failure("提取超时", EXTRACTOR_NAME);
        } catch (Exception e) {
            log.error("轻量AI参数提取失败: {}", e.getMessage(), e);
            return ExtractionResult.failure("提取异常: " + e.getMessage(), EXTRACTOR_NAME);
        }
    }

    /**
     * 执行实际的参数提取
     */
    private ExtractionResult doExtract(String query, IntentType intentType) {
        try {
            // 构建提示词
            String prompt = buildPrompt(query, intentType);

            // 调用Kimi API
            List<Message> messages = List.of(
                Message.system(SYSTEM_PROMPT),
                Message.user(prompt)
            );

            ChatResponse response = kimiClient.chatCompletion(messages);

            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                return ExtractionResult.failure("AI返回空响应", EXTRACTOR_NAME);
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            if (content == null || content.isBlank()) {
                return ExtractionResult.failure("AI返回空内容", EXTRACTOR_NAME);
            }

            // 解析JSON响应
            Map<String, Object> parameters = parseJsonResponse(content);

            if (parameters == null) {
                return ExtractionResult.failure("无法解析AI返回的JSON", EXTRACTOR_NAME);
            }

            log.debug("轻量AI提取参数成功，查询: {}, 参数: {}", query, parameters);

            return ExtractionResult.builder()
                .success(true)
                .parameters(parameters)
                .extractorName(EXTRACTOR_NAME)
                .confidence(0.85)
                .build();

        } catch (Exception e) {
            log.error("轻量AI提取执行失败: {}", e.getMessage(), e);
            return ExtractionResult.failure("执行失败: " + e.getMessage(), EXTRACTOR_NAME);
        }
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(String query, IntentType intentType) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("意图类型: ").append(intentType.getDescription()).append("\n");
        prompt.append("用户输入: ").append(query).append("\n");
        prompt.append("请提取参数并返回JSON格式:");
        return prompt.toString();
    }

    /**
     * 解析JSON响应
     */
    private Map<String, Object> parseJsonResponse(String content) {
        try {
            // 清理可能的markdown代码块
            String cleaned = content.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            // 解析JSON
            return JSON.parseObject(cleaned, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON解析失败: {}, 原始内容: {}", e.getMessage(), content);
            return null;
        }
    }

    @Override
    public String getName() {
        return EXTRACTOR_NAME;
    }

    @Override
    public String getDescription() {
        return "轻量AI参数提取器 - 使用AI快速提取参数，限制token=100，超时2秒";
    }

    @Override
    public boolean supports(IntentType intentType) {
        // 支持所有非闲聊意图
        return intentType != null
            && intentType != IntentType.SMALL_TALK
            && intentType != IntentType.UNKNOWN
            && intentType != IntentType.GENERAL_CHAT;
    }
}
