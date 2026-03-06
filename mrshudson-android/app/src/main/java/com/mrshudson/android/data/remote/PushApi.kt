package com.mrshudson.android.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 推送 API 接口
 * 用于注册设备 Token 到后端服务器
 */
interface PushApi {

    /**
     * 注册设备 Token
     *
     * @param request 包含 deviceToken 和 platform 的请求体
     * @return 后端统一响应结果
     */
    @POST("push/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<ResultDto<Unit>>
}

/**
 * 注册设备请求体
 *
 * @property deviceToken 设备的 FCM Token
 * @property platform 平台类型 (android/ios)
 */
data class RegisterDeviceRequest(
    val deviceToken: String,
    val platform: String
)
