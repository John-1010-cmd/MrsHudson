package com.mrshudson.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.ai.AIProvider;
import com.mrshudson.ai.AIService;
import com.mrshudson.ai.AIServiceFactory;
import com.mrshudson.domain.dto.*;
import com.mrshudson.domain.entity.ChatMessage;
import com.mrshudson.domain.entity.Conversation;
import com.mrshudson.mapper.ChatMessageMapper;
import com.mrshudson.mapper.ConversationMapper;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.ToolCall;
import com.mrshudson.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 * 支持多AI模型切换（Kimi, Kimi-for-coding）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final ConversationMapper conversationMapper;
    private final AIServiceFactory aiServiceFactory;
    private final ToolRegistry toolRegistry;

    /**
     * 构建系统提示词（包含当前日期）
     */
    private String buildSystemPrompt() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        return """
            你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。
            今天是%s。

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
            - 规划出行路线：
              * 查询路线（使用plan_route工具）：支持步行、驾车、公交三种方式
              * 需要提供起点和终点地址
              * 例如：从国贸到天安门怎么走、从我家到机场驾车路线

            当用户询问日程、会议、安排、计划等时，主动使用日历工具。
            当用户询问待办、任务、提醒、清单等时，主动使用待办工具。
            请用友好、专业的语气回复，适当使用表情符号。
            如果用户的问题超出你的能力范围，礼貌地说明。
            """.formatted(today);
    }

    @Override
    @Transactional
    public SendMessageResponse sendMessage(Long userId, SendMessageRequest request) {
        String userContent = request.getMessage();
        Long conversationId = request.getConversationId();
        log.info("用户 {} 发送消息: {}, 会话ID: {}", userId, userContent, conversationId);

        // 1. 保存用户消息
        ChatMessage userMessage = new ChatMessage();
        userMessage.setUserId(userId);
        userMessage.setRole(ChatMessage.Role.USER.name());
        userMessage.setContent(userContent);
        if (conversationId != null) {
            userMessage.setConversationId(conversationId);
        }
        chatMessageMapper.insert(userMessage);

        // 2. 构建消息列表（系统消息 + 历史消息 + 当前消息）
        List<Message> messages = buildMessageList(userId, conversationId, userContent);

        // 3. 获取当前AI服务并调用
        AIService aiService = aiServiceFactory.getService();
        AIService.ChatResult result = aiService.chatCompletionWithTools(
                messages,
                toolRegistry.getToolDefinitions()
        );

        // 4. 处理AI响应
        String aiContent = result.getContent();
        List<ToolCall> toolCalls = result.getToolCalls();
        List<SendMessageResponse.ToolCallInfo> toolCallInfos = new ArrayList<>();

        // 处理工具调用
        if (toolCalls != null && !toolCalls.isEmpty()) {
            log.info("AI调用工具，数量: {}", toolCalls.size());
            log.debug("工具调用详情: {}", JSON.toJSONString(toolCalls));

            List<Message> toolResults = new ArrayList<>();
            // 添加AI的工具调用请求，content为空字符串时设为null
            Message toolCallMessage = Message.builder()
                    .role("assistant")
                    .content((aiContent == null || aiContent.isEmpty()) ? null : aiContent)
                    .toolCalls(toolCalls)
                    .build();
            toolResults.add(toolCallMessage);

            for (ToolCall toolCall : toolCalls) {
                String toolName = toolCall.getFunction().getName();
                String arguments = toolCall.getFunction().getArguments();
                String toolCallId = toolCall.getId();

                log.debug("处理工具调用: id={}, name={}, arguments={}", toolCallId, toolName, arguments);

                if (toolCallId == null || toolCallId.isEmpty()) {
                    log.error("工具调用ID为空，跳过此工具调用");
                    continue;
                }

                // 执行工具
                String toolResult = toolRegistry.executeTool(toolName, arguments);

                toolCallInfos.add(SendMessageResponse.ToolCallInfo.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .result(toolResult)
                        .build());

                // 添加工具响应消息
                toolResults.add(Message.tool(toolCallId, toolResult));
            }

            // 再次调用AI获取最终回复
            messages.addAll(toolResults);
            AIService.ChatResult finalResult = aiService.chatCompletionWithTools(
                    messages,
                    toolRegistry.getToolDefinitions()
            );
            aiContent = finalResult.getContent();
        }

        // 5. 保存AI消息
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setUserId(userId);
        assistantMessage.setRole(ChatMessage.Role.ASSISTANT.name());
        assistantMessage.setContent(aiContent);
        if (conversationId != null) {
            assistantMessage.setConversationId(conversationId);
        }
        if (!toolCallInfos.isEmpty()) {
            assistantMessage.setFunctionCall(JSON.toJSONString(toolCallInfos));
        }
        chatMessageMapper.insert(assistantMessage);

        // 6. 更新会话最后消息时间（如果有会话ID）
        if (conversationId != null) {
            updateConversationLastMessageTime(conversationId);

            // 7. 检查是否是第一条消息，如果是则异步生成标题
            checkAndGenerateTitle(conversationId, userContent);
        }

        log.info("AI回复: {}", aiContent);

        // 7. 构建响应
        return SendMessageResponse.builder()
                .messageId(assistantMessage.getId().toString())
                .content(aiContent)
                .functionCalls(toolCallInfos.isEmpty() ? null : toolCallInfos)
                .createdAt(assistantMessage.getCreatedAt())
                .build();
    }

    @Override
    public ChatHistoryResponse getChatHistory(Long userId, int limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .orderByAsc(ChatMessage::getCreatedAt)
                .last("LIMIT " + limit);

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);

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

    @Override
    public ChatHistoryResponse getChatHistoryByConversation(Long userId, Long conversationId, int limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt)
                .last("LIMIT " + limit);

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);

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

    @Override
    @Transactional
    public ConversationDTO createConversation(Long userId, CreateConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(request.getTitle() != null ? request.getTitle() : "新对话");
        conversation.setProvider(aiServiceFactory.getCurrentProvider().getCode());

        conversationMapper.insert(conversation);

        return convertToDTO(conversation);
    }

    @Override
    public ConversationListResponse getConversationList(Long userId, int limit) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getLastMessageAt)
                .last("LIMIT " + limit);

        List<Conversation> conversations = conversationMapper.selectList(wrapper);

        List<ConversationDTO> dtos = conversations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ConversationListResponse.builder()
                .conversations(dtos)
                .build();
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        // 验证会话属于当前用户
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new RuntimeException("会话不存在或无权限");
        }

        // 逻辑删除会话
        conversationMapper.deleteById(conversationId);

        // 删除该会话下的所有消息
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId);
        chatMessageMapper.delete(wrapper);
    }

    @Override
    public AIProviderResponse getAIProviders() {
        AIProvider current = aiServiceFactory.getCurrentProvider();

        List<AIProviderResponse.ProviderInfo> providers = aiServiceFactory.getAvailableProviders().stream()
                .map(p -> AIProviderResponse.ProviderInfo.builder()
                        .code(p.getCode())
                        .name(p.getName())
                        .build())
                .collect(Collectors.toList());

        return AIProviderResponse.builder()
                .currentProvider(current.getCode())
                .providers(providers)
                .build();
    }

    /**
     * 更新会话最后消息时间
     */
    private void updateConversationLastMessageTime(Long conversationId) {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversationMapper.updateById(conversation);
    }

    /**
     * 检查并生成会话标题（如果是第一条用户消息）
     */
    private void checkAndGenerateTitle(Long conversationId, String firstMessage) {
        // 查询该会话的消息数量
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getRole, ChatMessage.Role.USER.name());

        long userMessageCount = chatMessageMapper.selectCount(wrapper);

        // 如果是第一条用户消息，异步生成标题
        if (userMessageCount == 1) {
            generateConversationTitle(conversationId, firstMessage);
        }
    }

    /**
     * 异步生成会话标题
     */
    @Async("taskExecutor")
    public void generateConversationTitle(Long conversationId, String firstMessage) {
        try {
            log.info("开始为会话 {} 生成标题", conversationId);

            // 构建标题生成提示词
            String prompt = "请用10-15字概括以下对话的主题，要求简洁明了：\n" + firstMessage;

            // 调用AI服务生成标题
            AIService aiService = aiServiceFactory.getService();
            AIService.ChatResult result = aiService.chatCompletionWithTools(
                    List.of(Message.system("你是一个对话标题生成助手，请用简洁的语言概括对话主题。"),
                            Message.user(prompt)),
                    null
            );

            String title = result.getContent();
            if (title != null && !title.isEmpty()) {
                // 清理标题（去除引号、换行等）
                title = title.replaceAll("[\"'\\n\\r]", "").trim();

                // 限制长度
                if (title.length() > 20) {
                    title = title.substring(0, 20);
                }

                // 更新会话标题
                conversationMapper.updateTitle(conversationId, title);
                log.info("会话 {} 标题已更新为: {}", conversationId, title);
            }
        } catch (Exception e) {
            log.error("生成会话标题失败: {}", conversationId, e);
        }
    }

    /**
     * 转换会话实体为DTO
     */
    private ConversationDTO convertToDTO(Conversation conversation) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId().toString());
        dto.setTitle(conversation.getTitle());
        dto.setProvider(conversation.getProvider());
        dto.setLastMessageAt(conversation.getLastMessageAt());
        dto.setCreatedAt(conversation.getCreatedAt());
        return dto;
    }

    /**
     * 构建消息列表
     *
     * @param userId         用户ID
     * @param conversationId 会话ID（可选）
     * @param userContent    用户消息内容
     * @return 消息列表
     */
    private List<Message> buildMessageList(Long userId, Long conversationId, String userContent) {
        List<Message> messages = new ArrayList<>();

        // 添加系统消息
        messages.add(Message.system(buildSystemPrompt()));

        // 获取历史消息（最近10条）
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT 20");

        // 如果指定了会话ID，只获取该会话的消息
        if (conversationId != null) {
            wrapper.eq(ChatMessage::getConversationId, conversationId);
        }

        List<ChatMessage> history = chatMessageMapper.selectList(wrapper);

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
