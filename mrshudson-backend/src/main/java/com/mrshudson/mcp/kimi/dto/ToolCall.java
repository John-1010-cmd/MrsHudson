package com.mrshudson.mcp.kimi.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /**
     * 工具调用ID
     */
    private String id;

    /**
     * 类型：function
     */
    private String type;

    /**
     * 函数调用
     */
    private Function function;

    /**
     * 函数调用详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        /**
         * 函数名称
         */
        private String name;

        /**
         * 参数（JSON字符串）
         */
        private String arguments;
    }
}
