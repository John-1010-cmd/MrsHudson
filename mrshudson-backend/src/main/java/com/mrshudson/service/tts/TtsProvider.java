package com.mrshudson.service.tts;

/**
 * TTS 语音合成策略接口
 * 遵循 SSE_TTS_UNIFIED_SPEC 规范第八节：策略模式切换 TTS 提供商
 */
public interface TtsProvider {

    /**
     * 合成语音
     *
     * @param text 要合成的文本
     * @return 音频文件 URL；无音频时返回 null（配置缺失、mock 模式等预期场景）
     * @throws RuntimeException 合成过程中发生异常（SDK 错误、网络异常等）
     */
    String synthesize(String text);
}
