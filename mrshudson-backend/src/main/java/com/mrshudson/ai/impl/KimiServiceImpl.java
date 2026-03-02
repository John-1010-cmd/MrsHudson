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
 * Kimi AI服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KimiServiceImpl implements AIService {

    private final AIProperties aiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 属性过滤器：过滤掉 Message 对象中为 null 的 name 字段
     */
    private static final PropertyFilter MESSAGE_NAME_FILTER = (object, name, value) -> {
        if (object instanceof Message && "name".equals(name) && value == null) {
            return false;
        }
        return true;
    };

    @Override
    public AIProvider getProvider() {
        return AIProvider.KIMI;
    }

    @Override
    public ChatResult chatCompletionWithTools(List<Message> messages, List<Tool> tools) {
        AIProperties.KimiConfig config = aiProperties.getKimi();
        String url = config.getBaseUrl() + "/chat/completions";

        // 构建请求
        ChatRequest request = ChatRequest.builder()
                .model(config.getModel())
                .messages(messages)
                .tools(tools)
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .stream(false)
                .build();

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        // 序列化请求体
        String requestBody = JSON.toJSONString(request, MESSAGE_NAME_FILTER,
                com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);
        log.debug("Kimi API请求体: {}", requestBody);

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

            throw new RuntimeException("AI服务返回空响应");

        } catch (Exception e) {
            log.error("调用Kimi API失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
    }
}
