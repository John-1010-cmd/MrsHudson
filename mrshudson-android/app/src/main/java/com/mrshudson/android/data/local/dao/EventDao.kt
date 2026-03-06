package com.mrshudson.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrshudson.android.data.local.entity.EventEntity

/**
 * 日历事件数据访问对象
 * 提供日历事件数据的数据库操作
 */
@Dao
interface EventDao {

    /**
     * 插入或更新事件
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    /**
     * 批量插入或更新事件
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    /**
     * 根据ID获取事件
     */
    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getById(eventId: Long): EventEntity?

    /**
     * 获取指定日期范围内的事件
     */
    @Query("""
        SELECT * FROM events
        WHERE startTime >= :startDate AND startTime <= :endDate
        ORDER BY startTime ASC
    """)
    suspend fun getByDateRange(startDate: String, endDate: String): List<EventEntity>

    /**
     * 获取用户的所有事件
     */
    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY startTime ASC")
    suspend fun getAllByUser(userId: Long): List<EventEntity>

    /**
     * 获取所有事件
     */
    @Query("SELECT * FROM events ORDER BY startTime ASC")
    suspend fun getAll(): List<EventEntity>

    /**
     * 删除指定事件
     */
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun delete(eventId: Long)

    /**
     * 删除用户的所有事件
     */
    @Query("DELETE FROM events WHERE userId = :userId")
    suspend fun deleteAllByUser(userId: Long)

    /**
     * 清空所有事件
     */
    @Query("DELETE FROM events")
    suspend fun deleteAll()
}
