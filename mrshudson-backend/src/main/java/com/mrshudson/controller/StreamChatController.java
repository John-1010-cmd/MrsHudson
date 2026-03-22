package com.mrshudson.controller;

import com.mrshudson.domain.dto.SendMessageRequest;
import com.mrshudson.service.AuthService;
import com.mrshudson.service.StreamChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;



/**
 * 流式对话控制器
 * 提供 SSE (Server-Sent Events) 流式响应端点
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class StreamChatController {

    private final StreamChatService streamChatService;
    private final AuthService authService;

    /**
     * 流式发送消息
     * 使用 SSE 协议流式返回 AI 响应
     *
     * @param request 消息请求
     * @return SSE 数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSendMessage(@Valid @RequestBody SendMessageRequest request) {
        Long userId = authService.getCurrentUser().getId();
        log.info("收到流式消息请求，用户ID: {}, 消息: {}", userId, request.getMessage());

        return streamChatService.streamSendMessage(userId, request)
                .doOnSubscribe(s -> log.info("SSE 流式响应开始，用户ID: {}", userId))
                .doOnComplete(() -> log.info("SSE 流式响应完成，用户ID: {}", userId))
                .doOnError(e -> log.error("SSE 流式响应异常，用户ID: {}, 错误: {}", userId, e.getMessage(), e));
    }
}
