package com.mrshudson.optim.intent;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 意图类型枚举
 * 定义系统支持的各种用户意图
 */
@Getter
public enum IntentType {

    /**
     * 天气查询
     * 例："北京天气怎么样"、"今天会下雨吗"
     */
    WEATHER_QUERY("weather_query", "天气查询",
            new HashSet<>(Arrays.asList("天气", "温度", "下雨", "雪", "风", "晴", "阴", "多云")),
            0.8),

    /**
     * 日历查询
     * 例："我今天有什么安排"、"下周的会议"
     */
    CALENDAR_QUERY("calendar_query", "日历查询",
            new HashSet<>(Arrays.asList("日程", "会议", "安排", "日历", "今天", "明天", "下周", "这周", "有什么")),
            0.7),

    /**
     * 待办查询
     * 例："我的待办事项"、"有什么任务"
     */
    TODO_QUERY("todo_query", "待办查询",
            new HashSet<>(Arrays.asList("待办", "任务", "todo", "提醒", "未完成", "要做")),
            0.7),

    /**
     * 路线规划
     * 例："怎么去天安门"、"从A到B怎么走"
     */
    ROUTE_QUERY("route_query", "路线规划",
            new HashSet<>(Arrays.asList("路线", "怎么去", "怎么走", "导航", "地图", "从", "到")),
            0.75),

    /**
     * 简单问候/闲聊
     * 例："你好"、"在吗"
     */
    SMALL_TALK("small_talk", "闲聊问候",
            new HashSet<>(Arrays.asList("你好", "您好", "在吗", "hello", "hi", "hey", "早上好", "下午好", "晚上好")),
            0.9),

    /**
     * 创建日历事件
     * 例："帮我创建一个会议"
     */
    CALENDAR_CREATE("calendar_create", "创建日程",
            new HashSet<>(Arrays.asList("创建", "添加", "新建", "安排", "会议", "日程")),
            0.65),

    /**
     * 创建待办
     * 例："添加一个待办"
     */
    TODO_CREATE("todo_create", "创建待办",
            new HashSet<>(Arrays.asList("创建", "添加", "新建", "待办", "任务", "提醒")),
            0.65),

    /**
     * 通用对话
     * 无法归类到具体意图的一般对话
     */
    GENERAL_CHAT("general_chat", "通用对话",
            Collections.emptySet(),
            0.5),

    /**
     * 未知意图
     * 无法识别的意图
     */
    UNKNOWN("unknown", "未知意图",
            Collections.emptySet(),
            0.0);

    /**
     * 意图编码
     */
    private final String code;

    /**
     * 意图描述
     */
    private final String description;

    /**
     * 关键词集合（用于规则匹配）
     */
    private final Set<String> keywords;

    /**
     * 基础置信度阈值
     */
    private final double baseConfidenceThreshold;

    IntentType(String code, String description, Set<String> keywords, double baseConfidenceThreshold) {
        this.code = code;
        this.description = description;
        this.keywords = keywords;
        this.baseConfidenceThreshold = baseConfidenceThreshold;
    }

    /**
     * 根据编码获取意图类型
     */
    public static IntentType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        for (IntentType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * 判断是否是工具查询类意图
     */
    public boolean isToolQuery() {
        return this == WEATHER_QUERY || this == CALENDAR_QUERY || this == TODO_QUERY || this == ROUTE_QUERY;
    }

    /**
     * 判断是否是创建类意图
     */
    public boolean isCreateOperation() {
        return this == CALENDAR_CREATE || this == TODO_CREATE;
    }

    /**
     * 判断是否是闲聊类意图
     */
    public boolean isSmallTalk() {
        return this == SMALL_TALK;
    }

    /**
     * 是否需要AI生成回复
     */
    public boolean needsAiResponse() {
        return this == GENERAL_CHAT || this == UNKNOWN;
    }
}
