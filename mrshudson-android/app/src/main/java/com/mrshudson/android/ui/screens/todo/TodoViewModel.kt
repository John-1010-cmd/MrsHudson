package com.mrshudson.android.ui.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.TodoRepository
import com.mrshudson.android.domain.model.TodoItem
import com.mrshudson.android.domain.model.TodoPriority
import com.mrshudson.android.domain.model.TodoStatus
import com.mrshudson.android.sync.SyncManager
import com.mrshudson.android.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 待办事项页面 ViewModel
 * 管理待办事项数据和业务逻辑
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    // 待办事项列表
    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    // 筛选状态
    private val _filterStatus = MutableStateFlow<String?>(null)
    val filterStatus: StateFlow<String?> = _filterStatus.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 是否显示创建/编辑对话框
    private val _showTodoDialog = MutableStateFlow(false)
    val showTodoDialog: StateFlow<Boolean> = _showTodoDialog.asStateFlow()

    // 当前编辑的待办（null 表示创建新待办）
    private val _editingTodo = MutableStateFlow<TodoItem?>(null)
    val editingTodo: StateFlow<TodoItem?> = _editingTodo.asStateFlow()

    // 网络是否可用
    val isNetworkAvailable: StateFlow<Boolean> = syncManager.isNetworkAvailable

    // 同步状态
    val syncState: StateFlow<SyncState> = syncManager.syncState

    init {
        loadTodos()
        // 注册网络状态回调
        syncManager.registerNetworkCallback()
    }

    /**
     * 加载待办事项列表
     */
    fun loadTodos() {
        viewModelScope.launch {
            todoRepository.getTodos(_filterStatus.value, null).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                        _errorMessage.value = null
                    }
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _todos.value = result.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        // 离线模式下可能已有缓存数据
                        if (_todos.value.isEmpty()) {
                            _errorMessage.value = result.message
                        }
                    }
                }
            }
        }
    }

    /**
     * 按状态筛选
     */
    fun filterByStatus(status: String?) {
        _filterStatus.value = status
        loadTodos()
    }

    /**
     * 显示创建待办对话框
     */
    fun showCreateTodoDialog() {
        _editingTodo.value = null
        _showTodoDialog.value = true
    }

    /**
     * 显示编辑待办对话框
     */
    fun showEditTodoDialog(todo: TodoItem) {
        _editingTodo.value = todo
        _showTodoDialog.value = true
    }

    /**
     * 关闭待办对话框
     */
    fun dismissTodoDialog() {
        _showTodoDialog.value = false
        _editingTodo.value = null
    }

    /**
     * 创建或更新待办
     */
    fun saveTodo(
        title: String,
        description: String?,
        priority: String?,
        dueDate: LocalDateTime?
    ) {
        val editing = _editingTodo.value
        val dueDateStr = dueDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        viewModelScope.launch {
            val flow = if (editing != null) {
                todoRepository.updateTodo(
                    id = editing.id,
                    title = title,
                    description = description,
                    priority = priority,
                    status = editing.status.toApiString(),
                    dueDate = dueDateStr
                )
            } else {
                todoRepository.createTodo(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDateStr
                )
            }

            flow.collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                    }
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        dismissTodoDialog()
                        loadTodos()
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = result.message
                        // 刷新列表以显示缓存数据
                        loadTodos()
                    }
                }
            }
        }
    }

    /**
     * 标记待办为已完成
     */
    fun completeTodo(todoId: Long) {
        viewModelScope.launch {
            todoRepository.completeTodo(todoId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        // 不显示加载状态，避免影响用户体验
                    }
                    is ApiResult.Success -> {
                        loadTodos()
                    }
                    is ApiResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            }
        }
    }

    /**
     * 标记待办为未完成
     */
    fun uncompleteTodo(todoId: Long) {
        viewModelScope.launch {
            todoRepository.updateTodo(
                id = todoId,
                title = null,
                description = null,
                priority = null,
                status = TodoStatus.PENDING.toApiString(),
                dueDate = null
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {}
                    is ApiResult.Success -> {
                        loadTodos()
                    }
                    is ApiResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            }
        }
    }

    /**
     * 删除待办
     */
    fun deleteTodo(todoId: Long) {
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                    }
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        loadTodos()
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
     * 获取待办统计信息
     */
    fun getTodoStats(): TodoStats {
        val allTodos = _todos.value
        return TodoStats(
            total = allTodos.size,
            pending = allTodos.count { it.status == TodoStatus.PENDING },
            inProgress = allTodos.count { it.status == TodoStatus.IN_PROGRESS },
            completed = allTodos.count { it.status == TodoStatus.COMPLETED }
        )
    }

    /**
     * 获取筛选后的待办列表
     */
    fun getFilteredTodos(): List<TodoItem> {
        return when (_filterStatus.value) {
            "pending" -> _todos.value.filter { it.status == TodoStatus.PENDING }
            "in_progress" -> _todos.value.filter { it.status == TodoStatus.IN_PROGRESS }
            "completed" -> _todos.value.filter { it.status == TodoStatus.COMPLETED }
            else -> _todos.value
        }
    }
}

/**
 * 待办统计信息
 */
data class TodoStats(
    val total: Int,
    val pending: Int,
    val inProgress: Int,
    val completed: Int
)
