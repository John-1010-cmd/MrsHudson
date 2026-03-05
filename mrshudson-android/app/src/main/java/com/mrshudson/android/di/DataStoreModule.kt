package com.mrshudson.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.mrshudson.android.data.local.datastore.TokenDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DataStore 模块
 * 提供 Preferences DataStore 实例和 TokenDataStore
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * DataStore 文件名称
     */
    private const val DATASTORE_NAME = "mrshudson_preferences"

    /**
     * 提供 DataStore<Preferences> 实例
     * 使用 PreferenceDataStoreFactory 创建
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(DATASTORE_NAME)
        }
    }

    /**
     * 提供 TokenDataStore 实例
     * 用于 Token 的存储和管理
     */
    @Provides
    @Singleton
    fun provideTokenDataStore(
        @ApplicationContext context: Context
    ): TokenDataStore {
        return TokenDataStore(context)
    }
}
