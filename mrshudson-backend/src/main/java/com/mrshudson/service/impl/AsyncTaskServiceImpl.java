package com.mrshudson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.ai.AIService;
import com.mrshudson.ai.AIServiceFactory;
import com.mrshudson.domain.entity.ChatMessage;
import com.mrshudson.mapper.ChatMessageMapper;
import com.mrshudson.mapper.ConversationMapper;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 异步任务服务实现
 * 解决 Spring AOP 代理自我调用 @Async 方法不生效的问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskServiceImpl implements AsyncTaskService {

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AIServiceFactory aiServiceFactory;

    @Override
    @Async("taskExecutor")
    public void generateConversationTitle(Long conversationId, String firstMessage) {
        try {
            log.info("开始为会话 {} 生成标题", conversationId);

            // 1. 尝试使用预设标题（规则匹配）
            String presetTitle = getPresetTitle(firstMessage);
            if (presetTitle != null) {
                conversationMapper.updateTitle(conversationId, presetTitle);
                log.info("会话 {} 使用预设标题: {}", conversationId, presetTitle);
                return;
            }

            // 2. 复杂对话调用AI生成标题
            String prompt = "请用10-15字概括以下对话的主题，要求简洁明了：\n" + firstMessage;

            AIService aiService = aiServiceFactory.getService();
            AIService.ChatResult result = aiService.chatCompletionWithTools(
                    List.of(Message.system("你是一个对话标题生成助手，请用简洁的语言概括对话主题。"),
                            Message.user(prompt)),
                    null
            );

            String title = result.getContent();
            if (title != null && !title.isEmpty()) {
                // 清理标题（去除引号、换行等）
                title = title.replaceAll("[\"'\n\r]", "").trim();

                // 限制长度
                if (title.length() > 20) {
                    title = title.substring(0, 20);
                }

                // 更新会话标题
                conversationMapper.updateTitle(conversationId, title);
                log.info("会话 {} 标题已更新为: {}", conversationId, title);
            }
        } catch (Exception e) {
            log.error("生成会话标题失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 根据消息内容获取预设标题
     * 返回null表示没有匹配的预设标题，需要AI生成
     */
    private String getPresetTitle(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "新对话";
        }

        String lowerMsg = message.toLowerCase();

        // 问候语
        if (isGreeting(lowerMsg)) {
            return "新对话";
        }

        // 天气查询
        if (containsAny(lowerMsg, "天气", "温度", "下雨", "下雪", "刮风", "晴", "阴", "多云")) {
            return "关于天气的咨询";
        }

        // 日历/日程查询
        if (containsAny(lowerMsg, "日程", "会议", "安排", "日历", "今天", "明天", "下周", "这周")) {
            return "关于日程的咨询";
        }

        // 待办查询
        if (containsAny(lowerMsg, "待办", "任务", "todo", "提醒", "未完成", "要做")) {
            return "关于待办的咨询";
        }

        // 路线规划
        if (containsAny(lowerMsg, "路线", "怎么去", "怎么走", "导航", "从", "到", "距离")) {
            return "关于路线的咨询";
        }

        // 没有匹配的预设标题
        return null;
    }

    /**
     * 检查是否是问候语
     */
    private boolean isGreeting(String message) {
        String[] greetings = {"你好", "您好", "在吗", "hello", "hi", "hey", "早上好", "下午好", "晚上好", "再见", "拜拜"};
        for (String greeting : greetings) {
            if (message.contains(greeting)) {
                // 确保问候语占消息的大部分
                if (message.length() <= greeting.length() + 5) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查消息是否包含任意关键词
     */
    private boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
