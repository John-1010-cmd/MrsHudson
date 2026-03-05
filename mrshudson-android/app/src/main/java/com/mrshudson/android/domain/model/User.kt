package com.mrshudson.android.domain.model

/**
 * 用户领域模型
 * 表示已登录用户的基本信息
 *
 * @property id 用户唯一标识
 * @property username 用户名
 */
data class User(
    val id: Long,
    val username: String
)
