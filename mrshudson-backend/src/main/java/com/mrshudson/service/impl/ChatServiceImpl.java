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
import com.mrshudson.optim.context.ContextManager;
import com.mrshudson.optim.cost.CachedResult;
import com.mrshudson.optim.cost.CostOptimizer;
import com.mrshudson.optim.cost.ModelRouter;
import com.mrshudson.optim.fallback.FallbackHandler;
import com.mrshudson.optim.fallback.FallbackMetrics;
import com.mrshudson.optim.intent.IntentConfidenceEvaluator;
import com.mrshudson.optim.intent.IntentClarificationService;
import com.mrshudson.optim.intent.IntentRouter;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.IntentResult;
import com.mrshudson.optim.correction.SelfCorrectingAgent;
import com.mrshudson.optim.correction.ValidationResult;
import com.mrshudson.optim.quality.QualityMetrics;
import com.mrshudson.optim.quality.QualityOptimizer;
import com.mrshudson.optim.quality.QualityProperties;
import com.mrshudson.optim.token.TokenTrackerService;
import com.mrshudson.optim.token.TokenUsage;
import com.mrshudson.service.AsyncTaskService;
import com.mrshudson.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话服务实现 - 增强版
 * 集成了：Token统计、上下文管理、兜底机制、成本优化、质量优化、意图理解
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final ConversationMapper conversationMapper;
    private final AIServiceFactory aiServiceFactory;
    private final ToolRegistry toolRegistry;
    private final AsyncTaskService asyncTaskService;

    // ========== 新增：优化模块依赖 ==========
    private final TokenTrackerService tokenTrackerService;
    private final ContextManager contextManager;
    private final FallbackHandler fallbackHandler;
    private final FallbackMetrics fallbackMetrics;
    private final CostOptimizer costOptimizer;
    private final ModelRouter modelRouter;
    private final QualityOptimizer qualityOptimizer;
    private final QualityMetrics qualityMetrics;
    private final IntentRouter intentRouter;
    private final IntentClarificationService intentClarificationService;
    private final IntentConfidenceEvaluator intentConfidenceEvaluator;
    private final SelfCorrectingAgent selfCorrectingAgent;

    /**
     * 构建系统提示词（精简版，控制在500字以内）
     */
    private String buildSystemPrompt() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        return """
            你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。今天是%s。

            可用工具：
            - get_weather: 查询天气（需要提供城市名称，如"北京"）
            - create_calendar_event/get_calendar_events/delete_calendar_event: 管理日程
            - create_todo/list_todos/complete_todo/delete_todo: 管理待办
            - plan_route: 路线规划（需要起点和终点）

            重要规则：
            1. 调用工具前，确保已获取所需参数。如果用户没有提供完整参数，主动询问用户确认。
            2. 询问日程/会议时主动使用日历工具；询问待办/任务时主动使用待办工具。
            3. 如果工具返回错误（如"城市不能为空"），请用友好的方式询问用户提供必要信息。

            语气友好专业，适当使用表情符号。
            """.formatted(today);
    }

    @Override
    @Transactional
    public SendMessageResponse sendMessage(Long userId, SendMessageRequest request) {
        String userContent = request.getMessage();
        Long conversationId = request.getConversationId();
        log.info("用户 {} 发送消息: {}, 会话ID: {}", userId, userContent, conversationId);

        // ========== 新增：记录总请求数 ==========
        fallbackMetrics.recordTotal();

        // ========== 新增：Token追踪开始 ==========
        String trackingId = tokenTrackerService.startTracking();
        long startTime = System.currentTimeMillis();

        try {
            // 1. 保存用户消息
            ChatMessage userMessage = new ChatMessage();
            userMessage.setUserId(userId);
            userMessage.setRole(ChatMessage.Role.USER.name());
            userMessage.setContent(userContent);
            if (conversationId != null) {
                userMessage.setConversationId(conversationId);
            }
            chatMessageMapper.insert(userMessage);

            // ========== 新增：成本优化 - 缓存检查 ==========
            CachedResult cacheResult = costOptimizer.checkCache(userId, userContent);
            if (cacheResult != null && cacheResult.isHit()) {
                log.info("缓存命中，直接返回缓存结果");
                String cachedResponse = cacheResult.getResponse();
                
                // 保存AI消息
                ChatMessage assistantMessage = new ChatMessage();
                assistantMessage.setUserId(userId);
                assistantMessage.setRole(ChatMessage.Role.ASSISTANT.name());
                assistantMessage.setContent(cachedResponse);
                if (conversationId != null) {
                    assistantMessage.setConversationId(conversationId);
                }
                chatMessageMapper.insert(assistantMessage);

                // 更新会话
                if (conversationId != null) {
                    updateConversationLastMessageTime(conversationId);
                }

                // 记录缓存命中
                TokenUsage usage = TokenUsage.builder()
                    .inputTokens(0)
                    .outputTokens(0)
                    .duration(System.currentTimeMillis() - startTime)
                    .model("cache")
                    .build();
                String stats = tokenTrackerService.formatStatistics(usage);
                cachedResponse = cachedResponse + stats;

                return SendMessageResponse.builder()
                    .messageId(assistantMessage.getId().toString())
                    .content(cachedResponse)
                    .functionCalls(null)
                    .createdAt(assistantMessage.getCreatedAt())
                    .build();
            }

            // ========== 新增：意图识别 ==========
            IntentType intentType = intentRouter.recognizeIntent(userContent);
            log.debug("识别到意图: {}", intentType);

            // ========== 新增：意图置信度评估 ==========
            IntentResult intentResult = intentConfidenceEvaluator.evaluate(userContent, intentType);
            log.debug("意图置信度: {}, 是否需要澄清: {}", intentResult.getConfidence(), intentResult.needsClarification());

            // ========== 新增：检查是否需要澄清 ==========
            if (intentResult.needsClarification()) {
                String clarificationQuestion = intentClarificationService.buildClarification(userContent, intentResult);
                if (clarificationQuestion != null) {
                    log.info("意图不明确，返回澄清问题: {}", clarificationQuestion);

                    // 保存AI消息（澄清问题）
                    ChatMessage clarificationMessage = new ChatMessage();
                    clarificationMessage.setUserId(userId);
                    clarificationMessage.setRole(ChatMessage.Role.ASSISTANT.name());
                    clarificationMessage.setContent(clarificationQuestion);
                    if (conversationId != null) {
                        clarificationMessage.setConversationId(conversationId);
                    }
                    chatMessageMapper.insert(clarificationMessage);

                    // 更新会话
                    if (conversationId != null) {
                        updateConversationLastMessageTime(conversationId);
                    }

                    return SendMessageResponse.builder()
                        .messageId(clarificationMessage.getId().toString())
                        .content(clarificationQuestion)
                        .functionCalls(null)
                        .createdAt(clarificationMessage.getCreatedAt())
                        .build();
                }
            }

            // ========== 新增：模型选择 ==========
            String model = modelRouter.getModel(userContent);
            log.debug("选择模型: {}", model);

            // 2. 构建消息列表（系统消息 + 历史消息 + 当前消息）
            List<Message> messages = buildMessageList(userId, conversationId, userContent);

            // ========== 新增：上下文压缩 ==========
            if (contextManager.needsCompression(messages)) {
                messages = contextManager.compress(messages);
                log.debug("上下文已压缩，当前消息数: {}", messages.size());
            }

            // ========== 新增：质量优化 ==========
            // 检测问题特征并记录指标
            if (qualityOptimizer.isComplex(userContent)) {
                qualityMetrics.recordComplexQuestion();
                qualityMetrics.recordQualityUpgrade();
                log.debug("复杂问题检测到，记录质量提升指标");
            } else {
                qualityMetrics.recordSpeedMode();
                log.debug("简单问题检测到，记录速度模式指标");
            }

            // 检测是否需要创意回答
            if (qualityOptimizer.needsCreativity(userContent)) {
                qualityMetrics.recordCreativityRequest();
            }

            AIService aiService = aiServiceFactory.getService();
            AIService.ChatResult result = aiService.chatCompletionWithTools(
                messages,
                toolRegistry.getToolDefinitions()
            );

            // 记录输入Token（估算）
            tokenTrackerService.recordInputTokens(trackingId, tokenTrackerService.estimateTokens(userContent));

            // 3. 处理AI响应
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

                    // ========== 新增：自纠错验证工具结果 ==========
                    ValidationResult validationResult = selfCorrectingAgent.validate(toolName, toolResult, arguments);
                    if (!validationResult.isValid()) {
                        log.warn("工具 {} 执行结果验证失败: {}", toolName, validationResult.getErrorMessage());

                        // 构建纠错上下文
                        FallbackHandler.FallbackContext correctionContext = new FallbackHandler.FallbackContext(
                            userContent,
                            userId,
                            conversationId,
                            intentType,
                            toolResult,
                            toolRegistry.getToolDescriptions(),
                            messages
                        );

                        // 触发纠错流程
                        String correctedResponse = selfCorrectingAgent.correctAndRetry(validationResult, correctionContext)
                            .block();

                        if (correctedResponse != null && !correctedResponse.isEmpty()) {
                            log.info("纠错成功，使用纠错后的响应");
                            toolResult = correctedResponse;
                        }
                    }

                    toolCallInfos.add(SendMessageResponse.ToolCallInfo.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .result(toolResult)
                        .build());

                    // 添加工具响应消息
                    toolResults.add(Message.tool(toolCallId, toolResult));
                }

                // ========== 新增：检查是否需要兜底 ==========
                boolean shouldFallback = fallbackHandler.shouldFallback(userContent, intentType, 
                    toolCallInfos.isEmpty() ? null : toolCallInfos.get(0).getResult());

                if (shouldFallback) {
                    log.info("触发兜底机制");
                    fallbackMetrics.recordFallback();
                    String fallbackResponse = fallbackHandler.executeFallback(
                        userContent,
                        new FallbackHandler.FallbackContext(
                            userContent,
                            userId,
                            conversationId,
                            intentType,
                            toolCallInfos.isEmpty() ? null : toolCallInfos.get(0).getResult(),
                            toolRegistry.getToolDescriptions(),
                            messages
                        )
                    ).block();

                    if (fallbackResponse != null) {
                        aiContent = fallbackResponse;
                    }
                } else {
                    // 再次调用AI获取最终回复
                    messages.addAll(toolResults);
                    AIService.ChatResult finalResult = aiService.chatCompletionWithTools(
                        messages,
                        toolRegistry.getToolDefinitions()
                    );
                    aiContent = finalResult.getContent();

                    // 记录输出Token（估算）
                    tokenTrackerService.recordOutputTokens(trackingId, tokenTrackerService.estimateTokens(aiContent));
                }
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

            // ========== 新增：保存到缓存 ==========
            costOptimizer.saveCache(userId, userContent, aiContent);

            log.info("AI回复: {}", aiContent);

            // ========== 新增：Token统计并追加到响应 ==========
            TokenUsage usage = tokenTrackerService.getUsage(trackingId);
            String stats = tokenTrackerService.formatStatistics(usage);
            aiContent = aiContent + stats;

            // 7. 构建响应
            return SendMessageResponse.builder()
                .messageId(assistantMessage.getId().toString())
                .content(aiContent)
                .functionCalls(toolCallInfos.isEmpty() ? null : toolCallInfos)
                .createdAt(assistantMessage.getCreatedAt())
                .build();

        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage(), e);
            
            // ========== 新增：兜底机制处理异常 ==========
            try {
                String fallbackResponse = fallbackHandler.executeFallback(
                    userContent,
                    new FallbackHandler.FallbackContext(
                        userContent,
                        userId,
                        conversationId,
                        IntentType.UNKNOWN,
                        e.getMessage(),
                        toolRegistry.getToolDescriptions(),
                        new ArrayList<>()
                    )
                ).block();

                if (fallbackResponse != null) {
                    return SendMessageResponse.builder()
                        .messageId(null)
                        .content(fallbackResponse)
                        .functionCalls(null)
                        .createdAt(LocalDateTime.now())
                        .build();
                }
            } catch (Exception fallbackError) {
                log.error("兜底机制也失败了: {}", fallbackError.getMessage());
            }

            throw new RuntimeException("处理消息失败: " + e.getMessage(), e);
        }
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
                .functionCall(msg.getFunctionCall())
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
                .functionCall(msg.getFunctionCall())
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
            .eq(Conversation::getDeleted, 0)  // 明确过滤已删除的会话
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

        // ========== 新增：清除相关缓存 ==========
        costOptimizer.clearUserCache(userId);
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
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    /**
     * 检查并生成会话标题（如果是第一条用户消息）
     */
    private void checkAndGenerateTitle(Long conversationId, String firstMessage) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getRole, ChatMessage.Role.USER.name());

        long userMessageCount = chatMessageMapper.selectCount(wrapper);

        log.info("检查会话 {} 是否需要生成标题，当前用户消息数: {}", conversationId, userMessageCount);

        if (userMessageCount == 1) {
            log.info("会话 {} 是第一条消息，调用 AsyncTaskService 生成标题", conversationId);
            asyncTaskService.generateConversationTitle(conversationId, firstMessage);
        }
    }

    /**
     * 异步生成会话标题
     */
    @Async("taskExecutor")
    public void generateConversationTitle(Long conversationId, String firstMessage) {
        try {
            log.info("开始为会话 {} 生成标题", conversationId);

            String presetTitle = getPresetTitle(firstMessage);
            if (presetTitle != null) {
                conversationMapper.updateTitle(conversationId, presetTitle);
                log.info("会话 {} 使用预设标题: {}", conversationId, presetTitle);
                return;
            }

            String prompt = "请用10-15字概括以下对话的主题，要求简洁明了：\n" + firstMessage;

            AIService aiService = aiServiceFactory.getService();
            AIService.ChatResult result = aiService.chatCompletionWithTools(
                List.of(Message.system("你是一个对话标题生成助手，请用简洁的语言概括对话主题。"),
                    Message.user(prompt)),
                null
            );

            String title = result.getContent();
            if (title != null && !title.isEmpty()) {
                title = title.replaceAll("[\"'\\n\\r]", "").trim();
                if (title.length() > 20) {
                    title = title.substring(0, 20);
                }
                conversationMapper.updateTitle(conversationId, title);
                log.info("会话 {} 标题已更新为: {}", conversationId, title);
            }
        } catch (Exception e) {
            log.error("生成会话标题失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 根据消息内容获取预设标题
     */
    private String getPresetTitle(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "新对话";
        }

        String lowerMsg = message.toLowerCase();

        if (isGreeting(lowerMsg)) {
            return "新对话";
        }

        if (containsAny(lowerMsg, "天气", "温度", "下雨", "下雪", "刮风", "晴", "阴", "多云")) {
            return "关于天气的咨询";
        }

        if (containsAny(lowerMsg, "日程", "会议", "安排", "日历", "今天", "明天", "下周", "这周")) {
            return "关于日程的咨询";
        }

        if (containsAny(lowerMsg, "待办", "任务", "todo", "提醒", "未完成", "要做")) {
            return "关于待办的咨询";
        }

        if (containsAny(lowerMsg, "路线", "怎么去", "怎么走", "导航", "从", "到", "距离")) {
            return "关于路线的咨询";
        }

        return null;
    }

    private boolean isGreeting(String message) {
        String[] greetings = {"你好", "您好", "在吗", "hello", "hi", "hey", "早上好", "下午好", "晚上好", "再见", "拜拜"};
        for (String greeting : greetings) {
            if (message.contains(greeting)) {
                if (message.length() <= greeting.length() + 5) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

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
     */
    private List<Message> buildMessageList(Long userId, Long conversationId, String userContent) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildSystemPrompt()));

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getUserId, userId)
            .orderByDesc(ChatMessage::getCreatedAt)
            .last("LIMIT 20");

        if (conversationId != null) {
            wrapper.eq(ChatMessage::getConversationId, conversationId);
        }

        List<ChatMessage> history = chatMessageMapper.selectList(wrapper);

        List<ChatMessage> orderedHistory = new ArrayList<>(history);
        Collections.reverse(orderedHistory);

        int startIndex = Math.max(0, orderedHistory.size() - 10);
        for (int i = startIndex; i < orderedHistory.size(); i++) {
            ChatMessage msg = orderedHistory.get(i);
            if (ChatMessage.Role.USER.name().equals(msg.getRole())) {
                messages.add(Message.user(msg.getContent()));
            } else if (ChatMessage.Role.ASSISTANT.name().equals(msg.getRole())) {
                messages.add(Message.assistant(msg.getContent()));
            }
        }

        messages.add(Message.user(userContent));

        return messages;
    }
}
