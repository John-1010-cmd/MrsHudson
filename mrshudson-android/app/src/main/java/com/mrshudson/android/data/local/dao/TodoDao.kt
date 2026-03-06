package com.mrshudson.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrshudson.android.data.local.entity.TodoEntity

/**
 * 待办事项数据访问对象
 * 提供待办事项数据的数据库操作
 */
@Dao
interface TodoDao {

    /**
     * 插入或更新待办事项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity)

    /**
     * 批量插入或更新待办事项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoEntity>)

    /**
     * 根据ID获取待办事项
     */
    @Query("SELECT * FROM todos WHERE id = :todoId")
    suspend fun getById(todoId: Long): TodoEntity?

    /**
     * 获取用户的所有待办事项
     */
    @Query("SELECT * FROM todos WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAllByUser(userId: Long): List<TodoEntity>

    /**
     * 获取所有待办事项
     */
    @Query("SELECT * FROM todos ORDER BY createdAt DESC")
    suspend fun getAll(): List<TodoEntity>

    /**
     * 根据状态获取待办事项
     */
    @Query("SELECT * FROM todos WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<TodoEntity>

    /**
     * 删除待办事项
     */
    @Query("DELETE FROM todos WHERE id = :todoId")
    suspend fun delete(todoId: Long)

    /**
     * 删除用户的所有待办事项
     */
    @Query("DELETE FROM todos WHERE userId = :userId")
    suspend fun deleteAllByUser(userId: Long)

    /**
     * 清空所有待办事项
     */
    @Query("DELETE FROM todos")
    suspend fun deleteAll()

    /**
     * 更新待办事项状态
     */
    @Query("UPDATE todos SET status = :status, completedAt = :completedAt WHERE id = :todoId")
    suspend fun updateStatus(todoId: Long, status: String, completedAt: String?)
}
