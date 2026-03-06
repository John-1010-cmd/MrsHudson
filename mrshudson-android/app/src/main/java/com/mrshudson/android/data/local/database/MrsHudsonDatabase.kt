package com.mrshudson.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mrshudson.android.data.local.dao.EventDao
import com.mrshudson.android.data.local.dao.MessageDao
import com.mrshudson.android.data.local.dao.TodoDao
import com.mrshudson.android.data.local.dao.UserDao
import com.mrshudson.android.data.local.entity.EventEntity
import com.mrshudson.android.data.local.entity.MessageEntity
import com.mrshudson.android.data.local.entity.TodoEntity
import com.mrshudson.android.data.local.entity.UserEntity

/**
 * MrsHudson 应用的数据库类
 * 使用 Room 持久化库管理本地数据存储
 */
@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        EventEntity::class,
        TodoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MrsHudsonDatabase : RoomDatabase() {

    companion object {
        const val DATABASE_NAME = "mrshudson_database"
    }

    /**
     * 获取用户 DAO
     */
    abstract fun userDao(): UserDao

    /**
     * 获取消息 DAO
     */
    abstract fun messageDao(): MessageDao

    /**
     * 获取日历事件 DAO
     */
    abstract fun eventDao(): EventDao

    /**
     * 获取待办事项 DAO
     */
    abstract fun todoDao(): TodoDao
}
