package com.mrshudson.ai;

import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.mcp.kimi.dto.ToolCall;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI服务接口
 */
public interface AIService {

    /**
     * 获取AI提供者
     *
     * @return AI提供者
     */
    AIProvider getProvider();

    /**
     * 对话完成（支持工具调用）
     *
     * @param messages 消息列表
     * @param tools    工具列表（可选）
     * @return AI回复结果（包含内容和工具调用）
     */
    ChatResult chatCompletionWithTools(List<Message> messages, List<Tool> tools);

    /**
     * 对话完成（无工具）
     *
     * @param messages 消息列表
     * @return AI回复内容
     */
    default String chatCompletion(List<Message> messages) {
        ChatResult result = chatCompletionWithTools(messages, null);
        return result.getContent();
    }

    /**
     * 简单的单轮对话
     *
     * @param systemMessage 系统消息
     * @param userMessage   用户消息
     * @return AI回复内容
     */
    default String simpleChat(String systemMessage, String userMessage) {
        List<Message> messages = List.of(
                Message.system(systemMessage),
                Message.user(userMessage)
        );
        return chatCompletion(messages);
    }

    /**
     * AI对话结果
     */
    @Data
    @Builder
    class ChatResult {
        /**
         * AI回复内容
         */
        private String content;

        /**
         * 工具调用列表（如果有）
         */
        private List<ToolCall> toolCalls;
    }
}
