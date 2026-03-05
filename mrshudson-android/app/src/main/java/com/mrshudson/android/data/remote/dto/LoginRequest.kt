package com.mrshudson.android.data.remote.dto

/**
 * 登录请求数据类
 * 用于发送用户登录请求
 *
 * @property username 用户名
 * @property password 密码
 */
data class LoginRequest(
    val username: String,
    val password: String
)
