package com.mrshudson.optim.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.entity.ChatMessage;
import com.mrshudson.mapper.ChatMessageMapper;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.optim.config.OptimProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文管理器实现
 * 智能上下文压缩，保留关键信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextManagerImpl implements ContextManager {

    private final ChatMessageMapper chatMessageMapper;
    private final KimiClient kimClient;
    private final OptimProperties optimProperties;

    /**
     * 触发压缩的消息数量阈值
     */
    private static final int TRIGGER_THRESHOLD = 15;

    /**
     * 保留的最近消息数
     */
    private static final int KEEP_RECENT = 6;

    /**
     * 摘要最大字数
     */
    private static final int SUMMARY_MAX_LENGTH = 200;

    @Override
    public List<Message> buildOptimizedContext(Long conversationId, Long userId) {
        // 1. 获取历史消息
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getUserId, userId);
        
        if (conversationId != null) {
            wrapper.eq(ChatMessage::getConversationId, conversationId);
        }
        
        wrapper.orderByAsc(ChatMessage::getCreatedAt)
            .last("LIMIT 50");

        List<ChatMessage> history = chatMessageMapper.selectList(wrapper);

        if (history.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 转换为 Message 对象
        List<Message> messages = convertToMessages(history);

        // 3. 检查是否需要压缩
        if (!needsCompression(messages)) {
            return messages;
        }

        // 4. 执行压缩
        return compress(messages);
    }

    @Override
    public boolean needsCompression(List<Message> messages) {
        if (messages == null) {
            return false;
        }
        return messages.size() > TRIGGER_THRESHOLD;
    }

    @Override
    public List<Message> compress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        // 如果消息数量 <= 保留数量，无需压缩
        if (messages.size() <= KEEP_RECENT) {
            return messages;
        }

        // 1. 保留最近的消息
        List<Message> recent = new ArrayList<>(messages.subList(
            Math.max(0, messages.size() - KEEP_RECENT), 
            messages.size()
        ));

        // 2. 压缩早期消息
        List<Message> older = new ArrayList<>(messages.subList(
            0, 
            messages.size() - KEEP_RECENT
        ));

        // 3. 生成摘要
        String summary = generateSummary(older);

        // 4. 构建压缩后的上下文
        List<Message> compressed = new ArrayList<>();
        compressed.add(Message.system("【历史摘要】" + summary));
        compressed.addAll(recent);

        log.info("上下文压缩完成：原始 {} 条，压缩后 {} 条，摘要长度: {}",
            messages.size(), compressed.size(), summary.length());

        return compressed;
    }

    @Override
    public int estimateTokens(List<Message> messages) {
        if (messages == null) {
            return 0;
        }
        int total = 0;
        for (Message msg : messages) {
            total += estimateTokens(msg.getContent());
        }
        return total;
    }

    @Override
    public int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        long chineseChars = content.chars().filter(c -> c > 127).count();
        int asciiChars = content.length() - (int) chineseChars;
        return (int) chineseChars + (asciiChars / 4);
    }

    /**
     * 生成对话摘要
     */
    private String generateSummary(List<Message> olderMessages) {
        if (olderMessages.isEmpty()) {
            return "无历史对话";
        }

        try {
            // 构建摘要提示词
            StringBuilder promptContent = new StringBuilder();
            for (Message msg : olderMessages) {
                promptContent.append(msg.getRole()).append(": ")
                    .append(msg.getContent() != null ? msg.getContent() : "[工具调用]")
                    .append("\n");
            }

            String userPrompt = "对话内容：\n" + promptContent.substring(0, Math.min(promptContent.length(), 1000));

            String summaryPrompt = String.format(
                "请用不超过%d字概括以下对话的主题和关键信息，保留重要的人名、时间、地点等实体。\n\n%s",
                SUMMARY_MAX_LENGTH, userPrompt
            );

            // 使用 kimClient 的 simpleChat 方法生成摘要
            String summary = kimClient.simpleChat(
                "你是一个对话摘要助手。",
                summaryPrompt
            );

            if (summary != null && !summary.isEmpty()) {
                log.debug("生成的摘要: {}", summary);
                return summary;
            }

        } catch (Exception e) {
            log.error("生成摘要失败: {}", e.getMessage(), e);
        }

        return "早期对话内容（摘要生成失败）";
    }

    /**
     * 转换实体为 Message 对象
     */
    private List<Message> convertToMessages(List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            if ("USER".equalsIgnoreCase(msg.getRole())) {
                messages.add(Message.user(msg.getContent()));
            } else if ("ASSISTANT".equalsIgnoreCase(msg.getRole())) {
                messages.add(Message.assistant(msg.getContent()));
            }
        }
        return messages;
    }
}
