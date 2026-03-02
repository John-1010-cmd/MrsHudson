package com.mrshudson.controller;

import com.mrshudson.domain.dto.*;
import com.mrshudson.service.AuthService;
import com.mrshudson.service.ChatService;
import com.mrshudson.service.VoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 * 对话控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;
    private final VoiceService voiceService;

    /**
     * 发送消息
     */
    @PostMapping("/send")
    public Result<SendMessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        Long userId = authService.getCurrentUser().getId();
        SendMessageResponse response = chatService.sendMessage(userId, request);
        return Result.success(response);
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/history")
    public Result<ChatHistoryResponse> getHistory(
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = authService.getCurrentUser().getId();
        ChatHistoryResponse response = chatService.getChatHistory(userId, limit);
        return Result.success(response);
    }

    /**
     * 获取指定会话的对话历史
     */
    @GetMapping("/history/{conversationId}")
    public Result<ChatHistoryResponse> getHistoryByConversation(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "50") int limit) {
        Long userId = authService.getCurrentUser().getId();
        ChatHistoryResponse response = chatService.getChatHistoryByConversation(userId, conversationId, limit);
        return Result.success(response);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/conversation")
    public Result<ConversationDTO> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = authService.getCurrentUser().getId();
        ConversationDTO response = chatService.createConversation(userId, request);
        return Result.success(response);
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public Result<ConversationListResponse> getConversationList(
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = authService.getCurrentUser().getId();
        ConversationListResponse response = chatService.getConversationList(userId, limit);
        return Result.success(response);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversation/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        Long userId = authService.getCurrentUser().getId();
        chatService.deleteConversation(userId, conversationId);
        return Result.success();
    }

    /**
     * 获取AI提供者信息
     */
    @GetMapping("/providers")
    public Result<AIProviderResponse> getAIProviders() {
        AIProviderResponse response = chatService.getAIProviders();
        return Result.success(response);
    }

    /**
     * 语音输入 - 语音识别后发送消息
     */
    @PostMapping("/voice")
    public Result<VoiceMessageResponse> voiceMessage(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "format", defaultValue = "wav") String format,
            @RequestParam(value = "sampleRate", defaultValue = "16000") Integer sampleRate,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "conversationId", required = false) Long conversationId) {
        Long userId = authService.getCurrentUser().getId();

        // 1. 语音识别
        log.info("收到语音消息，用户ID: {}, 文件大小: {} bytes", userId, audioFile.getSize());
        String recognizedText = voiceService.speechToText(audioFile, format, sampleRate);
        log.info("语音识别结果: {}", recognizedText);

        // 2. 构造消息请求
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(recognizedText);
        request.setSessionId(sessionId);
        request.setConversationId(conversationId);

        // 3. 调用对话服务
        SendMessageResponse chatResponse = chatService.sendMessage(userId, request);

        // 4. 构造语音消息响应
        VoiceMessageResponse response = new VoiceMessageResponse();
        response.setMessageId(chatResponse.getMessageId());
        response.setRecognizedText(recognizedText);
        response.setContent(chatResponse.getContent());
        response.setCreatedAt(chatResponse.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 转换函数调用信息
        if (chatResponse.getFunctionCalls() != null && !chatResponse.getFunctionCalls().isEmpty()) {
            java.util.List<VoiceMessageResponse.ToolCallInfo> toolCalls = new ArrayList<>();
            for (var func : chatResponse.getFunctionCalls()) {
                VoiceMessageResponse.ToolCallInfo toolCall = new VoiceMessageResponse.ToolCallInfo();
                toolCall.setName(func.getName());
                toolCall.setArguments(func.getArguments());
                toolCall.setResult(func.getResult());
                toolCalls.add(toolCall);
            }
            response.setFunctionCalls(toolCalls);
        }

        return Result.success(response);
    }
}
