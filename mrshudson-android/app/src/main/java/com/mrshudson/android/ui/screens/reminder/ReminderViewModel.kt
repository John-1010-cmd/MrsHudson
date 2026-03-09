package com.mrshudson.android.ui.screens.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.ReminderRepository
import com.mrshudson.android.domain.model.Reminder
import com.mrshudson.android.domain.model.ReminderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 提醒页面 UI 状态
 */
data class ReminderUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFilter: ReminderFilter = ReminderFilter.ALL,
    val unreadCount: Int = 0
)

/**
 * 提醒筛选类型
 */
enum class ReminderFilter {
    ALL,       // 全部
    EVENT,     // 日程
    TODO,      // 待办
    UNREAD     // 未读
}

/**
 * 提醒页面 ViewModel
 * 管理提醒数据和业务逻辑
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    init {
        loadReminders()
    }

    /**
     * 加载提醒列表
     */
    fun loadReminders() {
        viewModelScope.launch {
            reminderRepository.getReminders().collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            reminders = result.data,
                            isLoading = false,
                            unreadCount = result.data.count { !it.isRead }
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * 设置筛选类型
     */
    fun setFilter(filter: ReminderFilter) {
        _uiState.value = _uiState.value.copy(currentFilter = filter)
    }

    /**
     * 获取筛选后的提醒列表
     */
    fun getFilteredReminders(): List<Reminder> {
        return when (_uiState.value.currentFilter) {
            ReminderFilter.ALL -> _uiState.value.reminders
            ReminderFilter.EVENT -> _uiState.value.reminders.filter { it.type == ReminderType.EVENT }
            ReminderFilter.TODO -> _uiState.value.reminders.filter { it.type == ReminderType.TODO }
            ReminderFilter.UNREAD -> _uiState.value.reminders.filter { !it.isRead }
        }
    }

    /**
     * 标记提醒为已读
     */
    fun markAsRead(reminderId: Long) {
        viewModelScope.launch {
            reminderRepository.markAsRead(reminderId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        // 更新本地状态
                        val updatedReminders = _uiState.value.reminders.map { reminder ->
                            if (reminder.id == reminderId) {
                                reminder.copy(isRead = true)
                            } else {
                                reminder
                            }
                        }
                        _uiState.value = _uiState.value.copy(
                            reminders = updatedReminders,
                            unreadCount = updatedReminders.count { !it.isRead }
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(error = result.message)
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            }
        }
    }

    /**
     * 批量标记所有已读
     */
    fun markAllAsRead() {
        viewModelScope.launch {
            reminderRepository.markAllAsRead().collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val updatedReminders = _uiState.value.reminders.map { it.copy(isRead = true) }
                        _uiState.value = _uiState.value.copy(
                            reminders = updatedReminders,
                            unreadCount = 0
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(error = result.message)
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            }
        }
    }

    /**
     * 延迟提醒
     */
    fun snooze(reminderId: Long, minutes: Int) {
        viewModelScope.launch {
            reminderRepository.snooze(reminderId, minutes).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        // 更新本地状态
                        val updatedReminders = _uiState.value.reminders.map { reminder ->
                            if (reminder.id == reminderId) {
                                result.data
                            } else {
                                reminder
                            }
                        }
                        _uiState.value = _uiState.value.copy(reminders = updatedReminders)
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(error = result.message)
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            }
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
