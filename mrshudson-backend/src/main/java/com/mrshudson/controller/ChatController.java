package com.mrshudson.controller;

import com.mrshudson.domain.dto.*;
import com.mrshudson.service.AuthService;
import com.mrshudson.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
}
