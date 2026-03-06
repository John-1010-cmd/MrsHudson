package com.mrshudson.android.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.CalendarRepository
import com.mrshudson.android.domain.model.CalendarEvent
import com.mrshudson.android.sync.SyncManager
import com.mrshudson.android.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 日历页面 ViewModel
 * 管理日历事件数据和业务逻辑
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    // 当前显示的月份
    private val _currentMonth = MutableStateFlow(LocalDate.now())
    val currentMonth: StateFlow<LocalDate> = _currentMonth.asStateFlow()

    // 选中的日期
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    // 日历事件列表
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    // 选中的日期的事件列表
    private val _selectedDateEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val selectedDateEvents: StateFlow<List<CalendarEvent>> = _selectedDateEvents.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 是否显示创建/编辑对话框
    private val _showEventDialog = MutableStateFlow(false)
    val showEventDialog: StateFlow<Boolean> = _showEventDialog.asStateFlow()

    // 当前编辑的事件（null 表示创建新事件）
    private val _editingEvent = MutableStateFlow<CalendarEvent?>(null)
    val editingEvent: StateFlow<CalendarEvent?> = _editingEvent.asStateFlow()

    // 网络是否可用
    val isNetworkAvailable: StateFlow<Boolean> = syncManager.isNetworkAvailable

    // 同步状态
    val syncState: StateFlow<SyncState> = syncManager.syncState

    init {
        // 初始化时加载当月事件
        loadEventsForMonth(_currentMonth.value)

        // 注册网络状态回调
        syncManager.registerNetworkCallback()
    }

    /**
     * 加载指定月份的事件
     */
    fun loadEventsForMonth(date: LocalDate) {
        val startDate = date.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endDate = date.withDayOfMonth(date.lengthOfMonth()).format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewModelScope.launch {
            calendarRepository.getEvents(startDate, endDate).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                        _errorMessage.value = null
                    }
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _events.value = result.data
                        // 更新选中日期的事件
                        _selectedDate.value?.let { updateSelectedDateEvents(it) }
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        // 离线模式下可能已有缓存数据，不一定显示错误
                        if (_events.value.isEmpty()) {
                            _errorMessage.value = result.message
                        }
                    }
                }
            }
        }
    }

    /**
     * 切换到上个月
     */
    fun previousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
        loadEventsForMonth(_currentMonth.value)
    }

    /**
     * 切换到下个月
     */
    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
        loadEventsForMonth(_currentMonth.value)
    }

    /**
     * 选择日期
     */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        updateSelectedDateEvents(date)
    }

    /**
     * 更新选中日期的事件列表
     */
    private fun updateSelectedDateEvents(date: LocalDate) {
        _selectedDateEvents.value = _events.value.filter { event ->
            event.isOnDate(date.year, date.monthValue, date.dayOfMonth)
        }
    }

    /**
     * 显示创建事件对话框
     */
    fun showCreateEventDialog() {
        _editingEvent.value = null
        _showEventDialog.value = true
    }

    /**
     * 显示编辑事件对话框
     */
    fun showEditEventDialog(event: CalendarEvent) {
        _editingEvent.value = event
        _showEventDialog.value = true
    }

    /**
     * 关闭事件对话框
     */
    fun dismissEventDialog() {
        _showEventDialog.value = false
        _editingEvent.value = null
    }

    /**
     * 创建或更新事件
     */
    fun saveEvent(
        title: String,
        description: String?,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        location: String?,
        category: String?
    ) {
        val editing = _editingEvent.value
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val startTimeStr = startTime.format(dateFormatter)
        val endTimeStr = endTime.format(dateFormatter)

        viewModelScope.launch {
            val flow = if (editing != null) {
                calendarRepository.updateEvent(
                    id = editing.id,
                    title = title,
                    description = description,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    location = location,
                    category = category,
                    reminderMinutes = editing.reminderMinutes
                )
            } else {
                calendarRepository.createEvent(
                    title = title,
                    description = description,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    location = location,
                    category = category,
                    reminderMinutes = 15
                )
            }

            flow.collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                    }
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        dismissEventDialog()
                        loadEventsForMonth(_currentMonth.value)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        // 显示保存到缓存的提示
                        _errorMessage.value = result.message
                        // 刷新列表以显示缓存的数据
                        loadEventsForMonth(_currentMonth.value)
                    }
                }
            }
        }
    }

    /**
     * 删除事件
     */
    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            calendarRepository.deleteEvent(eventId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                    }
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        loadEventsForMonth(_currentMonth.value)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = result.message
                    }
                }
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 获取指定日期的事件
     */
    fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
        return _events.value.filter { event ->
            event.isOnDate(date.year, date.monthValue, date.dayOfMonth)
        }
    }

    /**
     * 检查指定日期是否有事件
     */
    fun hasEventsOnDate(date: LocalDate): Boolean {
        return getEventsForDate(date).isNotEmpty()
    }

    /**
     * 生成当月天数列表
     */
    fun getDaysInMonth(): List<LocalDate?> {
        val currentDate = _currentMonth.value
        val firstDayOfMonth = currentDate.withDayOfMonth(1)
        val daysInMonth = currentDate.lengthOfMonth()

        // 获取第一天是星期几 (1 = Monday, 7 = Sunday)
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value

        // 生成日期列表，前面补 null
        val days = mutableListOf<LocalDate?>()
        repeat(firstDayOfWeek - 1) {
            days.add(null)
        }

        // 添加当月天数
        for (day in 1..daysInMonth) {
            days.add(currentDate.withDayOfMonth(day))
        }

        return days
    }
}
