package com.mrshudson;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.mrshudson.mapper")
public class MrshudsonApplication {

    public static void main(String[] args) {
        // 在应用启动前强制设置 UTF-8 编码（解决 Windows 平台中文乱码）
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        System.setProperty("java.nio.charset.default", "UTF-8");
        
        // 打印启动信息
        System.out.println("============================================");
        System.out.println("  MrsHudson 应用启动");
        System.out.println("============================================");
        System.out.println("默认字符集: " + java.nio.charset.Charset.defaultCharset().name());
        System.out.println("文件编码: " + System.getProperty("file.encoding"));
        System.out.println("中文测试: 应用启动中...");
        System.out.println("============================================");
        
        SpringApplication.run(MrshudsonApplication.class, args);
    }
}
