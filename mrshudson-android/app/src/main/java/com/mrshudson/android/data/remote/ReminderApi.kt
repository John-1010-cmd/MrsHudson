package com.mrshudson.android.data.remote

import com.mrshudson.android.data.remote.dto.ReminderDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 提醒相关 API 接口
 * 定义提醒的查询和操作接口
 */
interface ReminderApi {

    /**
     * 获取所有提醒
     *
     * @return 提醒列表
     */
    @GET("reminders")
    suspend fun getReminders(): Response<ResultDto<List<ReminderDto>>>

    /**
     * 获取未读提醒
     *
     * @return 未读提醒列表
     */
    @GET("reminders/unread")
    suspend fun getUnreadReminders(): Response<ResultDto<List<ReminderDto>>>

    /**
     * 获取未读提醒数量
     *
     * @return 未读数量
     */
    @GET("reminders/unread-count")
    suspend fun getUnreadCount(): Response<ResultDto<UnreadCountDto>>

    /**
     * 标记提醒为已读
     *
     * @param id 提醒ID
     * @return 操作结果
     */
    @PUT("reminders/{id}/read")
    suspend fun markAsRead(
        @Path("id") id: Long
    ): Response<ResultDto<Unit>>

    /**
     * 批量标记所有提醒为已读
     *
     * @return 操作结果
     */
    @PUT("reminders/read-all")
    suspend fun markAllAsRead(): Response<ResultDto<Unit>>

    /**
     * 延迟提醒
     *
     * @param id 提醒ID
     * @param minutes 延迟分钟数
     * @return 延迟后的提醒
     */
    @PUT("reminders/{id}/snooze")
    suspend fun snooze(
        @Path("id") id: Long,
        @Query("minutes") minutes: Int
    ): Response<ResultDto<ReminderDto>>
}

/**
 * 未读数量 DTO
 */
data class UnreadCountDto(
    val count: Int
)
