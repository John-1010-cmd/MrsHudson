package com.mrshudson.optim.compress;

import com.mrshudson.mcp.kimi.dto.Message;

import java.util.List;

/**
 * 对话压缩器接口
 * 用于判断是否需要压缩对话历史并执行压缩操作
 */
public interface ConversationSummarizer {

    /**
     * 判断对话是否需要压缩
     *
     * @param messages 消息列表
     * @return true 如果需要压缩，false 否则
     */
    boolean needsCompression(List<Message> messages);

    /**
     * 压缩对话历史
     * 保留最近的消息，将前面的消息压缩为摘要
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    List<Message> compress(List<Message> messages);
}
