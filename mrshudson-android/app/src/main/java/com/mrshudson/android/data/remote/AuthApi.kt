package com.mrshudson.android.data.remote

import com.mrshudson.android.data.remote.dto.LoginRequest
import com.mrshudson.android.data.remote.dto.LoginResponse
import com.mrshudson.android.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 认证相关 API 接口
 * 定义用户登录、登出和获取当前用户信息等接口
 */
interface AuthApi {

    /**
     * 用户登录
     * 使用用户名和密码获取 JWT Token
     *
     * @param request 登录请求，包含用户名和密码
     * @return 登录响应，包含 accessToken 和 refreshToken
     */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ResultDto<LoginResponse>>

    /**
     * 用户登出
     * 通知服务端清除当前用户的 Token 信息
     *
     * @return 登出结果
     */
    @POST("auth/logout")
    suspend fun logout(): Response<ResultDto<Unit>>

    /**
     * 获取当前登录用户信息
     * 需要携带有效的 JWT Token
     *
     * @return 当前用户信息
     */
    @GET("auth/me")
    suspend fun getCurrentUser(): Response<ResultDto<UserDto>>
}
