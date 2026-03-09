package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.ReminderApi
import com.mrshudson.android.data.remote.dto.ReminderDto
import com.mrshudson.android.domain.model.Reminder
import com.mrshudson.android.domain.model.createReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提醒仓库实现类
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderApi: ReminderApi
) : ReminderRepository, BaseApi {

    override fun getReminders(): Flow<ApiResult<List<Reminder>>> = flow {
        emit(ApiResult.Loading)

        try {
            val response = reminderApi.getReminders()
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val reminders = result.data.map { it.toDomainModel() }
                    emit(ApiResult.Success(reminders))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "获取提醒失败"))
        }
    }

    override fun getUnreadReminders(): Flow<ApiResult<List<Reminder>>> = flow {
        emit(ApiResult.Loading)

        try {
            val response = reminderApi.getUnreadReminders()
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val reminders = result.data.map { it.toDomainModel() }
                    emit(ApiResult.Success(reminders))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "获取未读提醒失败"))
        }
    }

    override fun getUnreadCount(): Flow<ApiResult<Int>> = flow {
        emit(ApiResult.Loading)

        try {
            val response = reminderApi.getUnreadCount()
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    emit(ApiResult.Success(result.data.count))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "获取未读数量失败"))
        }
    }

    override fun markAsRead(id: Long): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)

        try {
            val response = reminderApi.markAsRead(id)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    emit(ApiResult.Success(Unit))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "标记已读失败"))
        }
    }

    override fun markAllAsRead(): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)

        try {
            val response = reminderApi.markAllAsRead()
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    emit(ApiResult.Success(Unit))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "批量标记已读失败"))
        }
    }

    override fun snooze(id: Long, minutes: Int): Flow<ApiResult<Reminder>> = flow {
        emit(ApiResult.Loading)

        try {
            val response = reminderApi.snooze(id, minutes)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val reminder = result.data.toDomainModel()
                    emit(ApiResult.Success(reminder))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "延迟提醒失败"))
        }
    }

    /**
     * 将 DTO 转换为领域模型
     */
    private fun ReminderDto.toDomainModel(): Reminder {
        return createReminder(
            id = id,
            type = type,
            title = title,
            content = content,
            remindAt = remindAt,
            isRead = isRead,
            refId = refId,
            createdAt = createdAt
        )
    }
}
