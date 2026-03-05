package com.mrshudson.optim.cache.impl;

import com.mrshudson.optim.cache.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键词嵌入服务（简化版）
 * 基于预定义关键词的one-hot编码实现文本向量化
 * 纯Java实现，无外部依赖
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "optim.semantic-cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KeywordEmbeddingService implements EmbeddingService {

    /**
     * 向量维度（预定义关键词数量）
     */
    private static final int DIMENSION = 100;

    /**
     * 预定义关键词列表
     * 按类别组织：天气、日历、待办、问候、通用等
     */
    private static final List<String> KEYWORDS = Arrays.asList(
        // 天气相关 (0-19)
        "天气", "温度", "下雨", "晴天", "阴天", "多云", "雪", "风", "湿度", "预报",
        "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安", "南京", "重庆",

        // 日历相关 (20-39)
        "日历", "日程", "会议", "约会", "提醒", "今天", "明天", "后天", "昨天", "下周",
        "上午", "下午", "晚上", "早上", "中午", "几点", "时间", "日期", "星期", "月份",

        // 待办相关 (40-59)
        "待办", "任务", "todo", "完成", "未完成", "待处理", "紧急", "重要", "优先级",
        "清单", "列表", "事项", "工作", "作业", "项目", "计划", "安排", "截止", "期限", "延期",

        // 问候闲聊 (60-74)
        "你好", "您好", "hello", "hi", "在吗", "在不在", "早上好", "下午好", "晚上好",
        "再见", "拜拜", "谢谢", "感谢", "不客气", "没关系",

        // 通用查询 (75-89)
        "查询", "查看", "显示", "列出", "搜索", "找", "是什么", "怎么样", "如何", "怎么",
        "为什么", "多少", "哪里", "谁", "什么",

        // 操作动词 (90-99)
        "添加", "删除", "修改", "更新", "创建", "新建", "取消", "确认", "保存", "提交"
    );

    /**
     * 关键词到索引的映射
     */
    private final Map<String, Integer> keywordIndexMap;

    public KeywordEmbeddingService() {
        // 构建关键词索引映射
        this.keywordIndexMap = new HashMap<>();
        for (int i = 0; i < KEYWORDS.size(); i++) {
            String keyword = KEYWORDS.get(i);
            keywordIndexMap.put(keyword, i);
            // 同时存储小写版本（用于英文关键词匹配）
            keywordIndexMap.put(keyword.toLowerCase(), i);
        }
        log.info("KeywordEmbeddingService initialized with {} keywords", KEYWORDS.size());
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[DIMENSION];
        }

        // 初始化零向量
        float[] embedding = new float[DIMENSION];

        // 分词并提取关键词
        Set<String> tokens = tokenize(text);

        // 生成one-hot向量
        for (String token : tokens) {
            Integer index = keywordIndexMap.get(token);
            if (index != null && index < DIMENSION) {
                embedding[index] = 1.0f;
            }
        }

        // 归一化向量（L2归一化）
        normalize(embedding);

        return embedding;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        return texts.stream()
                .map(this::embed)
                .collect(Collectors.toList());
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public String getName() {
        return "KeywordEmbeddingService";
    }

    /**
     * 对文本进行分词
     * 支持中文和英文简单分词
     *
     * @param text 输入文本
     * @return 分词结果集合
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();

        // 清理文本
        String cleaned = text.toLowerCase()
                .replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ")
                .trim();

        if (cleaned.isEmpty()) {
            return tokens;
        }

        // 中文：按字符和滑动窗口提取2-3字词
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);

            // 单字
            tokens.add(String.valueOf(c));

            // 2字词
            if (i + 1 < cleaned.length()) {
                String twoChars = cleaned.substring(i, i + 2);
                tokens.add(twoChars);

                // 3字词
                if (i + 2 < cleaned.length()) {
                    String threeChars = cleaned.substring(i, i + 3);
                    tokens.add(threeChars);
                }
            }
        }

        // 英文：按空格分词
        String[] words = cleaned.split("\\s+");
        for (String word : words) {
            if (!word.isEmpty()) {
                tokens.add(word);
            }
        }

        return tokens;
    }

    /**
     * L2归一化向量
     *
     * @param vector 待归一化的向量
     */
    private void normalize(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }

        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
    }

    /**
     * 获取预定义关键词列表（用于调试）
     *
     * @return 关键词列表
     */
    public List<String> getKeywords() {
        return Collections.unmodifiableList(KEYWORDS);
    }

    /**
     * 检查文本中包含的关键词
     *
     * @param text 输入文本
     * @return 匹配的关键词列表
     */
    public List<String> extractMatchedKeywords(String text) {
        Set<String> tokens = tokenize(text);
        return tokens.stream()
                .filter(keywordIndexMap::containsKey)
                .distinct()
                .collect(Collectors.toList());
    }
}
