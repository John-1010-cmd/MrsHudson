package com.mrshudson.optim.compress;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对话压缩配置类
 * 用于配置对话历史压缩的触发阈值和保留策略
 */
@Data
@Component
public class CompressionConfig {

    /**
     * 触发压缩的消息数阈值
     * 当对话消息数超过此值时触发压缩
     */
    @Value("${optim.compression.trigger-threshold:10}")
    private int triggerThreshold = 10;

    /**
     * 保留的最近消息数
     * 压缩时保留最近的N条消息不压缩
     */
    @Value("${optim.compression.keep-recent-messages:4}")
    private int keepRecentMessages = 4;

    /**
     * 摘要最大长度（字符数）
     * 限制生成的摘要长度
     */
    @Value("${optim.compression.summary-max-length:100}")
    private int summaryMaxLength = 100;
}
