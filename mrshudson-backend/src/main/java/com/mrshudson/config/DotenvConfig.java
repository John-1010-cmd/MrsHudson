package com.mrshudson.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量配置加载器
 * 在应用启动时自动加载 .env 文件中的配置到系统环境变量
 *
 * 配置优先级：系统环境变量 > .env文件 > 代码默认值
 */
@Slf4j
@Configuration
public class DotenvConfig {

    /**
     * 敏感关键词，用于隐藏敏感配置值
     */
    private static final String[] SENSITIVE_KEYWORDS = {"secret", "password", "key", "token", "api_key"};

    /**
     * .env 文件路径（从项目根目录查找）
     */
    private static final String ENV_FILE_NAME = ".env";

    /**
     * 解析 .env 文件中的键值对
     */
    private static final Pattern ENV_PATTERN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");

    @PostConstruct
    public void loadDotenv() {
        try {
            // 尝试从多个位置查找 .env 文件
            Path envPath = findEnvFile();

            if (envPath == null || !Files.exists(envPath)) {
                log.info("未找到 .env 配置文件，将使用系统环境变量或默认值");
                return;
            }

            log.info("发现 .env 配置文件: {}", envPath);

            // 解析 .env 文件
            Map<String, String> envMap = parseEnvFile(envPath);

            // 将配置加载到系统属性
            int loadedCount = 0;
            for (Map.Entry<String, String> entry : envMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // 只有当系统属性中没有设置时才设置
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                    loadedCount++;
                    log.debug("从 .env 加载配置: {} = {}", key, maskSensitive(key, value));
                }
            }

            log.info(".env 配置文件已加载，共 {} 个配置项", loadedCount);

        } catch (Exception e) {
            log.warn("加载 .env 配置文件失败: {}", e.getMessage());
            log.info("将使用系统环境变量或代码默认值");
        }
    }

    /**
     * 查找 .env 文件
     * 查找顺序：当前目录 -> 项目根目录 -> 用户主目录
     */
    private Path findEnvFile() {
        // 尝试的路径列表
        String[] searchPaths = {
            ".env",                           // 当前工作目录
            "../.env",                        // 项目根目录（backend 上级）
            System.getProperty("user.dir") + "/.env",
            System.getProperty("user.home") + "/.env"
        };

        for (String pathStr : searchPaths) {
            Path path = Paths.get(pathStr).toAbsolutePath();
            if (Files.exists(path)) {
                return path;
            }
        }

        return null;
    }

    /**
     * 解析 .env 文件内容
     */
    private Map<String, String> parseEnvFile(Path envPath) throws IOException {
        Map<String, String> result = new HashMap<>();

        for (String line : Files.readAllLines(envPath)) {
            // 跳过空行和注释行
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            // 解析键值对
            Matcher matcher = ENV_PATTERN.matcher(line);
            if (matcher.matches()) {
                String key = matcher.group(1).trim();
                String value = parseValue(matcher.group(2).trim());
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * 解析值，处理引号
     */
    private String parseValue(String value) {
        if (value == null) {
            return "";
        }

        // 移除首尾引号（单引号或双引号）
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        // 处理转义字符
        value = value.replace("\\\"", "\"")
                     .replace("\\'", "'")
                     .replace("\\\\", "\\");

        return value;
    }

    /**
     * 隐藏敏感配置值
     */
    private String maskSensitive(String key, String value) {
        String lowerKey = key.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerKey.contains(keyword)) {
                if (value != null && value.length() > 8) {
                    return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
                }
                return "****";
            }
        }
        return value;
    }
}
