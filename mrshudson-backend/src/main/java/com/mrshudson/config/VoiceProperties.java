package com.mrshudson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音识别API配置属性（讯飞语音）
 */
@Data
@Component
@ConfigurationProperties(prefix = "voice")
public class VoiceProperties {

    /**
     * 讯飞App ID
     */
    private String xfyunAppId;

    /**
     * 讯飞API Secret
     */
    private String xfyunApiSecret;

    /**
     * 讯飞API Key
     */
    private String xfyunApiKey;

    /**
     * 讯飞语音识别URL
     */
    private String xfyunAsrUrl = "http://iat-api.xfyun.cn/v2/iat";

    /**
     * 是否启用模拟模式（不调用真实API，返回模拟数据）
     */
    private boolean mockMode = true;

    /**
     * 模拟识别返回的文本（用于测试）
     */
    private String mockText = "你好，请帮我查询一下今天的天气";
}
