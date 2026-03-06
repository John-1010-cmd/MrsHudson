package com.mrshudson.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户实体类
 * 用于 Room 数据库存储用户信息
 *
 * @property id 用户ID
 * @property username 用户名
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: Long,
    val username: String
)
