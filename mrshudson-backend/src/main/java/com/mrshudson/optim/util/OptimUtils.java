package com.mrshudson.optim.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 优化层工具类
 * 提供相似度计算、Token估算等通用功能
 */
@Slf4j
public final class OptimUtils {

    // 中文分词正则
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");
    // 英文单词正则
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z]+");

    private OptimUtils() {
        // 工具类禁止实例化
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vectorA 向量A
     * @param vectorB 向量B
     * @return 相似度 (-1 到 1)
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) {
            return -1.0;
        }
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("向量维度不匹配: " + vectorA.length + " vs " + vectorB.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 计算两个向量的余弦相似度（double数组版本）
     *
     * @param vectorA 向量A
     * @param vectorB 向量B
     * @return 相似度 (-1 到 1)
     */
    public static double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA == null || vectorB == null) {
            return -1.0;
        }
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("向量维度不匹配: " + vectorA.length + " vs " + vectorB.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 估算文本的token数量
     * 简化算法：中文约2字符=1token，英文约4字符=1token
     *
     * @param text 文本
     * @return 估算的token数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int englishCharCount = 0;

        Matcher chineseMatcher = CHINESE_PATTERN.matcher(text);
        while (chineseMatcher.find()) {
            chineseCount++;
        }

        Matcher englishMatcher = ENGLISH_WORD_PATTERN.matcher(text);
        while (englishMatcher.find()) {
            englishCharCount += englishMatcher.group().length();
        }

        // 中文：2字符 = 1 token
        int chineseTokens = chineseCount / 2;
        // 英文：4字符 = 1 token
        int englishTokens = englishCharCount / 4;
        // 其他字符（标点、数字等）：4字符 = 1 token
        int otherChars = text.length() - chineseCount - englishCharCount;
        int otherTokens = otherChars / 4;

        return chineseTokens + englishTokens + otherTokens + 1; // +1 作为缓冲
    }

    /**
     * 计算文本相似度（基于字符重叠的简化算法）
     * 适用于快速预筛选，不适用于精确语义相似度
     *
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度 (0-1)
     */
    public static double textOverlapSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        if (text1.isEmpty() && text2.isEmpty()) {
            return 1.0;
        }
        if (text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        // 统一转小写并提取字符集合
        String lower1 = text1.toLowerCase();
        String lower2 = text2.toLowerCase();

        // 提取2-gram
        java.util.Set<String> grams1 = extractNGrams(lower1, 2);
        java.util.Set<String> grams2 = extractNGrams(lower2, 2);

        // 计算Jaccard相似度
        java.util.Set<String> intersection = new java.util.HashSet<>(grams1);
        intersection.retainAll(grams2);

        java.util.Set<String> union = new java.util.HashSet<>(grams1);
        union.addAll(grams2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * 提取n-gram
     */
    private static java.util.Set<String> extractNGrams(String text, int n) {
        java.util.Set<String> grams = new java.util.HashSet<>();
        for (int i = 0; i <= text.length() - n; i++) {
            grams.add(text.substring(i, i + n));
        }
        return grams;
    }

    /**
     * 计算字符串的哈希值（用于缓存key）
     *
     * @param input 输入字符串
     * @return 哈希值
     */
    public static String hashString(String input) {
        if (input == null) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 安全获取字符串长度（处理null）
     */
    public static int safeLength(String str) {
        return str == null ? 0 : str.length();
    }

    /**
     * 截断文本到指定长度
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
