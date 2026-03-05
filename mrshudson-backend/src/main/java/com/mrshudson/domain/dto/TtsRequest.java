package com.mrshudson.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 语音合成请求DTO
 */
@Data
public class TtsRequest {

    /**
     * 要合成的文本内容
     */
    @NotBlank(message = "文本内容不能为空")
    @Size(max = 1000, message = "文本长度不能超过1000字符")
    private String text;

    /**
     * 发音人（可选，默认使用配置值）
     * x4_xiaoyan(讯飞小燕女声)、x4_yezi(讯飞小露女声)、aisjiuxu(讯飞许久男声)
     */
    private String voice;

    /**
     * 语速（0-100，默认50）
     */
    private Integer speed;

    /**
     * 音量（0-100，默认50）
     */
    private Integer volume;

    /**
     * 音调（0-100，默认50）
     */
    private Integer pitch;
}
