package com.mrshudson.android.data.remote

import com.mrshudson.android.data.remote.dto.CalendarEventDto
import com.mrshudson.android.data.remote.dto.CreateEventRequest
import com.mrshudson.android.data.remote.dto.UpdateEventRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 日历相关 API 接口
 * 定义日历事件的 CRUD 操作接口
 */
interface CalendarApi {

    /**
     * 获取日历事件列表
     * 获取指定日期范围内的日历事件
     *
     * @param startDate 开始日期，格式为 YYYY-MM-DD
     * @param endDate 结束日期，格式为 YYYY-MM-DD
     * @return 日历事件列表
     */
    @GET("calendar/events")
    suspend fun getEvents(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): Response<ResultDto<List<CalendarEventDto>>>

    /**
     * 获取单个日历事件详情
     *
     * @param id 事件ID
     * @return 日历事件详情
     */
    @GET("calendar/events/{id}")
    suspend fun getEventById(
        @Path("id") id: Long
    ): Response<ResultDto<CalendarEventDto>>

    /**
     * 创建日历事件
     * 创建一个新的日历事件
     *
     * @param request 创建事件请求
     * @return 新创建的事件信息
     */
    @POST("calendar/events")
    suspend fun createEvent(
        @Body request: CreateEventRequest
    ): Response<ResultDto<CalendarEventDto>>

    /**
     * 更新日历事件
     * 更新指定的日历事件
     *
     * @param id 事件ID
     * @param request 更新事件请求
     * @return 更新后的事件信息
     */
    @PUT("calendar/events/{id}")
    suspend fun updateEvent(
        @Path("id") id: Long,
        @Body request: UpdateEventRequest
    ): Response<ResultDto<CalendarEventDto>>

    /**
     * 删除日历事件
     * 删除指定的日历事件
     *
     * @param id 要删除的事件ID
     * @return 删除结果
     */
    @DELETE("calendar/events/{id}")
    suspend fun deleteEvent(
        @Path("id") id: Long
    ): Response<ResultDto<Unit>>
}
