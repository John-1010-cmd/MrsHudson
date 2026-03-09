package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * 提醒仓库接口
 * 定义提醒的数据操作
 */
interface ReminderRepository {

    /**
     * 获取所有提醒
     *
     * @return 提醒列表
     */
    fun getReminders(): Flow<ApiResult<List<Reminder>>>

    /**
     * 获取未读提醒
     *
     * @return 未读提醒列表
     */
    fun getUnreadReminders(): Flow<ApiResult<List<Reminder>>>

    /**
     * 获取未读提醒数量
     *
     * @return 未读数量
     */
    fun getUnreadCount(): Flow<ApiResult<Int>>

    /**
     * 标记提醒为已读
     *
     * @param id 提醒ID
     * @return 操作结果
     */
    fun markAsRead(id: Long): Flow<ApiResult<Unit>>

    /**
     * 批量标记所有提醒为已读
     *
     * @return 操作结果
     */
    fun markAllAsRead(): Flow<ApiResult<Unit>>

    /**
     * 延迟提醒
     *
     * @param id 提醒ID
     * @param minutes 延迟分钟数
     * @return 延迟后的提醒
     */
    fun snooze(id: Long, minutes: Int): Flow<ApiResult<Reminder>>
}
