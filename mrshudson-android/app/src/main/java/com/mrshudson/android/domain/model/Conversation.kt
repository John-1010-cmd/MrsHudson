package com.mrshudson.android.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 会话领域模型
 *
 * @property id 会话ID
 * @property title 会话标题
 * @property provider AI提供商
 * @property lastMessageAt 最后消息时间
 * @property createdAt 创建时间
 */
data class Conversation(
    val id: String,
    val title: String,
    val provider: String? = null,
    val lastMessageAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null
) {
    /**
     * 格式化显示最后消息时间
     */
    fun formattedLastMessageTime(): String {
        return lastMessageAt?.let {
            val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            it.format(formatter)
        } ?: ""
    }

    /**
     * 获取显示标题
     */
    fun displayTitle(): String {
        return title.ifEmpty { "新对话" }
    }
}

/**
 * 创建会话的辅助函数
 *
 * @param id 会话ID
 * @param title 会话标题
 * @param provider AI提供商
 * @param lastMessageAt 最后消息时间字符串
 * @param createdAt 创建时间字符串
 */
fun createConversation(
    id: String,
    title: String,
    provider: String? = null,
    lastMessageAt: String? = null,
    createdAt: String? = null
): Conversation {
    fun parseDateTime(dateStr: String?): LocalDateTime? {
        if (dateStr == null) return null
        return try {
            LocalDateTime.parse(dateStr.replace("Z", "").substringBefore("."))
        } catch (e: Exception) {
            null
        }
    }

    return Conversation(
        id = id,
        title = title,
        provider = provider,
        lastMessageAt = parseDateTime(lastMessageAt),
        createdAt = parseDateTime(createdAt)
    )
}
