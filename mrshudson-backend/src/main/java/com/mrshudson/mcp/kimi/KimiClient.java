package com.mrshudson.mcp.kimi;

import com.alibaba.fastjson2.JSON;
import com.mrshudson.mcp.kimi.dto.ChatRequest;
import com.mrshudson.mcp.kimi.dto.ChatResponse;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Kimi API客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KimiClient {

    private final KimiProperties kimiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 对话完成
     *
     * @param messages 消息列表
     * @param tools    工具列表（可选）
     * @return 对话响应
     */
    public ChatResponse chatCompletion(List<Message> messages, List<Tool> tools) {
        String url = kimiProperties.getBaseUrl() + "/chat/completions";

        // 构建请求
        ChatRequest request = ChatRequest.builder()
                .model(kimiProperties.getModel())
                .messages(messages)
                .tools(tools)
                .temperature(kimiProperties.getTemperature())
                .maxTokens(kimiProperties.getMaxTokens())
                .stream(false)
                .build();

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(kimiProperties.getApiKey());

        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

        log.debug("调用Kimi API，消息数: {}, 工具数: {}",
                messages.size(), tools != null ? tools.size() : 0);

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
}
