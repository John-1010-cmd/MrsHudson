package com.mrshudson.domain.dto;

import lombok.Data;

/**
 * 语音消息请求DTO
 */
@Data
public class VoiceMessageRequest {

    /**
     * 会话ID（可选，用于多轮对话）
     */
    private String sessionId;

    /**
     * 音频格式（wav、mp3等，可选，默认wav）
     */
    private String format = "wav";

    /**
     * 采样率（可选，默认16000）
     */
    private Integer sampleRate = 16000;
}
