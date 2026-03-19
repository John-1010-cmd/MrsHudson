package com.mrshudson.optim.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 兜底回答提示词构建器
 * 构建用于兜底回答的系统提示词
 */
@Slf4j
@Component
public class FallbackPromptBuilder {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。

        【重要提示】
        1. 如果你知道答案，请直接回答用户的问题
        2. 如果不确定信息，请明确告知用户你无法确定
        3. 回答要友好、简洁、有帮助
        4. 适当使用表情符号让对话更生动

        【可用的工具参考】（如果有帮助可以使用，否则直接回答）：
        %s

        【当前日期】
        %s
        """;

    private static final String NO_TOOLS_MESSAGE = "（无可用工具，直接使用AI能力回答）";

    /**
     * 构建兜底回答的提示词
     *
     * @param userMessage 用户消息
     * @param availableTools 可用工具描述列表
     * @param currentDate 当前日期
     * @return 构建好的提示词
     */
    public String buildPrompt(String userMessage, List<String> availableTools, String currentDate) {
        String toolsDescription = formatToolsDescription(availableTools);

        String prompt = String.format(
            SYSTEM_PROMPT_TEMPLATE,
            toolsDescription,
            currentDate
        );

        log.debug("构建兜底提示词，长度: {}", prompt.length());
        return prompt;
    }

    /**
     * 格式化工具描述
     */
    private String formatToolsDescription(List<String> availableTools) {
        if (availableTools == null || availableTools.isEmpty()) {
            return NO_TOOLS_MESSAGE;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < availableTools.size(); i++) {
            sb.append("- ").append(availableTools.get(i)).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建兜底回答的系统提示词（无工具）
     */
    public String buildPromptWithoutTools(String currentDate) {
        return String.format(
            SYSTEM_PROMPT_TEMPLATE,
            NO_TOOLS_MESSAGE,
            currentDate
        );
    }

    /**
     * 获取兜底提示标识
     * 用于在响应中提示用户这是兜底回答
     */
    public String getFallbackIndicator() {
        return "💡 以下回答基于 AI 自身能力，可能不包含最新信息，仅供参考";
    }
}
