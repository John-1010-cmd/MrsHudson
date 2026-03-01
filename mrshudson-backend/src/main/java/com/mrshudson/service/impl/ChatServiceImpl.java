package com.mrshudson.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.dto.*;
import com.mrshudson.domain.entity.ChatMessage;
import com.mrshudson.mapper.ChatMessageMapper;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.ChatResponse;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.ToolCall;
import com.mrshudson.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final KimiClient kimiClient;
    private final ToolRegistry toolRegistry;

    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。

            你可以帮助用户：
            - 查询天气并提供穿衣建议（使用get_weather工具）
            - 管理日程安排：
              * 创建事件（使用create_calendar_event工具）
              * 查看日程（使用get_calendar_events工具）
              * 删除事件（使用delete_calendar_event工具）
            - 管理待办事项：
              * 创建待办（使用create_todo工具）
              * 查看待办列表（使用list_todos工具）
              * 完成待办（使用complete_todo工具）
              * 删除待办（使用delete_todo工具）
            - 规划出行路线

            当用户询问日程、会议、安排、计划等时，主动使用日历工具。
            当用户询问待办、任务、提醒、清单等时，主动使用待办工具。
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
        userMessage.setRole(ChatMessage.Role.USER.name());
        userMessage.setContent(userContent);
        chatMessageMapper.insert(userMessage);

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
        assistantMessage.setRole(ChatMessage.Role.ASSISTANT.name());
        assistantMessage.setContent(aiMessage.getContent());
        if (!toolCallInfos.isEmpty()) {
            assistantMessage.setFunctionCall(JSON.toJSONString(toolCallInfos));
        }
        chatMessageMapper.insert(assistantMessage);

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
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUserId, userId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .last("LIMIT 20")
        );

        List<ChatHistoryResponse.MessageInfo> messageInfos = messages.stream()
                .map(msg -> ChatHistoryResponse.MessageInfo.builder()
                        .id(msg.getId().toString())
                        .role(msg.getRole().toLowerCase())
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
        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUserId, userId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .last("LIMIT 20")
        );
        // 反转顺序（按时间正序）
        List<ChatMessage> orderedHistory = new ArrayList<>(history);
        Collections.reverse(orderedHistory);

        // 添加历史消息（只取最近10条避免超出token限制）
        int startIndex = Math.max(0, orderedHistory.size() - 10);
        for (int i = startIndex; i < orderedHistory.size(); i++) {
            ChatMessage msg = orderedHistory.get(i);
            if (ChatMessage.Role.USER.name().equals(msg.getRole())) {
                messages.add(Message.user(msg.getContent()));
            } else if (ChatMessage.Role.ASSISTANT.name().equals(msg.getRole())) {
                messages.add(Message.assistant(msg.getContent()));
            }
        }

        // 添加当前用户消息
        messages.add(Message.user(userContent));

        return messages;
    }
}
