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
        try {
            Long userId = authService.getCurrentUser().getId();
            SendMessageResponse response = chatService.sendMessage(userId, request);
            return Result.success(response);
        } catch (RuntimeException e) {
            log.warn("发送消息失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/history")
    public Result<ChatHistoryResponse> getHistory(
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Long userId = authService.getCurrentUser().getId();
            ChatHistoryResponse response = chatService.getChatHistory(userId, limit);
            return Result.success(response);
        } catch (RuntimeException e) {
            log.warn("获取历史失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 语音输入 - 语音识别后发送消息
     */
    @PostMapping("/voice")
    public Result<VoiceMessageResponse> voiceMessage(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "format", defaultValue = "wav") String format,
            @RequestParam(value = "sampleRate", defaultValue = "16000") Integer sampleRate,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            Long userId = authService.getCurrentUser().getId();

            // 1. 语音识别
            log.info("收到语音消息，用户ID: {}, 文件大小: {} bytes", userId, audioFile.getSize());
            String recognizedText = voiceService.speechToText(audioFile, format, sampleRate);
            log.info("语音识别结果: {}", recognizedText);

            // 2. 构造消息请求
            SendMessageRequest request = new SendMessageRequest();
            request.setMessage(recognizedText);
            request.setSessionId(sessionId);

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

        } catch (RuntimeException e) {
            log.warn("语音消息处理失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("语音消息处理异常", e);
            return Result.error("语音处理失败: " + e.getMessage());
        }
    }
}
