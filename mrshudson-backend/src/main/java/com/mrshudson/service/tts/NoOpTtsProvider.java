package com.mrshudson.service.tts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 无操作 TTS 提供商
 * 当 voice.tts-provider=noop 时使用，直接返回 null
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "voice.tts-provider", havingValue = "noop")
public class NoOpTtsProvider implements TtsProvider {

    @Override
    public String synthesize(String text) {
        log.debug("NoOp TTS Provider：跳过语音合成");
        return null;
    }
}
