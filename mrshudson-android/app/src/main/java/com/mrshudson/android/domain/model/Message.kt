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
 * 消息领域模型
 *
 * @property id 消息ID
 * @property role 消息角色
 * @property content 消息内容
 * @property createdAt 创建时间
 */
data class Message(
    val id: Long,
    val role: MessageRole,
    val content: String,
    val createdAt: LocalDateTime
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
}

/**
 * 创建新消息的辅助函数
 *
 * @param id 消息ID
 * @param role 消息角色字符串
 * @param content 消息内容
 * @param createdAt 创建时间字符串
 */
fun createMessage(
    id: Long,
    role: String,
    content: String,
    createdAt: String
): Message {
    val messageRole = MessageRole.fromString(role)
    val dateTime = try {
        LocalDateTime.parse(createdAt.replace("Z", "").substringBefore("."))
    } catch (e: Exception) {
        LocalDateTime.now()
    }

    return Message(
        id = id,
        role = messageRole,
        content = content,
        createdAt = dateTime
    )
}
