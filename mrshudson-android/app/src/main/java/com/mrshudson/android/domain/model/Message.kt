package com.mrshudson.android.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 消息角色枚举
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM;

    companion object {
        fun fromString(value: String): MessageRole {
            return when (value.lowercase()) {
                "user" -> USER
                "assistant" -> ASSISTANT
                "system" -> SYSTEM
                else -> USER
            }
        }
    }
}

/**
 * 消息播放状态
 */
enum class AudioPlayState {
    IDLE,       // 未播放
    PLAYING,    // 正在播放
    PAUSED,     // 已暂停
    LOADING     // 加载中
}

/**
 * TTS 合成状态
 */
enum class TtsStatus {
    PENDING,        // AI 还在生成
    SYNTHESIZING,   // content_done 已收到，等待 audio_done
    READY,          // audio_done 收到，有 URL，可播放
    TIMEOUT,        // 超时，后台继续合成，audioUrl 置空
    ERROR,          // 合成异常，audioUrl 置空
    NOAUDIO,        // 提供商返回空，audioUrl 置空
    NO_AUDIO        // 历史消息专用：audioUrl 为 null，不显示按钮
}

/**
 * 消息领域模型
 *
 * @property id 消息ID
 * @property role 消息角色
 * @property content 消息内容
 * @property createdAt 创建时间
 * @property audioUrl 语音合成音频URL
 * @property ttsStatus TTS 合成状态
 * @property audioPlayState 音频播放状态
 * @property pausedPosition 暂停位置（毫秒）
 */
data class Message(
    val id: Long,
    val role: MessageRole,
    val content: String,
    val createdAt: LocalDateTime,
    val audioUrl: String? = null,
    val ttsStatus: TtsStatus = TtsStatus.NO_AUDIO,
    val audioPlayState: AudioPlayState = AudioPlayState.IDLE,
    val pausedPosition: Long = 0L
) {
    /**
     * 格式化显示时间
     */
    fun formattedTime(): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return createdAt.format(formatter)
    }

    /**
     * 判断是否为用户消息
     */
    fun isUserMessage(): Boolean = role == MessageRole.USER

    /**
     * 判断是否为AI消息
     */
    fun isAssistantMessage(): Boolean = role == MessageRole.ASSISTANT

    /**
     * 判断是否有音频
     */
    fun hasAudio(): Boolean = !audioUrl.isNullOrBlank()

    /**
     * 复制新的播放状态（不可变更新）
     */
    fun withAudioPlayState(state: AudioPlayState, position: Long = 0L): Message {
        return copy(audioPlayState = state, pausedPosition = position)
    }
}

/**
 * 创建新消息的辅助函数
 *
 * @param id 消息ID
 * @param role 消息角色字符串
 * @param content 消息内容
 * @param createdAt 创建时间字符串
 * @param audioUrl 音频URL（可选）
 */
fun createMessage(
    id: Long,
    role: String,
    content: String,
    createdAt: String,
    audioUrl: String? = null
): Message {
    val messageRole = MessageRole.fromString(role)
    val dateTime = try {
        // 清理时间字符串，支持多种格式：2026-03-15T19:27:38、2026-03-15T19:27:38.123、2026-03-15T19:27:38Z
        val cleaned = createdAt
            .replace("Z", "")
            .substringBefore(".") // 去掉毫秒
            .trim()
        if (cleaned.isNotEmpty()) {
            LocalDateTime.parse(cleaned)
        } else {
            LocalDateTime.now()
        }
    } catch (e: Exception) {
        try {
            // 尝试直接解析（可能包含毫秒）
            LocalDateTime.parse(createdAt.replace("Z", ""))
        } catch (e2: Exception) {
            LocalDateTime.now()
        }
    }

    return Message(
        id = id,
        role = messageRole,
        content = content,
        createdAt = dateTime,
        audioUrl = audioUrl
    )
}
