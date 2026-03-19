package com.mrshudson.mcp.kimi;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.PropertyFilter;
import com.mrshudson.mcp.kimi.dto.ChatRequest;
import com.mrshudson.mcp.kimi.dto.ChatResponse;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.optim.config.OptimProperties;
import com.mrshudson.optim.token.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kimi API客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KimiClient {

    private final KimiProperties kimiProperties;
    private final OptimProperties optimProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 属性过滤器：过滤掉 Message 对象中为 null 的 name 字段
     * Kimi API 要求 tool 消息不能包含 name 字段
     */
    private static final PropertyFilter MESSAGE_NAME_FILTER = (object, name, value) -> {
        if (object instanceof Message && "name".equals(name) && value == null) {
            return false; // 跳过 null 的 name 字段
        }
        return true;
    };

    /**
     * 对话完成
     *
     * @param messages 消息列表
     * @param tools    工具列表（可选）
     * @return 对话响应
     */
    public ChatResponse chatCompletion(List<Message> messages, List<Tool> tools) {
        String url = kimiProperties.getBaseUrl() + "/chat/completions";

        // 从配置读取参数，使用 OptimProperties 覆盖 KimiProperties
        double temperature = optimProperties.getKimiParams() != null
                ? optimProperties.getKimiParams().getTemperature()
                : kimiProperties.getTemperature();
        int maxTokens = optimProperties.getKimiParams() != null
                ? optimProperties.getKimiParams().getMaxTokens()
                : kimiProperties.getMaxTokens();

        // 构建请求
        ChatRequest request = ChatRequest.builder()
                .model(kimiProperties.getModel())
                .messages(messages)
                .tools(tools)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .stream(false)
                .build();

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(kimiProperties.getApiKey());

        // 调试：打印API Key前10个字符（隐藏敏感信息）
        String apiKey = kimiProperties.getApiKey();
        String maskedKey = apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 10) + "..."
                : "null or empty";
        log.debug("调用Kimi API，API Key: {}, 消息数: {}, 工具数: {}",
                maskedKey, messages.size(), tools != null ? tools.size() : 0);

        // 序列化请求体（保留null值字段，但过滤掉Message中为null的name字段）
        String requestBody = JSON.toJSONString(request, MESSAGE_NAME_FILTER, com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);
        log.debug("Kimi API请求体: {}", requestBody);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // 解析响应
            ChatResponse chatResponse = JSON.parseObject(response.getBody(), ChatResponse.class);

            log.debug("Kimi API响应，使用token: {}/{}",
                    chatResponse.getUsage() != null ? chatResponse.getUsage().getTotalTokens() : 0,
                    kimiProperties.getMaxTokens());

            return chatResponse;

        } catch (Exception e) {
            log.error("调用Kimi API失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 对话完成（无工具）
     *
     * @param messages 消息列表
     * @return 对话响应
     */
    public ChatResponse chatCompletion(List<Message> messages) {
        return chatCompletion(messages, null);
    }

    /**
     * 简单的单轮对话
     *
     * @param systemMessage 系统消息
     * @param userMessage   用户消息
     * @return AI回复内容
     */
    public String simpleChat(String systemMessage, String userMessage) {
        List<Message> messages = List.of(
                Message.system(systemMessage),
                Message.user(userMessage)
        );

        ChatResponse response = chatCompletion(messages);

        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            return response.getChoices().get(0).getMessage().getContent();
        }

        throw new RuntimeException("AI服务返回空响应");
    }

    /**
     * 从响应中获取 Token 使用统计
     *
     * @param response API 响应
     * @return Token 使用统计
     */
    public TokenUsage getTokenUsage(ChatResponse response) {
        if (response == null || response.getUsage() == null) {
            return null;
        }
        return TokenUsage.builder()
                .inputTokens(response.getUsage().getPromptTokens())
                .outputTokens(response.getUsage().getCompletionTokens())
                .model(kimiProperties.getModel())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
