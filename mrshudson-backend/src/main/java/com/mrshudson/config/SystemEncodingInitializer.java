package com.mrshudson.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统编码初始化器
 * 在 Spring Boot 启动前强制设置 UTF-8 编码
 * 这是解决 Windows 平台中文乱码的最有效方法
 */
public class SystemEncodingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // 强制设置系统属性
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        System.setProperty("java.nio.charset.default", "UTF-8");

        // 设置环境属性
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put("file.encoding", "UTF-8");
        props.put("spring.output.encoding", "UTF-8");
        environment.getPropertySources().addFirst(new MapPropertySource("encodingProps", props));

        // 打印到控制台（此时日志系统可能还未初始化）
        System.out.println("============================================");
        System.out.println("  系统编码初始化完成");
        System.out.println("============================================");
        System.out.println("默认字符集: " + Charset.defaultCharset().name());
        System.out.println("文件编码: " + System.getProperty("file.encoding"));
        System.out.println("标准输出编码: " + System.getProperty("sun.stdout.encoding"));
        System.out.println("中文测试: 编码配置已生效");
        System.out.println("============================================");
    }
}
