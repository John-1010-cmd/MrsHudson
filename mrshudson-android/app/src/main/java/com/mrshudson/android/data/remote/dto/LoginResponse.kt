package com.mrshudson.android.data.remote.dto

/**
 * 登录响应数据类
 * 包含登录成功后返回的 Token 信息
 *
 * @property id 用户ID
 * @property username 用户名
 * @property accessToken JWT 访问令牌，用于 API 请求认证
 * @property refreshToken JWT 刷新令牌，用于获取新的访问令牌
 * @property expiresIn 令牌过期时间（秒）
 */
data class LoginResponse(
    val id: Long,
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long? = null
)
