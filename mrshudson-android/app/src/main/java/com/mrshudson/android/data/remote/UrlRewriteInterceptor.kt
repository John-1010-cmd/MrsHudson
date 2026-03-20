package com.mrshudson.android.data.remote

import android.content.Context
import com.mrshudson.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI
import javax.inject.Inject

/**
 * URL 重写拦截器
 * 用于在运行时动态修改请求的服务器地址
 */
class UrlRewriteInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {

    @Volatile
    private var initialized = false

    /**
     * 延迟初始化服务器地址
     * 由于 NetworkModule 初始化时无法使用协程，在此进行延迟加载
     */
    private fun ensureInitialized() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    try {
                        val settingsDataStore = SettingsDataStore(context)
                        // 使用 runBlocking 进行首次初始化（只执行一次）
                        val url = runBlocking {
                            settingsDataStore.getServerUrl().first()
                        }
                        if (url.isNotBlank()) {
                            ServerUrlManager.setServerUrl(url)
                        }
                    } catch (e: Exception) {
                        // 初始化失败，使用默认地址
                    }
                    initialized = true
                }
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // 确保已初始化
        ensureInitialized()

        val originalRequest = chain.request()

        // 检查是否设置了自定义服务器地址
        val customUrl = ServerUrlManager.getServerUrl()
        if (customUrl.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        // 获取原始 URL 信息
        val originalUrlString = originalRequest.url.toString()
        val uri = URI(originalUrlString)
        val path = uri.rawPath ?: "/"
        val query = uri.rawQuery ?: ""

        // 标准化用户输入的地址：
        // 1. 去掉末尾的 /
        // 2. 去掉末尾的 /api 或 /api/
        var normalizedBase = customUrl.trim().removeSuffix("/")
        if (normalizedBase.endsWith("/api")) {
            normalizedBase = normalizedBase.removeSuffix("/api")
        } else if (normalizedBase.endsWith("/api/")) {
            normalizedBase = normalizedBase.removeSuffix("/api/")
        }

        // 构建新路径：移除开头的 /api0
        val cleanPath = if (path.startsWith("/api/")) {
            path.removePrefix("/api")
        } else if (path.startsWith("/api")) {
            path.removePrefix("/api")
        } else {
            path
        }
        val finalPath = if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"

        // 构建新的完整 URL
        val newUrl = if (query.isNotBlank()) {
            "$normalizedBase/api$finalPath?$query"
        } else {
            "$normalizedBase/api$finalPath"
        }

        // 重建请求
        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
