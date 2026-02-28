package com.mrshudson.service.impl;

import com.alibaba.fastjson2.JSON;
import com.mrshudson.domain.dto.*;
import com.mrshudson.domain.entity.ChatMessage;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.ChatResponse;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.ToolCall;
import com.mrshudson.repository.ChatMessageRepository;
import com.mrshudson.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final KimiClient kimiClient;
    private final ToolRegistry toolRegistry;

    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。

            你可以帮助用户：
            - 查询天气并提供穿衣建议
            - 管理日程安排（创建、查看、删除事件）
            - 记录待办事项（创建、完成、查看）
            - 规划出行路线

            请用友好、专业的语气回复，适当使用表情符号。
            如果用户的问题超出你的能力范围，礼貌地说明。
            """;

    @Override
    @Transactional
    public SendMessageResponse sendMessage(Long userId, SendMessageRequest request) {
        String userContent = request.getMessage();
        log.info("用户 {} 发送消息: {}", userId, userContent);

        // 1. 保存用户消息
        ChatMessage userMessage = new ChatMessage();
        userMessage.setUserId(userId);
        userMessage.setRole(ChatMessage.Role.USER);
        userMessage.setContent(userContent);
        chatMessageRepository.save(userMessage);

        // 2. 构建消息列表（系统消息 + 历史消息 + 当前消息）
        List<Message> messages = buildMessageList(userId, userContent);

        // 3. 调用Kimi API
        ChatResponse response = kimiClient.chatCompletion(
                messages,
                toolRegistry.getToolDefinitions()
        );

        // 4. 处理AI响应
        Message aiMessage = response.getChoices().get(0).getMessage();
        List<SendMessageResponse.ToolCallInfo> toolCallInfos = new ArrayList<>();

        // 处理工具调用
        if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
            log.info("AI调用工具，数量: {}", aiMessage.getToolCalls().size());

            List<Message> toolResults = new ArrayList<>();
            toolResults.add(aiMessage);  // 添加AI的工具调用请求

            for (ToolCall toolCall : aiMessage.getToolCalls()) {
                String toolName = toolCall.getFunction().getName();
                String arguments = toolCall.getFunction().getArguments();

                // 执行工具
                String result = toolRegistry.executeTool(toolName, arguments);

                toolCallInfos.add(SendMessageResponse.ToolCallInfo.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .result(result)
                        .build());

                // 添加工具响应消息
                toolResults.add(Message.tool(toolCall.getId(), result));
            }

            // 再次调用Kimi获取最终回复
            messages.addAll(toolResults);
            ChatResponse finalResponse = kimiClient.chatCompletion(
                    messages,
                    toolRegistry.getToolDefinitions()
            );
            aiMessage = finalResponse.getChoices().get(0).getMessage();
        }

        // 5. 保存AI消息
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setUserId(userId);
        assistantMessage.setRole(ChatMessage.Role.ASSISTANT);
        assistantMessage.setContent(aiMessage.getContent());
        if (!toolCallInfos.isEmpty()) {
            assistantMessage.setFunctionCall(JSON.toJSONString(toolCallInfos));
        }
        chatMessageRepository.save(assistantMessage);

        log.info("AI回复: {}", aiMessage.getContent());

        // 6. 构建响应
        return SendMessageResponse.builder()
                .messageId(assistantMessage.getId().toString())
                .content(aiMessage.getContent())
                .functionCalls(toolCallInfos.isEmpty() ? null : toolCallInfos)
                .createdAt(assistantMessage.getCreatedAt())
                .build();
    }

    @Override
    public ChatHistoryResponse getChatHistory(Long userId, int limit) {
        List<ChatMessage> messages = chatMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);

        List<ChatHistoryResponse.MessageInfo> messageInfos = messages.stream()
                .map(msg -> ChatHistoryResponse.MessageInfo.builder()
                        .id(msg.getId().toString())
                        .role(msg.getRole().name().toLowerCase())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ChatHistoryResponse.builder()
                .messages(messageInfos)
                .build();
    }

    /**
     * 构建消息列表
     *
     * @param userId      用户ID
     * @param userContent 用户消息内容
     * @return 消息列表
     */
    private List<Message> buildMessageList(Long userId, String userContent) {
        List<Message> messages = new ArrayList<>();

        // 添加系统消息
        messages.add(Message.system(SYSTEM_PROMPT));

        // 获取历史消息（最近10条）
        List<ChatMessage> history = chatMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        // 反转顺序（按时间正序）
        List<ChatMessage> orderedHistory = new ArrayList<>(history);
        java.util.Collections.reverse(orderedHistory);

        // 添加历史消息（只取最近10条避免超出token限制）
        int startIndex = Math.max(0, orderedHistory.size() - 10);
        for (int i = startIndex; i < orderedHistory.size(); i++) {
            ChatMessage msg = orderedHistory.get(i);
            if (msg.getRole() == ChatMessage.Role.USER) {
                messages.add(Message.user(msg.getContent()));
            } else if (msg.getRole() == ChatMessage.Role.ASSISTANT) {
                messages.add(Message.assistant(msg.getContent()));
            }
        }

        // 添加当前用户消息
        messages.add(Message.user(userContent));

        return messages;
    }
}
