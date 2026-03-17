package com.mrshudson.android.data.remote.dto

/**
 * 语音合成请求
 *
 * @property text 要转换为语音的文本
 * @property voice 发音人（可选）
 * @property speed 语速（0-100，默认50）
 * @property volume 音量（0-100，默认50）
 * @property pitch 音调（0-100，默认50）
 */
data class TtsRequest(
    val text: String,
    val voice: String? = null,
    val speed: Int? = null,
    val volume: Int? = null,
    val pitch: Int? = null
)

/**
 * 语音合成响应
 *
 * @property audioUrl 生成的音频URL
 * @property text 原始文本
 * @property duration 音频时长（秒）
 * @property engine 使用的语音引擎
 * @property success 是否成功
 * @property errorMessage 错误信息（失败时）
 */
data class TtsResponse(
    val audioUrl: String? = null,
    val text: String,
    val duration: Int? = null,
    val engine: String? = null,
    val success: Boolean = true,
    val errorMessage: String? = null
)
