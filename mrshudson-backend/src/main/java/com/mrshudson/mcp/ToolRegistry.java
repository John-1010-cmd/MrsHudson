package com.mrshudson.mcp;

import com.alibaba.fastjson2.JSON;
import com.mrshudson.mcp.kimi.dto.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * MCP工具注册中心
 */
@Slf4j
@Component
public class ToolRegistry {

    /**
     * 工具定义Map
     */
    private final Map<String, Tool> toolDefinitions = new HashMap<>();

    /**
     * 工具执行器Map
     */
    private final Map<String, Function<String, String>> toolExecutors = new HashMap<>();

    /**
     * 注册工具
     *
     * @param tool     工具定义
     * @param executor 工具执行器
     */
    public void registerTool(Tool tool, Function<String, String> executor) {
        String toolName = tool.getFunction().getName();
        toolDefinitions.put(toolName, tool);
        toolExecutors.put(toolName, executor);
        log.info("注册MCP工具: {}", toolName);
    }

    /**
     * 获取所有工具定义
     *
     * @return 工具定义列表
     */
    public List<Tool> getToolDefinitions() {
        return new ArrayList<>(toolDefinitions.values());
    }

    /**
     * 执行工具
     *
     * @param toolName  工具名称
     * @param arguments 参数（JSON字符串）
     * @return 执行结果
     */
    public String executeTool(String toolName, String arguments) {
        Function<String, String> executor = toolExecutors.get(toolName);
        if (executor == null) {
            throw new RuntimeException("未找到工具: " + toolName);
        }

        log.info("执行工具: {}, 参数: {}", toolName, arguments);

        try {
            String result = executor.apply(arguments);
            log.info("工具执行成功: {}", toolName);
            return result;
        } catch (Exception e) {
            log.error("工具执行失败: toolName={}, arguments={}", toolName, arguments, e);
            return "执行失败: " + e.getMessage();
        }
    }

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    public boolean hasTool(String toolName) {
        return toolDefinitions.containsKey(toolName);
    }

    /**
     * 获取所有工具的描述列表
     *
     * @return 工具描述列表
     */
    public List<String> getToolDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (Tool tool : toolDefinitions.values()) {
            String name = tool.getFunction().getName();
            String desc = tool.getFunction().getDescription();
            descriptions.add(name + ": " + desc);
        }
        return descriptions;
    }
}
