package com.mrshudson.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrshudson.android.data.local.entity.UserEntity

/**
 * 用户数据访问对象
 * 提供用户数据的数据库操作
 */
@Dao
interface UserDao {

    /**
     * 插入或更新用户
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    /**
     * 根据ID获取用户
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): UserEntity?

    /**
     * 获取所有用户
     */
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    /**
     * 删除用户
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long)

    /**
     * 清空所有用户
     */
    @Query("DELETE FROM users")
    suspend fun clearAllUsers()
}
