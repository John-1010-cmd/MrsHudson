package com.mrshudson.android.data.remote.dto

/**
 * 用户数据传输对象
 * 用于获取当前登录用户信息
 *
 * @property id 用户唯一标识
 * @property username 用户名
 * @property createdAt 用户创建时间（ISO 8601 格式）
 * @property updatedAt 用户信息更新时间（ISO 8601 格式）
 */
data class UserDto(
    val id: Long,
    val username: String,
    val createdAt: String?,
    val updatedAt: String?
)
