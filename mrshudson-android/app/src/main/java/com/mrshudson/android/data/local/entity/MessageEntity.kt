package com.mrshudson.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 消息实体类
 * 用于 Room 数据库存储聊天消息
 * 缓存最近100条对话记录
 *
 * @property id 消息ID
 * @property conversationId 会话ID
 * @property role 消息角色：user, assistant, system
 * @property content 消息内容
 * @property createdAt 创建时间
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: Long,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: String
)
