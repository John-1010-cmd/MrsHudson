package com.mrshudson.optim.context;

import com.mrshudson.mcp.kimi.dto.Message;
import java.util.List;

/**
 * 上下文管理器接口
 * 智能管理对话上下文，支持压缩和截断策略
 */
public interface ContextManager {

    /**
     * 构建优化的上下文
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @return 优化后的消息列表
     */
    List<Message> buildOptimizedContext(Long conversationId, Long userId);

    /**
     * 判断是否需要压缩
     *
     * @param messages 消息列表
     * @return boolean 是否需要压缩
     */
    boolean needsCompression(List<Message> messages);

    /**
     * 执行上下文压缩
     *
     * @param messages 需要压缩的消息
     * @return 压缩后的消息列表
     */
    List<Message> compress(List<Message> messages);

    /**
     * 估算 token 数量
     *
     * @param messages 消息列表
     * @return 估算的 token 数量
     */
    int estimateTokens(List<Message> messages);

    /**
     * 估算单条消息的 token 数量
     *
     * @param content 消息内容
     * @return 估算的 token 数量
     */
    int estimateTokens(String content);
}
