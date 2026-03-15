package com.mrshudson.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置数据存储管理类
 * 使用 DataStore 存储应用设置
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private const val SETTINGS_PREFERENCES_NAME = "settings_preferences"

        private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
            name = SETTINGS_PREFERENCES_NAME
        )

        // 服务器地址 Key
        private val SERVER_URL = stringPreferencesKey("server_url")
    }

    /**
     * 保存服务器地址
     */
    suspend fun saveServerUrl(url: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }

    /**
     * 获取服务器地址
     */
    fun getServerUrl(): Flow<String> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[SERVER_URL] ?: ""
        }
    }

    /**
     * 清除服务器地址（使用默认值）
     */
    suspend fun clearServerUrl() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(SERVER_URL)
        }
    }
}
