package com.mrshudson.service;

import java.util.List;

/**
 * 异步任务服务接口
 */
public interface AsyncTaskService {

    /**
     * 异步生成会话标题
     *
     * @param conversationId 会话ID
     * @param firstMessage   第一条用户消息
     */
    void generateConversationTitle(Long conversationId, String firstMessage);
}
