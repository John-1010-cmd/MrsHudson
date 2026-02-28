package com.mrshudson.mcp.kimi.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kimi对话请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 工具列表（可选）
     */
    private List<Tool> tools;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大token数
     */
    @JSONField(name = "max_tokens")
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    private Boolean stream;
}
