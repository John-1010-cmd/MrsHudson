package com.mrshudson.android.di

import com.mrshudson.android.data.repository.AuthRepository
import com.mrshudson.android.data.repository.AuthRepositoryImpl
import com.mrshudson.android.data.repository.CalendarRepository
import com.mrshudson.android.data.repository.CalendarRepositoryImpl
import com.mrshudson.android.data.repository.ChatRepository
import com.mrshudson.android.data.repository.ChatRepositoryImpl
import com.mrshudson.android.data.repository.PushRepository
import com.mrshudson.android.data.repository.PushRepositoryImpl
import com.mrshudson.android.data.repository.ReminderRepository
import com.mrshudson.android.data.repository.ReminderRepositoryImpl
import com.mrshudson.android.data.repository.RouteRepository
import com.mrshudson.android.data.repository.RouteRepositoryImpl
import com.mrshudson.android.data.repository.TodoRepository
import com.mrshudson.android.data.repository.TodoRepositoryImpl
import com.mrshudson.android.data.repository.WeatherRepository
import com.mrshudson.android.data.repository.WeatherRepositoryImpl
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

    /**
     * 绑定聊天仓库
     */
    @Binds
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    /**
     * 绑定天气仓库
     */
    @Binds
    abstract fun bindWeatherRepository(
        weatherRepositoryImpl: WeatherRepositoryImpl
    ): WeatherRepository

    /**
     * 绑定日历仓库
     */
    @Binds
    abstract fun bindCalendarRepository(
        calendarRepositoryImpl: CalendarRepositoryImpl
    ): CalendarRepository

    /**
     * 绑定待办仓库
     */
    @Binds
    abstract fun bindTodoRepository(
        todoRepositoryImpl: TodoRepositoryImpl
    ): TodoRepository

    /**
     * 绑定路线仓库
     */
    @Binds
    abstract fun bindRouteRepository(
        routeRepositoryImpl: RouteRepositoryImpl
    ): RouteRepository

    /**
     * 绑定推送仓库
     */
    @Binds
    abstract fun bindPushRepository(
        pushRepositoryImpl: PushRepositoryImpl
    ): PushRepository

    /**
     * 绑定提醒仓库
     */
    @Binds
    abstract fun bindReminderRepository(
        reminderRepositoryImpl: ReminderRepositoryImpl
    ): ReminderRepository
}
