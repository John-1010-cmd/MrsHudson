package com.mrshudson.mcp.kimi.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kimi对话响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 响应ID
     */
    private String id;

    /**
     * 对象类型
     */
    private String object;

    /**
     * 创建时间戳
     */
    private Long created;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 选择列表
     */
    private List<Choice> choices;

    /**
     * Token使用情况
     */
    private Usage usage;

    /**
     * 选择项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        /**
         * 索引
         */
        private Integer index;

        /**
         * 消息
         */
        private Message message;

        /**
         * 结束原因
         */
        @JSONField(name = "finish_reason")
        private String finishReason;
    }

    /**
     * Token使用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        /**
         * 提示token数
         */
        @JSONField(name = "prompt_tokens")
        private Integer promptTokens;

        /**
         * 完成token数
         */
        @JSONField(name = "completion_tokens")
        private Integer completionTokens;

        /**
         * 总token数
         */
        @JSONField(name = "total_tokens")
        private Integer totalTokens;
    }
}
