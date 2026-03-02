package com.mrshudson.service;

import com.mrshudson.domain.dto.*;

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

    /**
     * 获取指定会话的对话历史
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @param limit          限制条数
     * @return 对话历史
     */
    ChatHistoryResponse getChatHistoryByConversation(Long userId, Long conversationId, int limit);

    /**
     * 创建新会话
     *
     * @param userId  用户ID
     * @param request 创建会话请求
     * @return 会话DTO
     */
    ConversationDTO createConversation(Long userId, CreateConversationRequest request);

    /**
     * 获取用户的会话列表
     *
     * @param userId 用户ID
     * @param limit  限制条数
     * @return 会话列表
     */
    ConversationListResponse getConversationList(Long userId, int limit);

    /**
     * 删除会话
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     */
    void deleteConversation(Long userId, Long conversationId);

    /**
     * 获取当前AI提供者信息
     *
     * @return AI提供者响应
     */
    AIProviderResponse getAIProviders();
}
