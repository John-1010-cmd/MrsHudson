package com.mrshudson.android.data.remote

import com.mrshudson.android.data.local.datastore.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器
 * 负责为每个请求添加 JWT Token 认证头，并处理 401 未授权响应
 *
 * @property tokenDataStore Token 数据存储，用于获取和清除 Token
 */
class AuthInterceptor(
    private val tokenDataStore: TokenDataStore
) : Interceptor {

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val HTTP_UNAUTHORIZED = 401
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 从 DataStore 获取 Access Token（使用 runBlocking 在同步上下文中获取）
        val accessToken = runBlocking {
            tokenDataStore.getAccessToken().first()
        }

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
            // 清除过期的 Token
            runBlocking {
                tokenDataStore.clearTokens()
            }
            // 注意：实际项目中这里可以触发重新登录或刷新 Token 的逻辑
            // 例如：发送广播通知 UI 层跳转到登录页面
        }

        return response
    }
}
