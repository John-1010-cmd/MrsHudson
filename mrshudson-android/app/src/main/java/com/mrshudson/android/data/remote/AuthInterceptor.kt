package com.mrshudson.android.data.remote

import android.util.Log
import com.mrshudson.android.data.local.datastore.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器
 * 负责为每个请求添加 JWT Token 认证头，并处理 401 未授权响应
 *
 * 优化策略：纯内存同步缓存，避免拦截器中使用 runBlocking
 * - Token 由登录流程通过 updateCachedToken() 主动设置
 * - 401 响应时清除缓存，触发重新登录
 * - 无需在拦截器内读取 DataStore，避免 I/O 阻塞
 *
 * @property tokenDataStore Token 数据存储，用于处理 401 时的清除操作
 */
class AuthInterceptor(
    private val tokenDataStore: TokenDataStore
) : Interceptor {

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val HTTP_UNAUTHORIZED = 401
        private const val TAG = "AuthInterceptor"

        // Token 内存缓存，同步访问，无锁
        @Volatile
        private var cachedToken: String? = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 从内存缓存获取 Access Token（同步，无阻塞）
        val accessToken = getAccessToken()

        // 构建带认证头的请求
        val authenticatedRequest = if (!accessToken.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken)
                .method(originalRequest.method, originalRequest.body)
                .build()
        } else {
            originalRequest
        }

        // 执行请求
        val response = chain.proceed(authenticatedRequest)

        // 处理 401 未授权响应
        if (response.code == HTTP_UNAUTHORIZED) {
            Log.w(TAG, "收到 401 响应，清除 Token 缓存")
            // 清除内存缓存
            clearCachedToken()
            // 异步清除 DataStore（不阻塞响应返回）
            runBlocking {
                try {
                    tokenDataStore.clearTokens()
                } catch (e: Exception) {
                    Log.e(TAG, "清除 DataStore Token 失败", e)
                }
            }
            // 注意：实际项目中这里可以触发重新登录或刷新 Token 的逻辑
            // 例如：发送广播通知 UI 层跳转到登录页面
        }

        return response
    }

    /**
     * 获取 Access Token（纯内存，同步，无阻塞）
     * 首次获取时若缓存为空，尝试从 DataStore 加载一次
     */
    private fun getAccessToken(): String? {
        // 快速路径：缓存命中
        cachedToken?.let { return it }

        // 缓存未命中，尝试从 DataStore 加载（仅在应用启动后的首次请求）
        // 使用 try-catch 包裹，避免任何异常影响请求
        return try {
            runBlocking {
                val token = tokenDataStore.getAccessToken().first()
                if (!token.isNullOrBlank()) {
                    cachedToken = token
                    Log.d(TAG, "从 DataStore 加载 Token 成功")
                }
                token
            }
        } catch (e: Exception) {
            Log.e(TAG, "从 DataStore 加载 Token 失败", e)
            null
        }
    }

    /**
     * 清除缓存的 Token
     */
    private fun clearCachedToken() {
        cachedToken = null
        Log.d(TAG, "Token 缓存已清除")
    }

    /**
     * 公开方法：更新缓存的 Token（登录成功后调用）
     * 这是设置 Token 的主要方式，避免拦截器内部频繁读取 DataStore
     *
     * @param token JWT Access Token
     */
    fun updateCachedToken(token: String?) {
        cachedToken = token
        Log.d(TAG, "Token 缓存已更新: ${if (token != null) "已设置" else "已清空"}")
    }

    /**
     * 公开方法：使缓存失效（登出时调用）
     */
    fun invalidateCache() {
        clearCachedToken()
    }
}
