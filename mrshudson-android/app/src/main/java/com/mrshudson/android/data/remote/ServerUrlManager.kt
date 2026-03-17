package com.mrshudson.android.data.remote

/**
 * 服务器地址管理器
 * 用于在运行时动态管理 API 服务器地址
 */
object ServerUrlManager {

    /**
     * 当前使用的服务器地址
     * 默认为空，表示使用 NetworkModule 中的默认值
     */
    @Volatile
    private var currentServerUrl: String = ""

    /**
     * 获取当前服务器地址
     * 如果为空则返回 null，表示使用默认值
     */
    fun getServerUrl(): String? = currentServerUrl.ifBlank { null }

    /**
     * 设置服务器地址
     */
    fun setServerUrl(url: String) {
        currentServerUrl = url
    }

    /**
     * 清除自定义服务器地址，使用默认值
     */
    fun clearServerUrl() {
        currentServerUrl = ""
    }

    /**
     * 检查是否设置了自定义服务器地址
     */
    fun hasCustomUrl(): Boolean = currentServerUrl.isNotBlank()
}
