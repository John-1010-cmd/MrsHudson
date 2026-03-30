package com.mrshudson.service.tts;

import com.mrshudson.config.VoiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MiniMax TTS 提供商（占位符实现）
 * 后续集成 MiniMax 语音合成 API 时替换此实现
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "voice.tts-provider", havingValue = "minimax")
@RequiredArgsConstructor
public class MiniMaxTtsProvider implements TtsProvider {

    private final VoiceProperties voiceProperties;

    @Override
    public String synthesize(String text) {
        log.warn("MiniMax TTS 尚未实现，跳过语音合成。文本长度: {} 字符", text != null ? text.length() : 0);
        return null;
    }
}
