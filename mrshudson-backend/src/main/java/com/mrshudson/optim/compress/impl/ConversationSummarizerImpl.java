package com.mrshudson.optim.compress.impl;

import com.mrshudson.ai.AIClientFactory;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.ChatResponse;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.optim.compress.CompressionConfig;
import com.mrshudson.optim.compress.ConversationSummarizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话压缩器实现类
 * 使用 AI API 生成对话摘要，减少 Token 消耗
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationSummarizerImpl implements ConversationSummarizer {

    private final CompressionConfig compressionConfig;
    private final AIClientFactory aiClientFactory;

    /**
     * 判断对话是否需要压缩
     *
     * @param messages 消息列表
     * @return true 如果消息数超过阈值
     */
    @Override
    public boolean needsCompression(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.size() > compressionConfig.getTriggerThreshold();
    }

    /**
     * 压缩对话历史
     * 保留最近的消息，将前面的消息压缩为摘要
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    @Override
    public List<Message> compress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        int totalMessages = messages.size();
        int keepRecent = compressionConfig.getKeepRecentMessages();

        // 如果消息数不足，无需压缩
        if (totalMessages <= keepRecent) {
            return messages;
        }

        log.debug("开始压缩对话历史，总消息数: {}，保留最近: {} 条", totalMessages, keepRecent);

        // 分割消息：前面需要压缩的部分和后面保留的部分
        List<Message> messagesToSummarize = messages.subList(0, totalMessages - keepRecent);
        List<Message> recentMessages = new ArrayList<>(messages.subList(totalMessages - keepRecent, totalMessages));

        // 生成摘要
        String summary = generateSummary(messagesToSummarize);

        // 构建压缩后的消息列表
        List<Message> compressedMessages = new ArrayList<>();

        // 添加摘要作为系统消息
        if (summary != null && !summary.isEmpty()) {
            String summaryContent = "【历史对话摘要】" + summary;
            compressedMessages.add(Message.system(summaryContent));
            log.debug("生成摘要: {}", summary);
        }

        // 添加保留的最近消息
        compressedMessages.addAll(recentMessages);

        log.info("对话压缩完成，原始消息数: {}，压缩后消息数: {}，摘要长度: {} 字",
                totalMessages, compressedMessages.size(),
                summary != null ? summary.length() : 0);

        return compressedMessages;
    }

    /**
     * 生成对话摘要
     *
     * @param messages 需要摘要的消息列表
     * @return 生成的摘要文本
     */
    private String generateSummary(List<Message> messages) {
        try {
            String prompt = generateSummaryPrompt(messages);

            // 使用轻量级系统提示词
            String systemPrompt = "你是一个对话摘要助手。请用简洁的语言概括对话主题，严格限制字数。";

            KimiClient aiClient = (KimiClient) aiClientFactory.getClient();
            ChatResponse response = aiClient.chatCompletion(
                    List.of(
                            Message.system(systemPrompt),
                            Message.user(prompt)
                    )
            );

            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                String summary = response.getChoices().get(0).getMessage().getContent();
                // 清理摘要，确保不超过最大长度
                return truncateSummary(summary);
            }

            return "对话历史已压缩";

        } catch (Exception e) {
            log.error("生成对话摘要失败: {}", e.getMessage(), e);
            // 降级处理：返回简单的占位摘要
            return "对话历史已压缩（摘要生成失败）";
        }
    }

    /**
     * 生成摘要提示词
     *
     * @param messages 需要摘要的消息列表
     * @return 格式化的提示词
     */
    private String generateSummaryPrompt(List<Message> messages) {
        StringBuilder dialogContent = new StringBuilder();

        for (Message message : messages) {
            String role = message.getRole();
            String content = message.getContent();

            if (content == null || content.isEmpty()) {
                continue;
            }

            // 根据角色显示不同前缀
            String roleLabel = switch (role) {
                case "user" -> "用户";
                case "assistant" -> "助手";
                case "system" -> "系统";
                default -> role;
            };

            dialogContent.append(roleLabel).append("：").append(content).append("\n");
        }

        int maxLength = compressionConfig.getSummaryMaxLength();

        return String.format(
                "用%d字以内概括以下对话主题：\n\n%s",
                maxLength,
                dialogContent.toString()
        );
    }

    /**
     * 截断摘要至最大长度
     *
     * @param summary 原始摘要
     * @return 截断后的摘要
     */
    private String truncateSummary(String summary) {
        if (summary == null) {
            return "";
        }

        int maxLength = compressionConfig.getSummaryMaxLength();

        if (summary.length() <= maxLength) {
            return summary;
        }

        // 截断并添加省略号
        return summary.substring(0, maxLength - 3) + "...";
    }
}
