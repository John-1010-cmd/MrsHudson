package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.IntentFingerprint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 查询归一化器
 * 将相对时间转换为具体日期，生成指纹
 * 对齐 AI_ARCHITECTURE.md v3.5 规范
 */
@Slf4j
@Component
public class QueryNormalizer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 相对时间正则表达式
    private static final Pattern TODAY_PATTERN = Pattern.compile("今天|今日|这天", Pattern.CASE_INSENSITIVE);
    private static final Pattern YESTERDAY_PATTERN = Pattern.compile("昨天|昨日", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOMORROW_PATTERN = Pattern.compile("明天|明日", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY_AFTER_TOMORROW_PATTERN = Pattern.compile("后天", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOW_PATTERN = Pattern.compile("现在|目前|当前", Pattern.CASE_INSENSITIVE);
    private static final Pattern JUST_NOW_PATTERN = Pattern.compile("刚才|刚刚", Pattern.CASE_INSENSITIVE);

    // 口语化表达映射
    private static final Pattern CHECK_PATTERN = Pattern.compile("查一下|看看|查查看", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_PATTERN = Pattern.compile("帮我弄|给我建|帮我建", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROVIDE_PATTERN = Pattern.compile("给我|能给我", Pattern.CASE_INSENSITIVE);

    /**
     * 归一化用户输入并生成指纹
     *
     * @param rawInput 原始输入
     * @return 意图指纹
     */
    public IntentFingerprint normalize(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return null;
        }

        LocalDate today = LocalDate.now();
        String normalized = rawInput.trim();

        // 1. 时间归一化 (相对时间 -> 具体日期)
        normalized = normalizeTemporalExpressions(normalized, today);

        // 2. 口语化归一化 (可选)
        normalized = normalizeColloquialExpressions(normalized);

        // 3. 去除多余空格
        normalized = normalized.replaceAll("\\s+", "");

        // 4. 生成 MD5 指纹
        String fingerprintHash = generateMD5(normalized);

        return IntentFingerprint.builder()
                .rawInput(rawInput)
                .normalizedInput(normalized)
                .fingerprintHash(fingerprintHash)
                .concreteDate(today)
                .build();
    }

    /**
     * 归一化时间表达
     */
    private String normalizeTemporalExpressions(String input, LocalDate today) {
        String result = input;

        // 今天 -> yyyy-MM-dd
        result = TODAY_PATTERN.matcher(result).replaceAll(today.format(DATE_FORMATTER));

        // 昨天 -> yyyy-MM-dd
        result = YESTERDAY_PATTERN.matcher(result).replaceAll(
                today.minusDays(1).format(DATE_FORMATTER));

        // 明天 -> yyyy-MM-dd
        result = TOMORROW_PATTERN.matcher(result).replaceAll(
                today.plusDays(1).format(DATE_FORMATTER));

        // 后天 -> yyyy-MM-dd
        result = DAY_AFTER_TOMORROW_PATTERN.matcher(result).replaceAll(
                today.plusDays(2).format(DATE_FORMATTER));

        // 现在/目前 -> yyyy-MM-dd
        result = NOW_PATTERN.matcher(result).replaceAll(today.format(DATE_FORMATTER));

        // 刚才/刚刚 -> yyyy-MM-dd
        result = JUST_NOW_PATTERN.matcher(result).replaceAll(today.format(DATE_FORMATTER));

        return result;
    }

    /**
     * 归一化口语化表达
     */
    private String normalizeColloquialExpressions(String input) {
        String result = input;

        // 查一下/看看 -> 查询
        result = CHECK_PATTERN.matcher(result).replaceAll("查询");

        // 帮我弄/给我建 -> 创建
        result = CREATE_PATTERN.matcher(result).replaceAll("创建");

        // 给我/能给我 -> 提供
        result = PROVIDE_PATTERN.matcher(result).replaceAll("提供");

        return result;
    }

    /**
     * 生成 MD5 哈希
     */
    public String generateMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return String.format("%032x", new BigInteger(1, hash));
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
            // Fallback to simple hash
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 生成指纹哈希 (兼容旧接口)
     */
    public String generateFingerprint(String normalizedInput) {
        return generateMD5(normalizedInput);
    }
}
