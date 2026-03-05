package com.mrshudson.android.data.local.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore Preferences 键定义
 * 集中管理所有 DataStore 存储的键名
 */
object PreferencesKeys {

    /**
     * Access Token 存储键
     * 用于存储 JWT 访问令牌
     */
    val ACCESS_TOKEN = stringPreferencesKey("access_token")

    /**
     * Refresh Token 存储键
     * 用于存储 JWT 刷新令牌
     */
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
}
