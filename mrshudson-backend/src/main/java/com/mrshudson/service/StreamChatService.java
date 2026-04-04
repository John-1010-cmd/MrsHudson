package com.mrshudson.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.ai.AIClientFactory;
import com.mrshudson.ai.AIProvider;
import com.mrshudson.domain.dto.SendMessageRequest;
import com.mrshudson.domain.dto.SendMessageResponse;
import com.mrshudson.domain.entity.ChatMessage;
import com.mrshudson.domain.entity.Conversation;
import com.mrshudson.mapper.ChatMessageMapper;
import com.mrshudson.mapper.ConversationMapper;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.mcp.minimax.MiniMaxClient;
import com.mrshudson.optim.context.ContextManager;
import com.mrshudson.optim.correction.SelfCorrectingAgent;
import com.mrshudson.optim.correction.ValidationResult;
import com.mrshudson.optim.cost.CostOptimizer;
import com.mrshudson.optim.cost.ModelRouter;
import com.mrshudson.optim.fallback.FallbackHandler;
import com.mrshudson.optim.intent.IntentConfidenceEvaluator;
import com.mrshudson.optim.intent.IntentResult;
import com.mrshudson.optim.intent.IntentRouter;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.optim.quality.QualityOptimizer;
import com.mrshudson.optim.token.TokenTrackerService;
import com.mrshudson.optim.token.TokenUsage;
import com.mrshudson.util.SseFormatter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 流式对话服务 提供真正的SSE流式响应，支持Token统计追加
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
  private final ModelRouter modelRouter;

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
   * 流式发送消�? 返回JSON格式的SSE数据�?
   *
   * @param userId 用户ID
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
      // 缓存命中时，去掉末尾的 token 统计信息（避免重复追加）
      String cachedResponse = cacheResult.getResponse();
      int statsIdx = cachedResponse.indexOf("\n\n--- 💡 本次对话消耗 ---");
      if (statsIdx > 0) {
        cachedResponse = cachedResponse.substring(0, statsIdx);
      }
      Long messageId = saveAssistantMessage(userId, conversationId, cachedResponse, null, null);
      checkAndGenerateTitle(conversationId, userMessage);
      // 发送 cache_hit 事件，然后走完整的 content_done + TTS + audio_done + done 流程
      return buildAsyncTtsFlux(
          Flux.just(SseFormatter.cacheHit(cachedResponse)),
          cachedResponse, conversationId, messageId);
    }

    // 3. 意图路由 - 直接使用 route() 获取结果，避免与 IntentConfidenceEvaluator 重复评估
    RouteResult routeResult = intentRouter.route(userId, userMessage);
    IntentType intentType = routeResult.getIntentType();

    // 4. 检查意图路由是否已直接处理（如天气查询、闲聊等）
    if (routeResult.isHandled()) {
      log.info("意图路由直接处理: type={}, layer={}", routeResult.getIntentType(),
          routeResult.getRouterLayer());
      String response = routeResult.getResponse();
      if (response != null && !response.isEmpty()) {
        Long messageId = saveAssistantMessage(userId, conversationId, response, null, null);
        checkAndGenerateTitle(conversationId, userMessage);
        return buildAsyncTtsFlux(
            Flux.just(SseFormatter.content(response, conversationId, messageId)),
            response, conversationId, messageId);
      }
      // 如果路由说已处理但没有响应内容，继续走AI流程（可能是需要AI执行工具）
    }

    // 5. 仅在路由未处理时检查是否需要澄清（使用 IntentConfidenceEvaluator）
    if (!routeResult.isHandled()) {
      IntentResult intentResult = intentConfidenceEvaluator.evaluate(userMessage, intentType);
      if (intentResult.needsClarification()) {
        String clarification = buildClarificationPrompt(userMessage, intentResult);
        if (clarification != null) {
          Long messageId = saveAssistantMessage(userId, conversationId, clarification, null, null);
          checkAndGenerateTitle(conversationId, userMessage);
          // 无 TTS，意图澄清只需 clarification + done，不发 content_done / audio_done / token_usage
          return Flux.just(
                SseFormatter.clarification(clarification),
                SseFormatter.done()
            );
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
    AtomicReference<String> aiFinalContent = new AtomicReference<>("");
    AtomicReference<TokenUsage> tokenUsageRef = new AtomicReference<>();
    List<SendMessageResponse.ToolCallInfo> toolCallInfos =
        Collections.synchronizedList(new ArrayList<>());

    // 预保存占位消息，提前拿到 messageId，使 content 事件能携带正确的 messageId
    Long messageId = saveAssistantMessage(userId, conversationId, "", null, null);

    // cancelSink：与本次请求绑定，客户端断开时通过 doOnCancel 触发
    Sinks.One<Void> cancelSink = Sinks.one();

    // 用 Sink 驱动整个流（AI内容 + content_done + TTS异步 + audio_done + token_usage + done）
    Sinks.Many<String> mainSink = Sinks.many().unicast().onBackpressureBuffer();

    // contentDoneEmitted：标记 content_done 是否已发出，用于客户端断开时的 TTS 兜底判断
    java.util.concurrent.atomic.AtomicBoolean contentDoneEmitted = new java.util.concurrent.atomic.AtomicBoolean(false);

    Flux<String> aiStream = executeStreamingAiCallWithTools(messages,
        toolRegistry.getToolDefinitions(), userId, conversationId, messageId, toolCallInfos,
        aiFinalContent, tokenUsageRef, userMessage, intentType, new AtomicInteger(0),
              cancelSink);

    // 10. 订阅 AI 流，内容推入 mainSink，完成时触发异步 TTS
    AtomicInteger outputTokens = new AtomicInteger(0);

    aiStream.subscribe(chunk -> {
      outputTokens.addAndGet(tokenTrackerService.estimateTokens(chunk));
      // executeStreamingAiCallWithTools 内部已将 content 包装为 JSON 事件，直接转发
      mainSink.tryEmitNext(chunk);
    }, error -> {
      log.error("AI 流异常", error);
      mainSink.tryEmitNext(SseFormatter.error(error.getMessage()));
      mainSink.tryEmitNext(SseFormatter.done());
      mainSink.tryEmitComplete();
    }, () -> {
      // AI 内容流结束
      tokenTrackerService.recordOutputTokens(trackingId, outputTokens.get());
      TokenUsage usage = tokenTrackerService.getUsage(trackingId);
      tokenUsageRef.set(usage);

      String finalContent = aiFinalContent.get();
      if (finalContent == null || finalContent.isEmpty()) {
        emitTokenUsageAndDone(mainSink, usage);
        return;
      }

      // 更新预保存消息的内容和 functionCall
      String functionCallJson = toolCallInfos.isEmpty() ? null : JSON.toJSONString(toolCallInfos);
      updateAssistantMessageContent(messageId, finalContent, functionCallJson);

      // 发送 content_done，并标记已发出
      contentDoneEmitted.set(true);
      mainSink.tryEmitNext(SseFormatter.contentDone(conversationId, messageId));

      // 异步 TTS（带超时）
      // 不在 supplyAsync 内 try-catch：让异常传播到 exceptionally 块，
      // 以正确区分 error（SDK 异常）vs noaudio（配置缺失/mock 模式）
      CompletableFuture<String> ttsFuture = CompletableFuture.supplyAsync(() -> {
          return voiceService.textToSpeech(finalContent);
      });

      ttsFuture.orTimeout(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS).thenAccept(audioUrl -> {
        if (audioUrl != null && !audioUrl.isEmpty()) {
          updateMessageAudioUrl(messageId, audioUrl);
          mainSink.tryEmitNext(
              SseFormatter.audioDone(audioUrl, false, null, conversationId, messageId));
        } else {
          mainSink
              .tryEmitNext(SseFormatter.audioDone(null, false, null, conversationId, messageId));
        }
        emitTokenUsageAndDone(mainSink, tokenUsageRef.get());
      }).exceptionally(ex -> {
        if (ex.getCause() instanceof TimeoutException) {
          log.warn("TTS 超时（{}ms），后台继续合成", TTS_TIMEOUT_MS);
          mainSink.tryEmitNext(SseFormatter.audioDone(null, true, null, conversationId, messageId));
          ttsFuture.thenAccept(audioUrl -> {
            if (audioUrl != null && !audioUrl.isEmpty()) {
              updateMessageAudioUrl(messageId, audioUrl);
              log.info("TTS 超时后完成，audioUrl 已写入数据库，messageId={}", messageId);
            }
          });
        } else {
          log.error("TTS 异常", ex);
          mainSink.tryEmitNext(
              SseFormatter.audioDone(null, false, "TTS_FAILED", conversationId, messageId));
        }
        emitTokenUsageAndDone(mainSink, tokenUsageRef.get());
        return null;
      });

      costOptimizer.saveCache(userId, userMessage, finalContent);

      if (conversationId != null) {
        updateConversationLastMessageTime(conversationId);
        checkAndGenerateTitle(conversationId, userMessage);
      }
    });

    return mainSink.asFlux()
        .doOnCancel(() -> {
          log.info("客户端断开，取消 AI 流，userId={}, messageId={}", userId, messageId);
          // 触发 takeUntilOther，终止 AI 流
          cancelSink.tryEmitEmpty();
          // TTS 兜底：content_done 已发出则静默合成写 DB，否则保存已有内容
          handleClientDisconnect(messageId, contentDoneEmitted.get(), aiFinalContent.get(),
              toolCallInfos);
        });
  }

  private static final long TTS_TIMEOUT_MS = 10000;

  /**
   * 工具调用递归深度限制
   */
  private static final int MAX_TOOL_RECURSION_DEPTH = 5;

  private void emitTokenUsageAndDone(Sinks.Many<String> sink, TokenUsage usage) {
    if (usage != null) {
      sink.tryEmitNext(
          SseFormatter.tokenUsage(usage.getInputTokens() != null ? usage.getInputTokens() : 0,
              usage.getOutputTokens() != null ? usage.getOutputTokens() : 0,
              usage.getDuration() != null ? usage.getDuration() : 0L,
              usage.getModel() != null ? usage.getModel() : "unknown"));
    }
    sink.tryEmitNext(SseFormatter.done());
    sink.tryEmitComplete();
  }

  /**
   * 构建异步 TTS Flux（意图路由 / 澄清 / 缓存命中路径复用）
   * 顺序：contentFlux → content_done → audio_done → done
   */
  private Flux<String> buildAsyncTtsFlux(Flux<String> contentFlux, String text, Long conversationId,
      Long messageId) {
    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    CompletableFuture<String> ttsFuture = CompletableFuture.supplyAsync(() -> {
        return voiceService.textToSpeech(text);
    });

    ttsFuture.orTimeout(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS).thenAccept(audioUrl -> {
      if (audioUrl != null && !audioUrl.isEmpty()) {
        updateMessageAudioUrl(messageId, audioUrl);
        sink.tryEmitNext(SseFormatter.audioDone(audioUrl, false, null, conversationId, messageId));
      } else {
        sink.tryEmitNext(SseFormatter.audioDone(null, false, null, conversationId, messageId));
      }
      sink.tryEmitNext(SseFormatter.done());
      sink.tryEmitComplete();
    }).exceptionally(ex -> {
      if (ex.getCause() instanceof TimeoutException) {
        log.warn("TTS 超时（{}ms），后台继续合成", TTS_TIMEOUT_MS);
        sink.tryEmitNext(SseFormatter.audioDone(null, true, null, conversationId, messageId));
        ttsFuture.thenAccept(audioUrl -> {
          if (audioUrl != null && !audioUrl.isEmpty()) {
            updateMessageAudioUrl(messageId, audioUrl);
            log.info("TTS 超时后完成，audioUrl 已写入数据库，messageId={}", messageId);
          }
        });
      } else {
        log.error("TTS 异常", ex);
        sink.tryEmitNext(
            SseFormatter.audioDone(null, false, "TTS_FAILED", conversationId, messageId));
      }
      sink.tryEmitNext(SseFormatter.done());
      sink.tryEmitComplete();
      return null;
    });

    // contentFlux 先发，然后 content_done，再等 sink（audio_done + done）
    return contentFlux
        .concatWith(Flux.just(SseFormatter.contentDone(conversationId, messageId)))
        .mergeWith(sink.asFlux());
  }

  /**
   * 更新消息的 audioUrl
   */
  private void updateMessageAudioUrl(Long messageId, String audioUrl) {
    if (messageId == null || audioUrl == null)
      return;
    ChatMessage msg = new ChatMessage();
    msg.setId(messageId);
    msg.setAudioUrl(audioUrl);
    chatMessageMapper.updateById(msg);
  }

  /**
   * 更新预保存消息的内容和 functionCall（流结束后回填）
   */
  private void updateAssistantMessageContent(Long messageId, String content, String functionCall) {
    if (messageId == null)
      return;
    ChatMessage msg = new ChatMessage();
    msg.setId(messageId);
    msg.setContent(content);
    if (functionCall != null && !functionCall.isEmpty()) {
      msg.setFunctionCall(functionCall);
    }
    chatMessageMapper.updateById(msg);
  }

  /**
   * 客户端断开时的处理逻辑
   * content_done 已发出 → accumulatedContent 即为完整 AI 回复，TTS 静默合成写 DB
   * content_done 未发出 → 内容不完整，保存已有部分，不启动 TTS
   */
  private void handleClientDisconnect(Long messageId, boolean contentDoneEmitted,
      String accumulatedContent, List<SendMessageResponse.ToolCallInfo> toolCallInfos) {
    if (messageId == null) return;
    if (contentDoneEmitted) {
      // content_done 已发出：accumulatedContent 即为完整 AI 回复
      String functionCallJson = (toolCallInfos == null || toolCallInfos.isEmpty())
          ? null : JSON.toJSONString(toolCallInfos);
      updateAssistantMessageContent(messageId, accumulatedContent, functionCallJson);
      // TTS 静默合成，结果写 DB，供历史消息使用
      CompletableFuture.runAsync(() -> {
        try {
          String audioUrl = voiceService.textToSpeech(accumulatedContent);
          if (audioUrl != null && !audioUrl.isEmpty()) {
            updateMessageAudioUrl(messageId, audioUrl);
            log.info("客户端断开后 TTS 完成，audioUrl 已写入数据库，messageId={}", messageId);
          }
        } catch (Exception e) {
          log.warn("客户端断开后 TTS 合成失败，messageId={}", messageId, e);
        }
      });
    } else {
      // content_done 未发出：内容不完整，保存已有部分，不启动 TTS
      if (accumulatedContent != null && !accumulatedContent.isEmpty()) {
        updateAssistantMessageContent(messageId, accumulatedContent, null);
        log.info("客户端断开，保存不完整内容，messageId={}", messageId);
      }
    }
  }

  /**
   * 执行流式AI调用（支持工具调用）
   *
   * @param messages 消息列表（会被修改，添加工具调用和结果）
   * @param tools 工具定义列表
   * @param userId 用户ID
   * @param conversationId 会话ID
   * @param aiFinalContent OUT参数：AI的最终文本内容（不含工具调用/结果事件�?
   * @param tokenUsageRef OUT参数：Token使用统计
   * @param userMessage 用户原始消息（用于纠错上下文）
   * @param intentType 识别的意图类型（用于纠错上下文）
   * @return 事件流（包含 tool_call、tool_result、content 事件�?
   */
  private Flux<String> executeStreamingAiCallWithTools(List<Message> messages, List<Tool> tools,
      Long userId, Long conversationId, Long messageId,
      List<SendMessageResponse.ToolCallInfo> toolCallInfos, AtomicReference<String> aiFinalContent,
      AtomicReference<TokenUsage> tokenUsageRef, String userMessage, IntentType intentType,
      AtomicInteger recursionDepth,
      Sinks.One<Void> cancelSink) {

    // 用于收集所有事件的 Sinks
    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    // 活跃订阅计数器，用于追踪异步流程
    AtomicInteger activeSubscriptions = new AtomicInteger(1);

    // 订阅 AI 流式响应，通过 takeUntilOther 编织取消信号
    AIProvider provider = aiClientFactory.getCurrentProvider();
    // 使用 ModelRouter 动态选择模型（仅对 Kimi 生效）
    String selectedModel = modelRouter.getModel(userMessage);
    log.debug("ModelRouter 选择模型: {}，用户消息: {}", selectedModel, userMessage.substring(0, Math.min(50, userMessage.length())));
    Flux<String> aiStream =
        (provider == AIProvider.KIMI ? kimiClient.streamChatCompletion(messages, tools, selectedModel)
            : miniMaxClient.streamChatCompletion(messages, tools))
                .takeUntilOther(cancelSink.asMono())  // 客户端断开时终止 AI 流
                .doOnSubscribe(s -> log.debug("AI 流式响应开始")).doOnError(e -> {
                  log.error("AI 流式响应异常: {}", e.getMessage(), e);
                  sink.tryEmitError(e);
                  if (activeSubscriptions.decrementAndGet() == 0) {
                    sink.tryEmitComplete();
                  }
                }).doOnCancel(() -> {
                  log.debug("AI 流式响应取消");
                  if (activeSubscriptions.decrementAndGet() == 0) {
                    sink.tryEmitComplete();
                  }
                });

    aiStream.subscribe(chunk -> {
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
          sink.tryEmitNext(SseFormatter.toolCall(toolName, arguments, conversationId));

          // 2. 执行工具调用
          String toolResult = toolRegistry.executeTool(toolName, arguments);
          toolCallInfos.add(SendMessageResponse.ToolCallInfo.builder().name(toolName)
              .arguments(arguments).result(toolResult).build());
          log.info("工具执行完成: tool={}, result={}", toolName, toolResult);

          // 3. 验证工具结果（集成自纠错机制）
          ValidationResult validationResult =
              selfCorrectingAgent.validate(toolName, toolResult, arguments);
          if (!validationResult.isValid()) {
            log.warn("工具 {} 执行结果验证失败: {}，尝试纠错", toolName, validationResult.getErrorMessage());

            // 构建纠错上下文
            FallbackHandler.FallbackContext correctionContext =
                new FallbackHandler.FallbackContext(userMessage, userId, conversationId, intentType,
                    toolResult, toolRegistry.getToolDescriptions(), messages);

            // 触发纠错流程
            String correctedResult =
                selfCorrectingAgent.correctAndRetry(validationResult, correctionContext).block();
            if (correctedResult != null && !correctedResult.isEmpty()) {
              log.info("纠错成功，使用纠错后的结果");
              toolResult = correctedResult;
            } else {
              // 纠错失败，发送错误事件给前端
              sink.tryEmitNext(
                  SseFormatter.error("工具执行结果异常：" + validationResult.getErrorMessage()));
            }
          }

          // 4. 将工具调用和结果添加到消息历�?
          // 添加工具调用消息（assistant 角色�?
          Message assistantMsg = Message
              .builder().role("assistant").content(
                  null)
              .toolCalls(List.of(com.mrshudson.mcp.kimi.dto.ToolCall.builder().id(toolCallId)
                  .type("function").function(com.mrshudson.mcp.kimi.dto.ToolCall.Function.builder()
                      .name(toolName).arguments(arguments).build())
                  .build()))
              .build();
          messages.add(assistantMsg);

          // 添加工具结果消息（tool 角色�?
          messages.add(Message.tool(toolCallId, toolResult));

          // 4. 发送 tool_result 事件
          sink.tryEmitNext(SseFormatter.toolResult(toolName, toolResult, conversationId));

          // 5. 继续调用 AI 获取下一轮响应
          // 增加活跃订阅计数
          activeSubscriptions.incrementAndGet();
          continueAiStream(messages, tools, toolCallInfos, sink, aiFinalContent,
              activeSubscriptions, userMessage, intentType, userId, conversationId, messageId, recursionDepth,
              cancelSink);
        }
      } else if (chunk.startsWith("[THINKING]")) {
        // 思考推理过程：发送 thinking 事件，不累加到 aiFinalContent
        String thinkingText = chunk.substring("[THINKING]".length());
        log.debug("AI 思考过程: {}", thinkingText.substring(0, Math.min(50, thinkingText.length())));
        sink.tryEmitNext(SseFormatter.thinking(thinkingText, conversationId, messageId));
      } else {
        // 正式回复：累加到 aiFinalContent，发送 content 事件
        aiFinalContent.set(aiFinalContent.get() + chunk);
        sink.tryEmitNext(SseFormatter.content(chunk, conversationId, messageId));
      }
    }, error -> {
      // 处理错误
      log.error("AI 流订阅错误: {}", error.getMessage(), error);
      if (activeSubscriptions.decrementAndGet() == 0) {
        sink.tryEmitComplete();
      }
    }, () -> {
      // 处理完成 - 正常结束时减少活跃订阅计数
      log.debug("AI 流正常完成");
      if (activeSubscriptions.decrementAndGet() == 0) {
        sink.tryEmitComplete();
      }
    });

    // 返回事件流
    return sink.asFlux().doOnComplete(() -> log.debug("事件流完成"))
        .doOnError(e -> log.error("事件流异常: {}", e.getMessage(), e));
  }

  /**
   * 继续 AI 流式响应（处理工具调用后的下一轮）
   */
  private void continueAiStream(List<Message> messages, List<Tool> tools,
      List<SendMessageResponse.ToolCallInfo> toolCallInfos, Sinks.Many<String> sink,
      AtomicReference<String> aiFinalContent, AtomicInteger activeSubscriptions, String userMessage,
      IntentType intentType, Long userId, Long conversationId, Long messageId,
      AtomicInteger recursionDepth,
      Sinks.One<Void> cancelSink) {


    // 检查递归深度，防止无限递归
    int currentDepth = recursionDepth.incrementAndGet();
    if (currentDepth > MAX_TOOL_RECURSION_DEPTH) {
      log.warn("工具调用递归深度超过限制: {}/{}，停止递归", currentDepth, MAX_TOOL_RECURSION_DEPTH);
      sink.tryEmitNext(SseFormatter.error("工具调用次数过多，请简化您的请求", conversationId));
      if (activeSubscriptions.decrementAndGet() == 0) {
        sink.tryEmitComplete();
      }
      return;
    }
    log.debug("当前工具调用递归深度: {}/{}", currentDepth, MAX_TOOL_RECURSION_DEPTH);

    // 继续订阅 AI 响应
    AIProvider provider = aiClientFactory.getCurrentProvider();
    // 使用 ModelRouter 动态选择模型（仅对 Kimi 生效）
    String continueModel = modelRouter.getModel(userMessage);
    Flux<String> continueStream =
        (provider == AIProvider.KIMI ? kimiClient.streamChatCompletion(messages, tools, continueModel)
            : miniMaxClient.streamChatCompletion(messages, tools))
                .takeUntilOther(cancelSink.asMono())  // 客户端断开时终止后续 AI 流
                .doOnError(e -> {
              log.error("继续 AI 调用异常: {}", e.getMessage(), e);
              sink.tryEmitError(e);
              if (activeSubscriptions.decrementAndGet() == 0) {
                sink.tryEmitComplete();
              }
            }).doOnCancel(() -> {
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
          sink.tryEmitNext(SseFormatter.toolCall(toolName, arguments, conversationId));

          // 执行工具调用
          String toolResult = toolRegistry.executeTool(toolName, arguments);

          // 验证工具结果（集成自纠错机制）
          ValidationResult validationResult =
              selfCorrectingAgent.validate(toolName, toolResult, arguments);
          if (!validationResult.isValid()) {
            log.warn("工具 {} 执行结果验证失败（继续）: {}，尝试纠错", toolName, validationResult.getErrorMessage());

            // 构建纠错上下文
            FallbackHandler.FallbackContext correctionContext =
                new FallbackHandler.FallbackContext(userMessage, userId, conversationId, intentType,
                    toolResult, toolRegistry.getToolDescriptions(), messages);

            // 触发纠错流程
            String correctedResult =
                selfCorrectingAgent.correctAndRetry(validationResult, correctionContext).block();
            if (correctedResult != null && !correctedResult.isEmpty()) {
              log.info("纠错成功（继续），使用纠错后的结果");
              toolResult = correctedResult;
            } else {
              // 纠错失败，发送错误事件给前端
              sink.tryEmitNext(
                  SseFormatter.error("工具执行结果异常：" + validationResult.getErrorMessage()));
            }
          }

          toolCallInfos.add(SendMessageResponse.ToolCallInfo.builder().name(toolName)
              .arguments(arguments).result(toolResult).build());
          log.info("工具执行完成（继续）: tool={}, result={}", toolName, toolResult);

          // 添加工具调用消息
          Message assistantMsg = Message
              .builder().role("assistant").content(
                  null)
              .toolCalls(List.of(com.mrshudson.mcp.kimi.dto.ToolCall.builder().id(toolCallId)
                  .type("function").function(com.mrshudson.mcp.kimi.dto.ToolCall.Function.builder()
                      .name(toolName).arguments(arguments).build())
                  .build()))
              .build();
          messages.add(assistantMsg);

          // 添加工具结果消息
          messages.add(Message.tool(toolCallId, toolResult));

          // 发送 tool_result 事件
          sink.tryEmitNext(SseFormatter.toolResult(toolName, toolResult, conversationId));

          // 递归继续
          activeSubscriptions.incrementAndGet();
          continueAiStream(messages, tools, toolCallInfos, sink, aiFinalContent,
              activeSubscriptions, userMessage, intentType, userId, conversationId, messageId, recursionDepth,
              cancelSink);
        }
      } else if (chunk.startsWith("[THINKING]")) {
        // 思考推理过程：发送 thinking 事件，不累加到 aiFinalContent
        String thinkingText = chunk.substring("[THINKING]".length());
        log.debug("AI 思考过程（继续）: {}", thinkingText.substring(0, Math.min(50, thinkingText.length())));
        sink.tryEmitNext(SseFormatter.thinking(thinkingText, conversationId, messageId));
      } else {
        // 正式回复：累加到 aiFinalContent，发送 content 事件
        aiFinalContent.set(aiFinalContent.get() + chunk);
        sink.tryEmitNext(SseFormatter.content(chunk, conversationId, messageId));
      }
    }, error -> {
      // 处理错误
      log.error("继续 AI 流订阅错误: {}", error.getMessage(), error);
      if (activeSubscriptions.decrementAndGet() == 0) {
        sink.tryEmitComplete();
      }
    }, () -> {
      // 处理完成
      log.debug("继续 AI 流正常完成");
      if (activeSubscriptions.decrementAndGet() == 0) {
        sink.tryEmitComplete();
      }
    });
  }
  /**
   * 构建消息列表
   * 使用 ContextManager 进行智能上下文压缩
   */
  private List<Message> buildMessageList(Long userId, Long conversationId, String userContent) {
    List<Message> messages = new ArrayList<>();
    messages.add(Message.system(buildSystemPrompt()));

    // 使用 ContextManager 获取优化后的历史消息（支持压缩）
    List<Message> optimizedHistory = contextManager.buildOptimizedContext(conversationId, userId);
    messages.addAll(optimizedHistory);

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
        sb.append(i + 1).append(". ").append(intentResult.getCandidates().get(i).getDescription())
            .append("\n");
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
   * 保存AI响应消息，返回消息ID
   */
  private Long saveAssistantMessage(Long userId, Long conversationId, String content,
      String functionCall, String audioUrl) {
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
    return assistantMsg.getId();
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
    LambdaQueryWrapper<ChatMessage> wrapper =
        new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getRole, ChatMessage.Role.USER.name());

    long userMessageCount = chatMessageMapper.selectCount(wrapper);

    log.info("检查会话 {} 是否需要生成标题，当前用户消息数 {}", conversationId, userMessageCount);

    if (userMessageCount == 1) {
      log.info("会话 {} 是第一条消息，调用 AsyncTaskService 生成标题", conversationId);
      asyncTaskService.generateConversationTitle(conversationId, firstMessage);
    }
  }
}
