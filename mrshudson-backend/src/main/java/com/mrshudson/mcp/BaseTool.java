package com.mrshudson.mcp;

import com.mrshudson.mcp.kimi.dto.Tool;

/**
 * MCP工具基类
 */
public interface BaseTool {

    /**
     * 获取工具定义
     *
     * @return 工具定义
     */
    Tool getToolDefinition();

    /**
     * 获取工具名称
     *
     * @return 工具名称
     */
    default String getName() {
        return getToolDefinition().getFunction().getName();
    }

    /**
     * 执行工具
     *
     * @param arguments 参数（JSON字符串）
     * @return 执行结果
     */
    String execute(String arguments);
}
