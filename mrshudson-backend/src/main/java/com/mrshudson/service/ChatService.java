package com.mrshudson.service;

import com.mrshudson.domain.dto.ChatHistoryResponse;
import com.mrshudson.domain.dto.SendMessageRequest;
import com.mrshudson.domain.dto.SendMessageResponse;

import java.util.List;

/**
 * 对话服务接口
 */
public interface ChatService {

    /**
     * 发送消息
     *
     * @param userId  用户ID
     * @param request 发送消息请求
     * @return 发送消息响应
     */
    SendMessageResponse sendMessage(Long userId, SendMessageRequest request);

    /**
     * 获取对话历史
     *
     * @param userId 用户ID
     * @param limit  限制条数
     * @return 对话历史
     */
    ChatHistoryResponse getChatHistory(Long userId, int limit);
}
