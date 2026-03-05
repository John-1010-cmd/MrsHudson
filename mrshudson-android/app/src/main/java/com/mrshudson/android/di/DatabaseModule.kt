package com.mrshudson.android.di

import android.content.Context
import androidx.room.Room
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

    // DAO 提供方法将在添加实体和 DAO 后取消注释
    // 示例：
    // @Provides
    // @Singleton
    // fun provideUserDao(database: MrsHudsonDatabase): UserDao {
    //     return database.userDao()
    // }
    //
    // @Provides
    // @Singleton
    // fun provideConversationDao(database: MrsHudsonDatabase): ConversationDao {
    //     return database.conversationDao()
    // }
    //
    // @Provides
    // @Singleton
    // fun provideChatMessageDao(database: MrsHudsonDatabase): ChatMessageDao {
    //     return database.chatMessageDao()
    // }
    //
    // @Provides
    // @Singleton
    // fun provideCalendarEventDao(database: MrsHudsonDatabase): CalendarEventDao {
    //     return database.calendarEventDao()
    // }
    //
    // @Provides
    // @Singleton
    // fun provideTodoItemDao(database: MrsHudsonDatabase): TodoItemDao {
    //     return database.todoItemDao()
    // }
}
