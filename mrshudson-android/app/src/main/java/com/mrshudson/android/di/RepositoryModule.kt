package com.mrshudson.android.di

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

    // Repository 绑定将在创建 Repository 接口和实现类后添加
    // 示例：
    // @Binds
    // abstract fun bindUserRepository(
    //     userRepositoryImpl: UserRepositoryImpl
    // ): UserRepository
    //
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
    //
    // @Binds
    // abstract fun bindAuthRepository(
    //     authRepositoryImpl: AuthRepositoryImpl
    // ): AuthRepository
}
