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
     * 讯飞语音合成URL
     */
    private String xfyunTtsUrl = "https://tts-api.xfyun.cn/v2/tts";

    /**
     * 讯飞语音合成发音人
     */
    private String xfyunTtsVoice = "xiaoyan";

    /**
     * 是否启用语音合成（TTS），开发环境默认关闭
     */
    private boolean enableTts = false;

    /**
     * TTS 提供商选择：xfyun / minimax / noop
     * 遵循规范第八节策略模式
     */
    private String ttsProvider = "xfyun";

    /**
     * MiniMax TTS API Key（当 tts-provider=minimax 时使用）
     */
    private String minimaxApiKey;

    /**
     * MiniMax TTS 发音人（当 tts-provider=minimax 时使用）
     * 可选值: English_expressive_narrator, female-tianmei 等
     */
    private String minimaxTtsVoice = "English_expressive_narrator";

    /**
     * MiniMax TTS 模型（当 tts-provider=minimax 时使用）
     * 可选值: speech-2.8-hd, speech-2.8-turbo, speech-2.6-hd, speech-2.6-turbo
     */
    private String minimaxTtsModel = "speech-2.6-turbo";

    /**
     * MiniMax TTS 语音速度（0.5-2.0）
     */
    private double minimaxTtsSpeed = 1.0;

    /**
     * MiniMax TTS 轮询最大次数
     */
    private int minimaxTtsMaxPolls = 30;

    /**
     * MiniMax TTS 轮询间隔（毫秒）
     */
    private int minimaxTtsPollInterval = 1000;

    /**
     * 是否启用模拟模式（不调用真实API，返回模拟数据）
     */
    private boolean mockMode = true;

    /**
     * 模拟识别返回的文本（用于测试）
     */
    private String mockText = "你好，请帮我查询一下今天的天气";

    /**
     * TTS音频文件存储路径
     */
    private String ttsStoragePath = "uploads/tts/";

    /**
     * 音频文件访问基础URL（用于生成完整URL）
     */
    private String ttsBaseUrl = "http://localhost:8080";

    /**
     * 是否上传到 GitHub
     */
    private boolean uploadToGithub = false;
}
