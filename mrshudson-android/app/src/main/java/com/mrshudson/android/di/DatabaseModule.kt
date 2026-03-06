package com.mrshudson.android.di

import android.content.Context
import androidx.room.Room
import com.mrshudson.android.data.local.dao.EventDao
import com.mrshudson.android.data.local.dao.MessageDao
import com.mrshudson.android.data.local.dao.TodoDao
import com.mrshudson.android.data.local.dao.UserDao
import com.mrshudson.android.data.local.database.MrsHudsonDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库模块
 * 提供 Room 数据库实例和各个 DAO
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 数据库名称
     */
    private const val DATABASE_NAME = "mrshudson_db"

    /**
     * 提供 MrsHudsonDatabase 实例
     * 使用 Room 数据库构建器创建数据库
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): MrsHudsonDatabase {
        return Room.databaseBuilder(
            context,
            MrsHudsonDatabase::class.java,
            DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 提供用户 DAO
     */
    @Provides
    @Singleton
    fun provideUserDao(database: MrsHudsonDatabase): UserDao {
        return database.userDao()
    }

    /**
     * 提供消息 DAO
     */
    @Provides
    @Singleton
    fun provideMessageDao(database: MrsHudsonDatabase): MessageDao {
        return database.messageDao()
    }

    /**
     * 提供日历事件 DAO
     */
    @Provides
    @Singleton
    fun provideEventDao(database: MrsHudsonDatabase): EventDao {
        return database.eventDao()
    }

    /**
     * 提供待办事项 DAO
     */
    @Provides
    @Singleton
    fun provideTodoDao(database: MrsHudsonDatabase): TodoDao {
        return database.todoDao()
    }
}
