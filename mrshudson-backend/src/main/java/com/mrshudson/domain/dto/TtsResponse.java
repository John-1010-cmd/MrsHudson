package com.mrshudson.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 语音合成响应DTO
 */
@Data
@Builder
public class TtsResponse {

    /**
     * 音频文件URL
     */
    private String audioUrl;

    /**
     * 合成的文本内容
     */
    private String text;

    /**
     * 音频时长（秒，预估）
     */
    private Integer duration;

    /**
     * 使用的语音合成引擎
     */
    private String engine;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;
}
