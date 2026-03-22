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
import org.springframework.web.reactive.function.client.WebClient;

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
    private final WebClient webClient;
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

        // 序列化请求体
        String requestBody = JSON.toJSONString(request, MESSAGE_NAME_FILTER,
                com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);

        log.debug("MiniMax流式API请求体: {}", requestBody);

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + miniMaxProperties.getApiKey())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> log.debug("MiniMax流式响应开始"))
                .doOnNext(chunk -> log.trace("收到SSE数据块: {}", chunk))
                .doOnError(e -> log.error("MiniMax流式响应异常: {}", e.getMessage(), e))
                .doOnComplete(() -> log.debug("MiniMax流式响应完成"))
                .flatMap(this::parseSseData);
    }

    /**
     * 解析SSE数据块
     * MiniMax 流式响应使用 delta 字段而非 message 字段
     */
    private Flux<String> parseSseData(String line) {
        if (line == null || line.isEmpty()) {
            return Flux.empty();
        }

        // 记录原始数据用于调试
        log.debug("MiniMax原始SSE数据: {}", line);

        // SSE格式: data: {"id":"...","choices":[...]}
        // MiniMax可能不使用 data: 前缀，直接是JSON行
        String data = line;
        if (line.startsWith("data: ")) {
            data = line.substring(6);
        }

        data = data.trim();

        // [DONE] 表示流结束
        if ("[DONE]".equals(data)) {
            return Flux.empty();
        }

        try {
            // MiniMax 使用 choices[0].delta.content 格式
            // 先尝试解析为包含 delta 的格式
            com.alibaba.fastjson2.JSONObject json = JSON.parseObject(data);
            var choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                var choice = choices.getJSONObject(0);

                // 优先尝试 delta.content（MiniMax 流式格式）
                var delta = choice.getJSONObject("delta");
                if (delta != null) {
                    String content = delta.getString("content");
                    // 如果 content 为空，尝试使用 reasoning_content（MiniMax 推理模式）
                    if (content == null || content.isEmpty()) {
                        String reasoning = delta.getString("reasoning_content");
                        if (reasoning != null && !reasoning.isEmpty()) {
                            return Flux.just(reasoning);
                        }
                    } else if (content != null && !content.isEmpty()) {
                        return Flux.just(content);
                    }
                }

                // 其次尝试 message.content（Kimi 非流式格式）
                var message = choice.getJSONObject("message");
                if (message != null) {
                    String content = message.getString("content");
                    List<com.mrshudson.mcp.kimi.dto.ToolCall> toolCalls = message.getList("tool_calls", com.mrshudson.mcp.kimi.dto.ToolCall.class);

                    if (content != null && !content.isEmpty()) {
                        return Flux.just(content);
                    } else if (toolCalls != null && !toolCalls.isEmpty()) {
                        com.mrshudson.mcp.kimi.dto.ToolCall toolCall = toolCalls.get(0);
                        if (toolCall != null && toolCall.getFunction() != null) {
                            String id = toolCall.getId() != null ? toolCall.getId() : "tool_" + System.currentTimeMillis();
                            String name = toolCall.getFunction().getName();
                            String arguments = toolCall.getFunction().getArguments();
                            String toolCallStr = "[TOOL_CALL]" + id + ":" + name + ":" + arguments;
                            log.debug("检测到MiniMax工具调用: {}", toolCallStr);
                            return Flux.just(toolCallStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析SSE数据失败: {}, 原始数据: {}", e.getMessage(), data);
        }

        log.trace("MiniMax未能解析的数据，跳过: {}", data);
        return Flux.empty();
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