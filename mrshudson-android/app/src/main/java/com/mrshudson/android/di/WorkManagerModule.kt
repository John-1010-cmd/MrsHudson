package com.mrshudson.android.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WorkManager 模块
 * 提供 WorkManager 和 HiltWorkerFactory 实例
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    /**
     * 提供 WorkManager 实例
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }

    /**
     * 提供 HiltWorkerFactory 实例
     * 由 Hilt 自动注入
     */
    // HiltWorkerFactory 由 Hilt 自动提供，无需手动配置
}
