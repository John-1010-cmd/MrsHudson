package com.mrshudson.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrshudson.android.data.local.entity.MessageEntity

/**
 * 消息数据访问对象
 * 提供消息数据的数据库操作
 */
@Dao
interface MessageDao {

    /**
     * 插入消息
     * 如果存在冲突则替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /**
     * 批量插入消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /**
     * 获取指定会话的所有消息
     * 按创建时间升序排列
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getAllByConversation(conversationId: String): List<MessageEntity>

    /**
     * 获取所有消息
     */
    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    suspend fun getAll(): List<MessageEntity>

    /**
     * 获取最近的消息（限制数量）
     * 用于缓存最近100条对话
     */
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    /**
     * 删除指定会话的所有消息
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    /**
     * 清空所有消息
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    /**
     * 获取消息数量
     */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getCount(): Int

    /**
     * 超过限制时删除最旧的消息
     * 保留最近 limit 条消息
     */
    @Query("""
        DELETE FROM messages WHERE id NOT IN (
            SELECT id FROM messages ORDER BY createdAt DESC LIMIT :limit
        )
    """)
    suspend fun deleteOldMessages(limit: Int)
}
