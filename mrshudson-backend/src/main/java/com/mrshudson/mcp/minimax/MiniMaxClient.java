package com.mrshudson.mcp.minimax;

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

import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MiniMax API客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniMaxClient {

    private final MiniMaxProperties miniMaxProperties;
    private final OptimProperties optimProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 属性过滤器：过滤掉 Message 对象中为 null 的 name 字段
     * MiniMax API 要求 tool 消息不能包含 name 字段
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
        // MiniMax API 路径格式: /text/chatcompletion_v2
        String url = miniMaxProperties.getBaseUrl() + "/text/chatcompletion_v2";

        // 从配置读取参数
        double temperature = optimProperties.getKimiParams() != null
                ? optimProperties.getKimiParams().getTemperature()
                : miniMaxProperties.getTemperature();
        int maxTokens = optimProperties.getKimiParams() != null
                ? optimProperties.getKimiParams().getMaxTokens()
                : miniMaxProperties.getMaxTokens();

        // 构建请求
        ChatRequest request = ChatRequest.builder()
                .model(miniMaxProperties.getModel())
                .messages(messages)
                .tools(tools)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .stream(false)
                .build();

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(miniMaxProperties.getApiKey());

        // 调试：打印API Key前10个字符（隐藏敏感信息）
        String apiKey = miniMaxProperties.getApiKey();
        String maskedKey = apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 10) + "..."
                : "null or empty";
        log.debug("调用MiniMax API，API Key: {}, 消息数: {}, 工具数: {}",
                maskedKey, messages.size(), tools != null ? tools.size() : 0);

        // 序列化请求体（保留null值字段，但过滤掉Message中为null的name字段）
        String requestBody = JSON.toJSONString(request, MESSAGE_NAME_FILTER, com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);
        log.debug("MiniMax API请求体: {}", requestBody);

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

            log.debug("MiniMax API响应，使用token: {}",
                    chatResponse.getUsage() != null ? chatResponse.getUsage().getTotalTokens() : 0);

            return chatResponse;

        } catch (Exception e) {
            log.error("调用MiniMax API失败: {}", e.getMessage(), e);
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
                .model(miniMaxProperties.getModel())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 流式对话完成
     *
     * @param messages 消息列表
     * @param tools    工具列表（可选）
     * @return 流式响应
     */
    public Flux<String> streamChatCompletion(List<Message> messages, List<Tool> tools) {
        // MiniMax API 路径格式: /text/chatcompletion_v2
        String url = miniMaxProperties.getBaseUrl() + "/text/chatcompletion_v2";

        // 从配置读取参数
        double temperature = optimProperties.getKimiParams() != null
                ? optimProperties.getKimiParams().getTemperature()
                : miniMaxProperties.getTemperature();
        int maxTokens = optimProperties.getKimiParams() != null
                ? optimProperties.getKimiParams().getMaxTokens()
                : miniMaxProperties.getMaxTokens();

        // 构建请求（启用流式）
        ChatRequest request = ChatRequest.builder()
                .model(miniMaxProperties.getModel())
                .messages(messages)
                .tools(tools)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .stream(true)
                .build();

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(miniMaxProperties.getApiKey());

        // 序列化请求体
        String requestBody = JSON.toJSONString(request, MESSAGE_NAME_FILTER,
                com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        return Flux.create(sink -> {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        String.class
                );

                String body = response.getBody();
                if (body != null) {
                    // 按行分割 SSE 响应
                    String[] lines = body.split("\n");
                    for (String line : lines) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                continue;
                            }
                            try {
                                ChatResponse chatResponse = JSON.parseObject(data, ChatResponse.class);
                                if (chatResponse.getChoices() != null && !chatResponse.getChoices().isEmpty()) {
                                    String content = chatResponse.getChoices().get(0).getMessage().getContent();
                                    if (content != null) {
                                        sink.next(content);
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("解析SSE数据失败: {}", e.getMessage());
                            }
                        }
                    }
                }
                sink.complete();
            } catch (Exception e) {
                log.error("流式调用MiniMax API失败: {}", e.getMessage(), e);
                sink.error(new RuntimeException("AI服务调用失败: " + e.getMessage()));
            }
        });
    }

    /**
     * 流式对话完成（无工具）
     *
     * @param messages 消息列表
     * @return 流式响应
     */
    public Flux<String> streamChatCompletion(List<Message> messages) {
        return streamChatCompletion(messages, null);
    }
}