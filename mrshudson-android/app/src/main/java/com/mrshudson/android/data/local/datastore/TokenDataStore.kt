package com.mrshudson.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Token 数据存储管理类
 * 使用 DataStore 安全地存储和检索 JWT Token
 */
class TokenDataStore(private val context: Context) {

    companion object {
        private const val TOKEN_PREFERENCES_NAME = "token_preferences"

        private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
            name = TOKEN_PREFERENCES_NAME
        )
    }

    /**
     * 保存 Access Token 和 Refresh Token
     *
     * @param accessToken JWT 访问令牌
     * @param refreshToken JWT 刷新令牌
     */
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.tokenDataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = accessToken
            preferences[PreferencesKeys.REFRESH_TOKEN] = refreshToken
        }
    }

    /**
     * 获取 Access Token
     *
     * @return Access Token 的 Flow，如果没有存储则返回 null
     */
    fun getAccessToken(): Flow<String?> {
        return context.tokenDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN]
        }
    }

    /**
     * 获取 Refresh Token
     *
     * @return Refresh Token 的 Flow，如果没有存储则返回 null
     */
    fun getRefreshToken(): Flow<String?> {
        return context.tokenDataStore.data.map { preferences ->
            preferences[PreferencesKeys.REFRESH_TOKEN]
        }
    }

    /**
     * 清除所有存储的 Token
     * 通常在用户登出时调用
     */
    suspend fun clearTokens() {
        context.tokenDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACCESS_TOKEN)
            preferences.remove(PreferencesKeys.REFRESH_TOKEN)
        }
    }

    /**
     * 检查是否已登录（是否有 Access Token）
     *
     * @return 是否已登录的 Flow
     */
    fun isLoggedIn(): Flow<Boolean> {
        return context.tokenDataStore.data.map { preferences ->
            !preferences[PreferencesKeys.ACCESS_TOKEN].isNullOrBlank()
        }
    }
}
