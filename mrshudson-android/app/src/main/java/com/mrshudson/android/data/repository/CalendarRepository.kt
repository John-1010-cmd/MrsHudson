package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.domain.model.CalendarEvent
import kotlinx.coroutines.flow.Flow

/**
 * 日历仓库接口
 * 定义日历事件的数据操作
 */
interface CalendarRepository {

    /**
     * 获取日历事件列表
     * 获取指定日期范围内的日历事件
     *
     * @param startDate 开始日期，格式为 YYYY-MM-DD
     * @param endDate 结束日期，格式为 YYYY-MM-DD
     * @return 日历事件列表结果的 Flow
     */
    fun getEvents(startDate: String?, endDate: String?): Flow<ApiResult<List<CalendarEvent>>>

    /**
     * 获取单个日历事件详情
     *
     * @param id 事件ID
     * @return 日历事件详情结果的 Flow
     */
    fun getEventById(id: Long): Flow<ApiResult<CalendarEvent>>

    /**
     * 创建日历事件
     * 创建一个新的日历事件
     *
     * @param title 事件标题
     * @param description 事件描述
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param location 事件地点
     * @param category 事件分类
     * @param reminderMinutes 提醒分钟数
     * @return 创建结果的 Flow
     */
    fun createEvent(
        title: String,
        description: String?,
        startTime: String,
        endTime: String,
        location: String?,
        category: String?,
        reminderMinutes: Int?
    ): Flow<ApiResult<CalendarEvent>>

    /**
     * 更新日历事件
     * 更新指定的日历事件
     *
     * @param id 事件ID
     * @param title 事件标题
     * @param description 事件描述
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param location 事件地点
     * @param category 事件分类
     * @param reminderMinutes 提醒分钟数
     * @return 更新结果的 Flow
     */
    fun updateEvent(
        id: Long,
        title: String,
        description: String?,
        startTime: String,
        endTime: String,
        location: String?,
        category: String?,
        reminderMinutes: Int?
    ): Flow<ApiResult<CalendarEvent>>

    /**
     * 删除日历事件
     * 删除指定的日历事件
     *
     * @param id 要删除的事件ID
     * @return 删除结果
     */
    fun deleteEvent(id: Long): Flow<ApiResult<Unit>>
}
