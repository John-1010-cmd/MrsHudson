package com.mrshudson.mcp.kimi.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 消息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
     * 角色：system, user, assistant, tool
     */
    private String role;

    /**
     * 内容
     */
    private String content;

    /**
     * 工具调用（assistant消息中）
     */
    @JSONField(name = "tool_calls")
    private List<ToolCall> toolCalls;

    /**
     * 工具调用ID（tool消息中）
     */
    @JSONField(name = "tool_call_id")
    private String toolCallId;

    /**
     * 名称（tool消息中）
     */
    private String name;

    /**
     * 创建系统消息
     */
    public static Message system(String content) {
        return Message.builder()
                .role("system")
                .content(content)
                .build();
    }

    /**
     * 创建用户消息
     */
    public static Message user(String content) {
        return Message.builder()
                .role("user")
                .content(content)
                .build();
    }

    /**
     * 创建助手消息
     */
    public static Message assistant(String content) {
        return Message.builder()
                .role("assistant")
                .content(content)
                .build();
    }

    /**
     * 创建工具消息
     */
    public static Message tool(String toolCallId, String content) {
        return Message.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .content(content)
                .build();
    }
}
