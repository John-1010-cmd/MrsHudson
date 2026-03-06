package com.mrshudson.ai.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.PropertyFilter;
import com.mrshudson.ai.AIProvider;
import com.mrshudson.ai.AIService;
import com.mrshudson.config.AIProperties;
import com.mrshudson.mcp.kimi.dto.ChatRequest;
import com.mrshudson.mcp.kimi.dto.ChatResponse;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * MiniMax AI服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MiniMaxServiceImpl implements AIService {

    private final AIProperties aiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final PropertyFilter MESSAGE_NAME_FILTER = (object, name, value) -> {
        if (object instanceof Message && "name".equals(name) && value == null) {
            return false;
        }
        return true;
    };

    @Override
    public AIProvider getProvider() {
        return AIProvider.MINI_MAX;
    }

    @Override
    public ChatResult chatCompletionWithTools(List<Message> messages, List<Tool> tools) {
        AIProperties.MiniMaxConfig config = aiProperties.getMiniMax();

        // MiniMax API 路径格式: /text/chatcompletion_v2
        String url = config.getBaseUrl() + "/text/chatcompletion_v2";

        ChatRequest request = ChatRequest.builder()
                .model(config.getModel())
                .messages(messages)
                .tools(tools)
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .stream(false)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        String requestBody = JSON.toJSONString(request, MESSAGE_NAME_FILTER,
                com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);
        log.debug("MiniMax API请求体: {}", requestBody);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            ChatResponse chatResponse = JSON.parseObject(response.getBody(), ChatResponse.class);

            if (chatResponse.getChoices() != null && !chatResponse.getChoices().isEmpty()) {
                Message aiMessage = chatResponse.getChoices().get(0).getMessage();
                return ChatResult.builder()
                        .content(aiMessage.getContent())
                        .toolCalls(aiMessage.getToolCalls())
                        .build();
            }

            throw new RuntimeException("MiniMax AI服务返回空响应");

        } catch (Exception e) {
            log.error("调用MiniMax API失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
    }
}
