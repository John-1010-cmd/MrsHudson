package com.mrshudson.optim.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 意图澄清服务
 * 当意图模糊时，向用户请求澄清
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClarificationService {

    /**
     * 构建澄清问题
     *
     * @param message 原始消息
     * @param intentResult 意图识别结果
     * @return 澄清问题（null表示不需要澄清）
     */
    public String buildClarification(String message, IntentResult intentResult) {
        if (intentResult == null || !intentResult.needsClarification()) {
            return null;
        }

        // 1. 多个候选意图时
        if (intentResult.isAmbiguous()) {
            return buildMultipleChoiceClarification(message, intentResult);
        }

        // 2. 置信度低但有主要意图
        if (intentResult.getConfidence() < 0.8 && intentResult.getType() != IntentType.UNKNOWN) {
            return buildLowConfidenceClarification(message, intentResult);
        }

        // 3. 缺少必要参数
        if (hasMissingParameters(intentResult)) {
            return buildParameterClarification(message, intentResult);
        }

        return null;
    }

    /**
     * 构建多选澄清问题
     */
    private String buildMultipleChoiceClarification(String message, IntentResult intentResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("我不太确定您的意思，请问您是想：\n");

        List<IntentType> candidates = intentResult.getCandidates();
        if (candidates != null) {
            for (int i = 0; i < candidates.size(); i++) {
                sb.append(i + 1).append(". ").append(candidates.get(i).getDescription()).append("\n");
            }
        }

        // 添加一个通用的"其他"选项
        sb.append(candidates != null ? candidates.size() + 1 : 1).append(". 其他（请说明）");

        return sb.toString();
    }

    /**
     * 构建低置信度澄清问题
     */
    private String buildLowConfidenceClarification(String message, IntentResult intentResult) {
        IntentType type = intentResult.getType();
        String intentDesc = type != null ? type.getDescription() : "这个";

        return String.format(
            "您是想%s吗？如果不是，请告诉我您具体想做什么？",
            intentDesc
        );
    }

    /**
     * 构建参数缺失澄清问题
     */
    private String buildParameterClarification(String message, IntentResult intentResult) {
        IntentType type = intentResult.getType();

        if (type == IntentType.WEATHER_QUERY) {
            return "请问您想查询哪个城市的天气？";
        }

        if (type == IntentType.ROUTE_QUERY) {
            return "请问您想从哪里出发，要去哪里呢？";
        }

        if (type == IntentType.CALENDAR_QUERY) {
            return "请问您想查询什么时间的日程？";
        }

        // 默认澄清
        return "请提供更多信息，以便我更好地帮助您。";
    }

    /**
     * 检查是否缺少必要参数
     */
    private boolean hasMissingParameters(IntentResult intentResult) {
        IntentType type = intentResult.getType();
        if (type == null || !type.isToolQuery()) {
            return false;
        }

        java.util.Map<String, Object> params = intentResult.getExtractedParams();
        if (params == null || params.isEmpty()) {
            return true;
        }

        // 检查各类型必需参数
        return switch (type) {
            case WEATHER_QUERY -> !params.containsKey("city");
            case ROUTE_QUERY -> !params.containsKey("from") || !params.containsKey("to");
            case CALENDAR_QUERY -> !params.containsKey("date");
            default -> false;
        };
    }

    /**
     * 生成候选意图描述
     */
    public String getIntentCandidatesDescription(List<IntentType> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            IntentType type = candidates.get(i);
            sb.append(i + 1).append(". ").append(type.getDescription());
            if (type.getDescription() != null) {
                sb.append("（").append(type.getDescription()).append("）");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
