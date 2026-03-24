package com.mrshudson.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.dto.SendMessageRequest;
import com.mrshudson.domain.dto.SendMessageResponse;
import com.mrshudson.domain.entity.ChatMessage;
import com.mrshudson.domain.entity.Conversation;
import com.mrshudson.mapper.ChatMessageMapper;
import com.mrshudson.mapper.ConversationMapper;
import com.mrshudson.ai.AIClientFactory;
import com.mrshudson.ai.AIClientFactory;
import com.mrshudson.ai.AIProvider;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.minimax.MiniMaxClient;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.optim.context.ContextManager;
import com.mrshudson.optim.correction.SelfCorrectingAgent;
import com.mrshudson.optim.correction.ValidationResult;
import com.mrshudson.optim.cost.CostOptimizer;
import com.mrshudson.optim.fallback.FallbackHandler;
import com.mrshudson.optim.intent.IntentConfidenceEvaluator;
import com.mrshudson.optim.intent.IntentResult;
import com.mrshudson.optim.intent.IntentRouter;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.optim.quality.QualityOptimizer;
import com.mrshudson.optim.token.TokenTrackerService;
import com.mrshudson.optim.token.TokenUsage;
import com.mrshudson.service.AsyncTaskService;
import com.mrshudson.util.SseFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式对话服务
 * 提供真正的SSE流式响应，支持Token统计追加
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatService {

    private final AIClientFactory aiClientFactory;
    private final KimiClient kimiClient;
    private final MiniMaxClient miniMaxClient;
    private final ToolRegistry toolRegistry;
    private final TokenTrackerService tokenTrackerService;
    private final ContextManager contextManager;
    private final CostOptimizer costOptimizer;
    private final QualityOptimizer qualityOptimizer;
    private final IntentRouter intentRouter;
    private final IntentConfidenceEvaluator intentConfidenceEvaluator;
    private final ChatMessageMapper chatMessageMapper;
    private final ConversationMapper conversationMapper;
    private final VoiceService voiceService;
    private final AsyncTaskService asyncTaskService;
    private final SelfCorrectingAgent selfCorrectingAgent;
    private final FallbackHandler fallbackHandler;

    /**
     * 构建系统提示词（精简版）
     */
    private String buildSystemPrompt() {
        return """
            你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。

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
            """;
    }

    /**
     * 流式发送消�?
     * 返回JSON格式的SSE数据�?
     *
     * @param userId  用户ID
     * @param request 消息请求
     * @return JSON格式的SSE数据�?
     */
    public Flux<String> streamSendMessage(Long userId, SendMessageRequest request) {
        String userMessage = request.getMessage();
        Long conversationId = request.getConversationId();

        log.info("流式消息处理开始，用户ID: {}, 消息: {}", userId, userMessage);

        // 1. 保存用户消息
        saveUserMessage(userId, conversationId, userMessage);

        // 2. 检查缓存
        var cacheResult = costOptimizer.checkCache(userId, userMessage);
        if (cacheResult != null && cacheResult.isHit()) {
            log.info("缓存命中，直接返回缓存结果");
            // 生成会话标题（如果是第一条消息）
            checkAndGenerateTitle(conversationId, userMessage);
            return Flux.just(SseFormatter.cacheHit(escapeJson(cacheResult.getResponse())));
        }

        // 3. 意图路由 - 直接使用 route() 获取结果，避免与 IntentConfidenceEvaluator 重复评估
        RouteResult routeResult = intentRouter.route(userId, userMessage);
        IntentType intentType = routeResult.getIntentType();

        // 4. 检查意图路由是否已直接处理（如天气查询、闲聊等）
        if (routeResult.isHandled()) {
            log.info("意图路由直接处理: type={}, layer={}", routeResult.getIntentType(), routeResult.getRouterLayer());
            String response = routeResult.getResponse();
            if (response != null && !response.isEmpty()) {
                // 追加语音合成
                String audioUrl = voiceService.textToSpeech(response);
                saveAssistantMessage(userId, conversationId, response, null, audioUrl);
                // 生成会话标题（如果是第一条消息）
                checkAndGenerateTitle(conversationId, userMessage);
                if (audioUrl != null && !audioUrl.isEmpty()) {
                    log.info("意图路由语音合成成功，audioUrl: {}", audioUrl);
                    return Flux.concat(
                        Flux.just(SseFormatter.content(escapeJson(response))),
                        Flux.just(SseFormatter.audioUrl(escapeJson(audioUrl)))
                    );
                }
                // 直接返回处理结果
                return Flux.just(SseFormatter.content(escapeJson(response)));
            }
            // 如果路由说已处理但没有响应内容，继续走AI流程（可能是需要AI执行工具）
        }

        // 5. 仅在路由未处理时检查是否需要澄清（使用 IntentConfidenceEvaluator）
        if (!routeResult.isHandled()) {
            IntentResult intentResult = intentConfidenceEvaluator.evaluate(userMessage, intentType);
            if (intentResult.needsClarification()) {
                String clarification = buildClarificationPrompt(userMessage, intentResult);
                if (clarification != null) {
                    // 追加语音合成
                    String audioUrl = voiceService.textToSpeech(clarification);
                    saveAssistantMessage(userId, conversationId, clarification, null, audioUrl);
                    // 生成会话标题（如果是第一条消息）
                    checkAndGenerateTitle(conversationId, userMessage);
                    // 澄清提示
                    return Flux.just(SseFormatter.clarification(escapeJson(clarification)));
                }
            }
        }

        // 6. 构建消息列表
        List<Message> messages = buildMessageList(userId, conversationId, userMessage);

        // 7. 上下文压缩检�?
        if (contextManager.needsCompression(messages)) {
            messages = contextManager.compress(messages);
            log.debug("上下文已压缩，当前消息数: {}", messages.size());
        }

        // 8. 记录输入Token
        String trackingId = tokenTrackerService.startTracking();
        int inputTokens = tokenTrackerService.estimateTokens(userMessage);
        tokenTrackerService.recordInputTokens(trackingId, inputTokens);

        // 8. 质量优化
        if (qualityOptimizer.isComplex(userMessage)) {
            log.debug("复杂问题检测到");
        }

        // 9. 执行流式AI调用（支持工具调用）
        // 返回：事件流、AI最终内容、Token统计
        AtomicReference<String> aiFinalContent = new AtomicReference<>("");
        AtomicReference<TokenUsage> tokenUsageRef = new AtomicReference<>();
        AtomicReference<String> audioUrlRef = new AtomicReference<>();
        List<SendMessageResponse.ToolCallInfo> toolCallInfos = Collections.synchronizedList(new ArrayList<>());

        Flux<String> aiStream = executeStreamingAiCallWithTools(
            messages,
            toolRegistry.getToolDefinitions(),
            userId,
            conversationId,
            toolCallInfos,
            aiFinalContent,
            tokenUsageRef,
            userMessage,
            intentType
        );

        // 10. 收集输出并追加Token统计
        AtomicInteger outputTokens = new AtomicInteger(0);

        return aiStream
                .doOnNext(chunk -> {
                    // 估算输出Token（只对普通内容估算，工具调用事件不计入）
                    outputTokens.addAndGet(tokenTrackerService.estimateTokens(chunk));
                })
                // AI 内容（统一返回纯JSON字符串，由Controller添加data:前缀）
                .flatMap(event -> {
                    // 检查事件是否已经是完整JSON格式（以 {"type": 开头）
                    if (event.startsWith("{\"type\":")) {
                        // 已是完整JSON格式（tool_call, tool_result, error, cache_hit, clarification等），直接返回
                        return Flux.just(event);
                    } else {
                        // 纯文本内容，包装为 JSON 格式
                        return Flux.just(SseFormatter.content(escapeJson(event)));
                    }
                })
                .doOnComplete(() -> {
                    // 记录输出Token
                    tokenTrackerService.recordOutputTokens(trackingId, outputTokens.get());
                    TokenUsage usage = tokenTrackerService.getUsage(trackingId);
                    tokenUsageRef.set(usage);

                    // 保存AI响应（只有最终内容，不包含工具调用事件）
                    String finalContent = aiFinalContent.get();
                    if (finalContent != null && !finalContent.isEmpty()) {
                        String functionCallJson = toolCallInfos.isEmpty() ? null : JSON.toJSONString(toolCallInfos);
                        // 同步获取音频URL并保存
                        String audioUrl = voiceService.textToSpeech(finalContent);
                        saveAssistantMessage(userId, conversationId, finalContent, functionCallJson, audioUrl);
                        if (audioUrl != null && !audioUrl.isEmpty()) {
                            log.info("语音合成成功，audioUrl: {}", audioUrl);
                            audioUrlRef.set(audioUrl); // 保存audioUrl供后续SSE事件使用
                        }
                        // 保存到缓存
                        costOptimizer.saveCache(userId, userMessage, finalContent);
                    }

                    // 更新会话
                    if (conversationId != null) {
                        updateConversationLastMessageTime(conversationId);
                        // 检查是否是第一条消息，如果是则异步生成标题
                        checkAndGenerateTitle(conversationId, userMessage);
                    }
                })
                // 末尾追加 Token 统计
                .concatWith(Flux.defer(() -> {
                    TokenUsage usage = tokenUsageRef.get();
                    if (usage == null) {
                        usage = tokenTrackerService.getUsage(trackingId);
                    }
                    return Flux.just(SseFormatter.tokenUsage(
                        usage.getInputTokens() != null ? usage.getInputTokens() : 0,
                        usage.getOutputTokens() != null ? usage.getOutputTokens() : 0,
                        usage.getDuration() != null ? usage.getDuration() : 0L,
                        usage.getModel() != null ? usage.getModel() : "unknown"
                    ));
                }))
                // 末尾追加音频 URL（语音合成）
                .concatWith(Flux.defer(() -> {
                    String audioUrl = audioUrlRef.get();
                    if (audioUrl != null && !audioUrl.isEmpty()) {
                        return Flux.just(SseFormatter.audioUrl(escapeJson(audioUrl)));
                    }
                    return Flux.empty();
                }))
                // 末尾追加完成标记
                .concatWith(Flux.just(SseFormatter.done()));
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * 执行流式AI调用（支持工具调用）
     *
     * @param messages       消息列表（会被修改，添加工具调用和结果）
     * @param tools          工具定义列表
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @param aiFinalContent OUT参数：AI的最终文本内容（不含工具调用/结果事件�?
     * @param tokenUsageRef  OUT参数：Token使用统计
     * @param userMessage    用户原始消息（用于纠错上下文）
     * @param intentType     识别的意图类型（用于纠错上下文）
     * @return 事件流（包含 tool_call、tool_result、content 事件�?
     */
    private Flux<String> executeStreamingAiCallWithTools(
            List<Message> messages,
            List<Tool> tools,
            Long userId,
            Long conversationId,
            List<SendMessageResponse.ToolCallInfo> toolCallInfos,
            AtomicReference<String> aiFinalContent,
            AtomicReference<TokenUsage> tokenUsageRef,
            String userMessage,
            IntentType intentType) {

        // 用于收集所有事件的 Sinks
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 活跃订阅计数器，用于追踪异步流程
        AtomicInteger activeSubscriptions = new AtomicInteger(1);

        // 订阅 AI 流式响应
        AIProvider provider = aiClientFactory.getCurrentProvider();
        Flux<String> aiStream = (provider == AIProvider.KIMI
                ? kimiClient.streamChatCompletion(messages, tools)
                : miniMaxClient.streamChatCompletion(messages, tools))
                .doOnSubscribe(s -> log.debug("AI 流式响应开始"))
                .doOnError(e -> {
                    log.error("AI 流式响应异常: {}", e.getMessage(), e);
                    sink.tryEmitError(e);
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                })
                .doOnCancel(() -> {
                    log.debug("AI 流式响应取消");
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                });

        aiStream.subscribe(
                chunk -> {
                    // 检查是否是工具调用事件
                    if (chunk.startsWith("[TOOL_CALL]")) {
                        // 解析工具调用信息，格式：[TOOL_CALL]tool_call_id:tool_name:arguments
                        String toolCallInfo = chunk.substring("[TOOL_CALL]".length());
                        String[] parts = toolCallInfo.split(":", 3);

                        if (parts.length >= 3) {
                            String toolCallId = parts[0];
                            String toolName = parts[1];
                            String arguments = parts[2];

                            log.info("检测到工具调用: id={}, tool={}, args={}", toolCallId, toolName, arguments);

                            // 1. 发送 tool_call 事件
                            sink.tryEmitNext(SseFormatter.toolCall(toolName, arguments));

                            // 2. 执行工具调用
                            String toolResult = toolRegistry.executeTool(toolName, arguments);
                            toolCallInfos.add(SendMessageResponse.ToolCallInfo.builder()
                                .name(toolName)
                                .arguments(arguments)
                                .result(toolResult)
                                .build());
                            log.info("工具执行完成: tool={}, result={}", toolName, toolResult);

                            // 3. 验证工具结果（集成自纠错机制）
                            ValidationResult validationResult = selfCorrectingAgent.validate(toolName, toolResult, arguments);
                            if (!validationResult.isValid()) {
                                log.warn("工具 {} 执行结果验证失败: {}，尝试纠错", toolName, validationResult.getErrorMessage());

                                // 构建纠错上下文
                                FallbackHandler.FallbackContext correctionContext = new FallbackHandler.FallbackContext(
                                    userMessage,
                                    userId,
                                    conversationId,
                                    intentType,
                                    toolResult,
                                    toolRegistry.getToolDescriptions(),
                                    messages
                                );

                                // 触发纠错流程
                                String correctedResult = selfCorrectingAgent.correctAndRetry(validationResult, correctionContext).block();
                                if (correctedResult != null && !correctedResult.isEmpty()) {
                                    log.info("纠错成功，使用纠错后的结果");
                                    toolResult = correctedResult;
                                } else {
                                    // 纠错失败，发送错误事件给前端
                                    sink.tryEmitNext(SseFormatter.error("工具执行结果异常：" + validationResult.getErrorMessage()));
                                }
                            }

                            // 4. 将工具调用和结果添加到消息历�?
                            // 添加工具调用消息（assistant 角色�?
                            Message assistantMsg = Message.builder()
                                .role("assistant")
                                .content(null)
                                .toolCalls(List.of(
                                    com.mrshudson.mcp.kimi.dto.ToolCall.builder()
                                        .id(toolCallId)
                                        .type("function")
                                        .function(com.mrshudson.mcp.kimi.dto.ToolCall.Function.builder()
                                            .name(toolName)
                                            .arguments(arguments)
                                            .build())
                                        .build()
                                ))
                                .build();
                            messages.add(assistantMsg);

                            // 添加工具结果消息（tool 角色�?
                            messages.add(Message.tool(toolCallId, toolResult));

                            // 4. 发送 tool_result 事件
                            sink.tryEmitNext(SseFormatter.toolResult(toolName, toolResult));

                            // 5. 继续调用 AI 获取下一轮响�?
                            // 增加活跃订阅计数
                            activeSubscriptions.incrementAndGet();
                            continueAiStream(messages, tools, toolCallInfos, sink, aiFinalContent, activeSubscriptions, userMessage, intentType, userId, conversationId);
                        }
                    } else {
                        // 普通内容：累加到最终内容，发送给前端
                        aiFinalContent.set(aiFinalContent.get() + chunk);
                        // 发送 content 事件（后续由 flatMap 统一处理）
                        sink.tryEmitNext(chunk);
                    }
                },
                error -> {
                    // 处理错误
                    log.error("AI 流订阅错误: {}", error.getMessage(), error);
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                },
                () -> {
                    // 处理完成 - 正常结束时减少活跃订阅计数
                    log.debug("AI 流正常完成");
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                }
        );

        // 返回事件�?
        return sink.asFlux()
            .doOnComplete(() -> log.debug("事件流完成"))
            .doOnError(e -> log.error("事件流异常: {}", e.getMessage(), e));
    }

    /**
     * 继续 AI 流式响应（处理工具调用后的下一轮）
     */
    private void continueAiStream(
            List<Message> messages,
            List<Tool> tools,
            List<SendMessageResponse.ToolCallInfo> toolCallInfos,
            Sinks.Many<String> sink,
            AtomicReference<String> aiFinalContent,
            AtomicInteger activeSubscriptions,
            String userMessage,
            IntentType intentType,
            Long userId,
            Long conversationId) {

        log.debug("继续调用 AI 获取下一轮响应，当前消息数: {}", messages.size());

        // 继续订阅 AI 响应
        AIProvider provider = aiClientFactory.getCurrentProvider();
        Flux<String> continueStream = (provider == AIProvider.KIMI
                ? kimiClient.streamChatCompletion(messages, tools)
                : miniMaxClient.streamChatCompletion(messages, tools))
                .doOnNext(chunk -> {
                    aiFinalContent.set(aiFinalContent.get() + chunk);
                })
                .doOnError(e -> {
                    log.error("继续 AI 调用异常: {}", e.getMessage(), e);
                    sink.tryEmitError(e);
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                })
                .doOnCancel(() -> {
                    log.debug("继续 AI 流式响应取消");
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                });

        continueStream.subscribe(chunk -> {
                    // 检查是否是工具调用事件
                    if (chunk.startsWith("[TOOL_CALL]")) {
                        // 解析工具调用信息
                        String toolCallInfo = chunk.substring("[TOOL_CALL]".length());
                        String[] parts = toolCallInfo.split(":", 3);

                        if (parts.length >= 3) {
                            String toolCallId = parts[0];
                            String toolName = parts[1];
                            String arguments = parts[2];

                            log.info("检测到工具调用（继续）: id={}, tool={}, args={}", toolCallId, toolName, arguments);

                            // 发送 tool_call 事件
                            sink.tryEmitNext(SseFormatter.toolCall(toolName, arguments));

                            // 执行工具调用
                            String toolResult = toolRegistry.executeTool(toolName, arguments);

                            // 验证工具结果（集成自纠错机制）
                            ValidationResult validationResult = selfCorrectingAgent.validate(toolName, toolResult, arguments);
                            if (!validationResult.isValid()) {
                                log.warn("工具 {} 执行结果验证失败（继续）: {}，尝试纠错", toolName, validationResult.getErrorMessage());

                                // 构建纠错上下文
                                FallbackHandler.FallbackContext correctionContext = new FallbackHandler.FallbackContext(
                                    userMessage,
                                    userId,
                                    conversationId,
                                    intentType,
                                    toolResult,
                                    toolRegistry.getToolDescriptions(),
                                    messages
                                );

                                // 触发纠错流程
                                String correctedResult = selfCorrectingAgent.correctAndRetry(validationResult, correctionContext).block();
                                if (correctedResult != null && !correctedResult.isEmpty()) {
                                    log.info("纠错成功（继续），使用纠错后的结果");
                                    toolResult = correctedResult;
                                } else {
                                    // 纠错失败，发送错误事件给前端
                                    sink.tryEmitNext(SseFormatter.error("工具执行结果异常：" + validationResult.getErrorMessage()));
                                }
                            }

                            toolCallInfos.add(SendMessageResponse.ToolCallInfo.builder()
                                .name(toolName)
                                .arguments(arguments)
                                .result(toolResult)
                                .build());
                            log.info("工具执行完成（继续）: tool={}, result={}", toolName, toolResult);

                            // 添加工具调用消息
                            Message assistantMsg = Message.builder()
                                .role("assistant")
                                .content(null)
                                .toolCalls(List.of(
                                    com.mrshudson.mcp.kimi.dto.ToolCall.builder()
                                        .id(toolCallId)
                                        .type("function")
                                        .function(com.mrshudson.mcp.kimi.dto.ToolCall.Function.builder()
                                            .name(toolName)
                                            .arguments(arguments)
                                            .build())
                                        .build()
                                ))
                                .build();
                            messages.add(assistantMsg);

                            // 添加工具结果消息
                            messages.add(Message.tool(toolCallId, toolResult));

                            // 发送 tool_result 事件
                            sink.tryEmitNext(SseFormatter.toolResult(toolName, toolResult));

                            // 递归继续
                            activeSubscriptions.incrementAndGet();
                            continueAiStream(messages, tools, toolCallInfos, sink, aiFinalContent, activeSubscriptions, userMessage, intentType, userId, conversationId);
                        }
                    } else {
                        // 普通内容：累加到最终内容，发送给前端
                        aiFinalContent.set(aiFinalContent.get() + chunk);
                        // 发送 content 事件（后续由 flatMap 统一处理）
                        sink.tryEmitNext(chunk);
                    }
                },
                error -> {
                    // 处理错误
                    log.error("继续 AI 流订阅错误: {}", error.getMessage(), error);
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                },
                () -> {
                    // 处理完成
                    log.debug("继续 AI 流正常完成");
                    if (activeSubscriptions.decrementAndGet() == 0) {
                        sink.tryEmitComplete();
                    }
                }
        );
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessageList(Long userId, Long conversationId, String userContent) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildSystemPrompt()));

        // 查询历史消息
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT 20");

        if (conversationId != null) {
            wrapper.eq(ChatMessage::getConversationId, conversationId);
        }

        List<ChatMessage> history = chatMessageMapper.selectList(wrapper);
        Collections.reverse(history);

        // 取最�?0�?
        int startIndex = Math.max(0, history.size() - 10);
        for (int i = startIndex; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if ("USER".equalsIgnoreCase(msg.getRole())) {
                messages.add(Message.user(msg.getContent()));
            } else if ("ASSISTANT".equalsIgnoreCase(msg.getRole())) {
                messages.add(Message.assistant(msg.getContent()));
            }
        }

        messages.add(Message.user(userContent));
        return messages;
    }

    /**
     * 构建澄清提示
     */
    private String buildClarificationPrompt(String message, IntentResult intentResult) {
        if (intentResult.isAmbiguous() && intentResult.getCandidates() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("我不太确定您的意思，请问您是想：\n");
            for (int i = 0; i < intentResult.getCandidates().size(); i++) {
                sb.append(i + 1).append(". ").append(intentResult.getCandidates().get(i).getDescription()).append("\n");
            }
            return sb.toString();
        }

        if (intentResult.getConfidence() < 0.8 && intentResult.getType() != IntentType.UNKNOWN) {
            return "抱歉，我没有完全理解您的意思。您能说得更具体一些吗？";
        }

        return null;
    }

    /**
     * 保存用户消息
     */
    private void saveUserMessage(Long userId, Long conversationId, String content) {
        ChatMessage userMsg = new ChatMessage();
        userMsg.setUserId(userId);
        userMsg.setConversationId(conversationId);
        userMsg.setRole(ChatMessage.Role.USER.name());
        userMsg.setContent(content);
        chatMessageMapper.insert(userMsg);
    }

    /**
     * 保存AI响应消息
     */
    private void saveAssistantMessage(Long userId, Long conversationId, String content, String functionCall, String audioUrl) {
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setUserId(userId);
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole(ChatMessage.Role.ASSISTANT.name());
        assistantMsg.setContent(content);
        if (functionCall != null && !functionCall.isEmpty()) {
            assistantMsg.setFunctionCall(functionCall);
        }
        if (audioUrl != null && !audioUrl.isEmpty()) {
            assistantMsg.setAudioUrl(audioUrl);
        }
        chatMessageMapper.insert(assistantMsg);
    }

    /**
     * 更新会话最后消息时�?
     */
    private void updateConversationLastMessageTime(Long conversationId) {
        if (conversationId == null) {
            return;
        }
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

        log.info("检查会话 {} 是否需要生成标题，当前用户消息数 {}", conversationId, userMessageCount);

        if (userMessageCount == 1) {
            log.info("会话 {} 是第一条消息，调用 AsyncTaskService 生成标题", conversationId);
            asyncTaskService.generateConversationTitle(conversationId, firstMessage);
        }
    }
}
