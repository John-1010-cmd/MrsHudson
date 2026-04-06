package com.mrshudson.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 编码配置类
 * 确保应用使用 UTF-8 编码，解决 Windows 平台中文乱码问题
 */
@Slf4j
@Configuration
public class EncodingConfig {

    @PostConstruct
    public void init() {
        // 设置系统属性
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");

        // 设置默认字符集
        System.setProperty("java.nio.charset.default", "UTF-8");

        // 验证编码设置
        Charset defaultCharset = Charset.defaultCharset();
        log.info("============================================");
        log.info("  编码配置初始化完成");
        log.info("============================================");
        log.info("默认字符集: {}", defaultCharset.name());
        log.info("文件编码: {}", System.getProperty("file.encoding"));
        log.info("标准输出编码: {}", System.getProperty("sun.stdout.encoding"));
        log.info("标准错误编码: {}", System.getProperty("sun.stderr.encoding"));
        log.info("============================================");

        // 测试中文输出
        log.info("中文测试: 编码配置已生效");
    }
}
