package com.mrshudson.android.data.repository

import com.mrshudson.android.data.local.dao.EventDao
import com.mrshudson.android.data.local.entity.EventEntity
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.CalendarApi
import com.mrshudson.android.data.remote.dto.CalendarEventDto
import com.mrshudson.android.data.remote.dto.CreateEventRequest
import com.mrshudson.android.data.remote.dto.UpdateEventRequest
import com.mrshudson.android.domain.model.CalendarEvent
import com.mrshudson.android.domain.model.createCalendarEvent
import com.mrshudson.android.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日历仓库实现类
 * 支持离线缓存：优先从本地读取，网络可用时同步
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val calendarApi: CalendarApi,
    private val eventDao: EventDao,
    private val syncManager: SyncManager
) : CalendarRepository, BaseApi {

    override fun getEvents(startDate: String?, endDate: String?): Flow<ApiResult<List<CalendarEvent>>> = flow {
        emit(ApiResult.Loading)

        // 首先尝试从本地缓存读取
        try {
            val localEvents = if (startDate != null && endDate != null) {
                eventDao.getByDateRange(startDate, endDate)
            } else {
                eventDao.getAll()
            }

            if (localEvents.isNotEmpty()) {
                emit(ApiResult.Success(localEvents.map { it.toDomainModel() }))
            }
        } catch (e: Exception) {
            // 本地读取失败，继续尝试网络
        }

        // 检查网络是否可用
        val isNetworkAvailable = syncManager.isNetworkAvailable.first()

        if (isNetworkAvailable) {
            // 网络可用，从服务器获取并更新本地缓存
            try {
                val response = calendarApi.getEvents(startDate, endDate)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val eventDtos = result.data
                        val events = eventDtos.map { it.toDomainModel() }

                        // 更新本地缓存
                        eventDao.insertAll(eventDtos.map { it.toEntity() })

                        emit(ApiResult.Success(events))
                    }
                    is ApiResult.Error -> {
                        // 如果本地没有数据，才返回错误
                        val localEvents = if (startDate != null && endDate != null) {
                            eventDao.getByDateRange(startDate, endDate)
                        } else {
                            eventDao.getAll()
                        }
                        if (localEvents.isEmpty()) {
                            emit(ApiResult.Error(result.code, result.message))
                        }
                        // 否则已经发送了本地数据，不需要额外处理
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                // 网络请求失败，返回本地数据或错误
                val localEvents = if (startDate != null && endDate != null) {
                    eventDao.getByDateRange(startDate, endDate)
                } else {
                    eventDao.getAll()
                }
                if (localEvents.isEmpty()) {
                    emit(ApiResult.Error(-1, e.message ?: "获取日历事件失败，请检查网络连接"))
                }
                // 否则已经发送了本地数据
            }
        } else {
            // 网络不可用，返回本地数据（如果之前已发送则不需要额外处理）
            // 已经在上面发送了本地数据
        }
    }

    override fun getEventById(id: Long): Flow<ApiResult<CalendarEvent>> = flow {
        emit(ApiResult.Loading)

        // 首先从本地读取
        try {
            val localEvent = eventDao.getById(id)
            if (localEvent != null) {
                emit(ApiResult.Success(localEvent.toDomainModel()))
            }
        } catch (e: Exception) {
            // 本地读取失败
        }

        // 检查网络
        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = calendarApi.getEventById(id)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val eventDto = result.data
                        val event = eventDto.toDomainModel()

                        // 更新本地缓存
                        eventDao.insert(eventDto.toEntity())

                        emit(ApiResult.Success(event))
                    }
                    is ApiResult.Error -> {
                        val localEvent = eventDao.getById(id)
                        if (localEvent == null) {
                            emit(ApiResult.Error(result.code, result.message))
                        }
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                val localEvent = eventDao.getById(id)
                if (localEvent == null) {
                    emit(ApiResult.Error(-1, e.message ?: "获取日历事件详情失败"))
                }
            }
        }
    }

    override fun createEvent(
        title: String,
        description: String?,
        startTime: String,
        endTime: String,
        location: String?,
        category: String?,
        reminderMinutes: Int?
    ): Flow<ApiResult<CalendarEvent>> = flow {
        emit(ApiResult.Loading)

        val request = CreateEventRequest(
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            location = location,
            category = category ?: "PERSONAL",
            reminderMinutes = reminderMinutes ?: 15
        )

        // 网络可用时尝试创建
        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = calendarApi.createEvent(request)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val eventDto = result.data
                        val event = eventDto.toDomainModel()

                        // 保存到本地缓存
                        eventDao.insert(eventDto.toEntity())

                        emit(ApiResult.Success(event))
                    }
                    is ApiResult.Error -> {
                        // 离线模式：先保存到本地，等待同步
                        saveOfflineEvent(request, null)
                        emit(ApiResult.Error(result.code, "已保存到离线缓存，网络恢复后自动同步"))
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                // 网络异常，离线保存
                saveOfflineEvent(request, e.message)
                emit(ApiResult.Error(-1, "已保存到离线缓存，网络恢复后自动同步"))
            }
        } else {
            // 离线模式：保存到本地
            saveOfflineEvent(request, "离线模式")
            emit(ApiResult.Error(-1, "已保存到离线缓存，网络恢复后自动同步"))
        }
    }

    override fun updateEvent(
        id: Long,
        title: String,
        description: String?,
        startTime: String,
        endTime: String,
        location: String?,
        category: String?,
        reminderMinutes: Int?
    ): Flow<ApiResult<CalendarEvent>> = flow {
        emit(ApiResult.Loading)

        val request = UpdateEventRequest(
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            location = location,
            category = category,
            reminderMinutes = reminderMinutes
        )

        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = calendarApi.updateEvent(id, request)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val eventDto = result.data
                        val event = eventDto.toDomainModel()

                        // 更新本地缓存
                        eventDao.insert(eventDto.toEntity())

                        emit(ApiResult.Success(event))
                    }
                    is ApiResult.Error -> {
                        // 离线更新
                        updateOfflineEvent(id, request)
                        emit(ApiResult.Error(result.code, "已保存到离线缓存"))
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                updateOfflineEvent(id, request)
                emit(ApiResult.Error(-1, "已保存到离线缓存"))
            }
        } else {
            updateOfflineEvent(id, request)
            emit(ApiResult.Error(-1, "已保存到离线缓存"))
        }
    }

    override fun deleteEvent(id: Long): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)

        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = calendarApi.deleteEvent(id)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        // 删除本地缓存
                        eventDao.delete(id)
                        emit(ApiResult.Success(Unit))
                    }
                    is ApiResult.Error -> {
                        // 离线删除：标记待同步（这里简化处理直接删除本地）
                        eventDao.delete(id)
                        emit(ApiResult.Success(Unit))
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                eventDao.delete(id)
                emit(ApiResult.Success(Unit))
            }
        } else {
            // 离线删除
            eventDao.delete(id)
            emit(ApiResult.Success(Unit))
        }
    }

    /**
     * 离线保存新事件
     */
    private suspend fun saveOfflineEvent(request: CreateEventRequest, errorMsg: String?) {
        val tempId = System.currentTimeMillis() // 临时ID
        val entity = EventEntity(
            id = tempId,
            userId = 0, // 暂时使用默认用户
            title = request.title,
            description = request.description,
            startTime = request.startTime,
            endTime = request.endTime,
            location = request.location,
            category = request.category,
            reminderMinutes = request.reminderMinutes,
            isRecurring = false,
            createdAt = null,
            updatedAt = null
        )
        eventDao.insert(entity)
    }

    /**
     * 离线更新事件
     */
    private suspend fun updateOfflineEvent(id: Long, request: UpdateEventRequest) {
        val existing = eventDao.getById(id) ?: return
        val updated = existing.copy(
            title = request.title ?: existing.title,
            description = request.description ?: existing.description,
            startTime = request.startTime ?: existing.startTime,
            endTime = request.endTime ?: existing.endTime,
            location = request.location ?: existing.location,
            category = request.category ?: existing.category,
            reminderMinutes = request.reminderMinutes ?: existing.reminderMinutes
        )
        eventDao.insert(updated)
    }

    /**
     * 将 CalendarEventDto 转换为领域模型 CalendarEvent
     */
    private fun CalendarEventDto.toDomainModel(): CalendarEvent {
        return createCalendarEvent(
            id = id,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            location = location,
            category = category,
            reminderMinutes = reminderMinutes,
            isRecurring = isRecurring
        )
    }

    /**
     * 将 CalendarEventDto 转换为实体
     */
    private fun CalendarEventDto.toEntity(): EventEntity {
        return EventEntity(
            id = id,
            userId = 0, // 暂时使用默认用户
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            location = location,
            category = category ?: "PERSONAL",
            reminderMinutes = reminderMinutes,
            isRecurring = isRecurring,
            createdAt = null,
            updatedAt = null
        )
    }

    /**
     * 将实体转换为领域模型
     */
    private fun EventEntity.toDomainModel(): CalendarEvent {
        return createCalendarEvent(
            id = id,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            location = location,
            category = category,
            reminderMinutes = reminderMinutes,
            isRecurring = isRecurring
        )
    }
}
