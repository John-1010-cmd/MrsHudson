package com.mrshudson.android.di

import com.mrshudson.android.data.repository.AuthRepository
import com.mrshudson.android.data.repository.AuthRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 仓库模块
 * 抽象模块，用于绑定 Repository 接口和实现类
 * 使用 @Binds 注解进行依赖注入绑定
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * 绑定认证仓库
     */
    @Binds
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    // TODO: 后续添加其他 Repository 绑定
    // @Binds
    // abstract fun bindConversationRepository(
    //     conversationRepositoryImpl: ConversationRepositoryImpl
    // ): ConversationRepository
    //
    // @Binds
    // abstract fun bindChatRepository(
    //     chatRepositoryImpl: ChatRepositoryImpl
    // ): ChatRepository
    //
    // @Binds
    // abstract fun bindCalendarRepository(
    //     calendarRepositoryImpl: CalendarRepositoryImpl
    // ): CalendarRepository
    //
    // @Binds
    // abstract fun bindTodoRepository(
    //     todoRepositoryImpl: TodoRepositoryImpl
    // ): TodoRepository
}
