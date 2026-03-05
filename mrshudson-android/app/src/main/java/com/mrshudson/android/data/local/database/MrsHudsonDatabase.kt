package com.mrshudson.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mrshudson.android.data.local.dao.*
import com.mrshudson.android.data.local.entity.*

/**
 * MrsHudson 应用的数据库类
 * 使用 Room 持久化库管理本地数据存储
 */
@Database(
    entities = [],
    version = 1,
    exportSchema = false
)
abstract class MrsHudsonDatabase : RoomDatabase() {

    companion object {
        const val DATABASE_NAME = "mrshudson_database"
    }

    // DAO 方法将在添加实体后在这里定义
    // abstract fun userDao(): UserDao
    // abstract fun conversationDao(): ConversationDao
    // abstract fun chatMessageDao(): ChatMessageDao
    // abstract fun calendarEventDao(): CalendarEventDao
    // abstract fun todoItemDao(): TodoItemDao
}
