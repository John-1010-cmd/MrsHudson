package com.mrshudson.mcp.kimi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具定义DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tool {

    /**
     * 工具类型：function
     */
    private String type;

    /**
     * 函数定义
     */
    private Function function;

    /**
     * 函数定义
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
         * 函数描述
         */
        private String description;

        /**
         * 参数定义（JSON Schema）
         */
        private Object parameters;
    }
}
