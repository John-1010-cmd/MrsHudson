package com.mrshudson.android.data.repository

import com.mrshudson.android.data.local.dao.TodoDao
import com.mrshudson.android.data.local.entity.TodoEntity
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.TodoApi
import com.mrshudson.android.data.remote.dto.CompleteTodoRequest
import com.mrshudson.android.data.remote.dto.CreateTodoRequest
import com.mrshudson.android.data.remote.dto.UpdateTodoRequest
import com.mrshudson.android.data.remote.dto.TodoDto
import com.mrshudson.android.domain.model.TodoItem
import com.mrshudson.android.domain.model.TodoStatus
import com.mrshudson.android.domain.model.createTodoItem
import com.mrshudson.android.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待办事项仓库实现类
 * 支持离线缓存：优先从本地读取，网络可用时同步
 */
@Singleton
class TodoRepositoryImpl @Inject constructor(
    private val todoApi: TodoApi,
    private val todoDao: TodoDao,
    private val syncManager: SyncManager
) : TodoRepository, BaseApi {

    override fun getTodos(status: String?, priority: String?): Flow<ApiResult<List<TodoItem>>> = flow {
        emit(ApiResult.Loading)

        // 首先从本地缓存读取
        try {
            val localTodos = when {
                status != null -> todoDao.getByStatus(status)
                else -> todoDao.getAll()
            }

            if (localTodos.isNotEmpty()) {
                emit(ApiResult.Success(localTodos.map { it.toDomainModel() }))
            }
        } catch (e: Exception) {
            // 本地读取失败
        }

        // 检查网络
        val isNetworkAvailable = syncManager.isNetworkAvailable.first()

        if (isNetworkAvailable) {
            try {
                val response = todoApi.getTodos(status, priority)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val todoDtos = result.data
                        val todos = todoDtos.map { it.toDomainModel() }

                        // 更新本地缓存
                        todoDao.insertAll(todoDtos.map { it.toEntity() })

                        emit(ApiResult.Success(todos))
                    }
                    is ApiResult.Error -> {
                        val localTodos = when {
                            status != null -> todoDao.getByStatus(status)
                            else -> todoDao.getAll()
                        }
                        if (localTodos.isEmpty()) {
                            emit(ApiResult.Error(result.code, result.message))
                        }
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                val localTodos = when {
                    status != null -> todoDao.getByStatus(status)
                    else -> todoDao.getAll()
                }
                if (localTodos.isEmpty()) {
                    emit(ApiResult.Error(-1, e.message ?: "获取待办事项失败"))
                }
            }
        }
    }

    override fun getTodoById(id: Long): Flow<ApiResult<TodoItem>> = flow {
        emit(ApiResult.Loading)

        // 从本地读取
        try {
            val localTodo = todoDao.getById(id)
            if (localTodo != null) {
                emit(ApiResult.Success(localTodo.toDomainModel()))
            }
        } catch (e: Exception) {
            // 本地失败
        }

        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = todoApi.getTodoById(id)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val todoDto = result.data
                        val todo = todoDto.toDomainModel()

                        // 更新本地
                        todoDao.insert(todoDto.toEntity())

                        emit(ApiResult.Success(todo))
                    }
                    is ApiResult.Error -> {
                        val localTodo = todoDao.getById(id)
                        if (localTodo == null) {
                            emit(ApiResult.Error(result.code, result.message))
                        }
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                val localTodo = todoDao.getById(id)
                if (localTodo == null) {
                    emit(ApiResult.Error(-1, e.message ?: "获取待办事项详情失败"))
                }
            }
        }
    }

    override fun createTodo(
        title: String,
        description: String?,
        priority: String?,
        dueDate: String?
    ): Flow<ApiResult<TodoItem>> = flow {
        emit(ApiResult.Loading)

        val request = CreateTodoRequest(
            title = title,
            description = description,
            priority = priority ?: "MEDIUM",
            dueDate = dueDate
        )

        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = todoApi.createTodo(request)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val todoDto = result.data
                        val todo = todoDto.toDomainModel()

                        // 保存到本地
                        todoDao.insert(todoDto.toEntity())

                        emit(ApiResult.Success(todo))
                    }
                    is ApiResult.Error -> {
                        saveOfflineTodo(request, null)
                        emit(ApiResult.Error(result.code, "已保存到离线缓存"))
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                saveOfflineTodo(request, e.message)
                emit(ApiResult.Error(-1, "已保存到离线缓存"))
            }
        } else {
            saveOfflineTodo(request, "离线模式")
            emit(ApiResult.Error(-1, "已保存到离线缓存"))
        }
    }

    override fun updateTodo(
        id: Long,
        title: String?,
        description: String?,
        priority: String?,
        status: String?,
        dueDate: String?
    ): Flow<ApiResult<TodoItem>> = flow {
        emit(ApiResult.Loading)

        val request = UpdateTodoRequest(
            title = title,
            description = description,
            priority = priority,
            status = status,
            dueDate = dueDate
        )

        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = todoApi.updateTodo(id, request)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        val todoDto = result.data
                        val todo = todoDto.toDomainModel()

                        todoDao.insert(todoDto.toEntity())

                        emit(ApiResult.Success(todo))
                    }
                    is ApiResult.Error -> {
                        updateOfflineTodo(id, request)
                        emit(ApiResult.Error(result.code, "已保存到离线缓存"))
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                updateOfflineTodo(id, request)
                emit(ApiResult.Error(-1, "已保存到离线缓存"))
            }
        } else {
            updateOfflineTodo(id, request)
            emit(ApiResult.Error(-1, "已保存到离线缓存"))
        }
    }

    override fun completeTodo(id: Long): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)

        val request = CompleteTodoRequest(completed = true)

        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = todoApi.completeTodo(id, request)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        // 使用服务器返回的数据更新本地状态
                        val todoDto = result.data
                        todoDao.insert(todoDto.toEntity())
                        emit(ApiResult.Success(Unit))
                    }
                    is ApiResult.Error -> {
                        // 离线完成
                        todoDao.updateStatus(id, "COMPLETED", java.time.LocalDateTime.now().toString())
                        emit(ApiResult.Success(Unit))
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                todoDao.updateStatus(id, "COMPLETED", java.time.LocalDateTime.now().toString())
                emit(ApiResult.Success(Unit))
            }
        } else {
            // 离线完成
            todoDao.updateStatus(id, "COMPLETED", java.time.LocalDateTime.now().toString())
            emit(ApiResult.Success(Unit))
        }
    }

    override fun deleteTodo(id: Long): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)

        if (syncManager.isNetworkAvailable.first()) {
            try {
                val response = todoApi.deleteTodo(id)
                val result = handleResultResponse(response)

                when (result) {
                    is ApiResult.Success -> {
                        todoDao.delete(id)
                        emit(ApiResult.Success(Unit))
                    }
                    is ApiResult.Error -> {
                        todoDao.delete(id)
                        emit(ApiResult.Success(Unit))
                    }
                    is ApiResult.Loading -> { /* ignore */ }
                }
            } catch (e: Exception) {
                todoDao.delete(id)
                emit(ApiResult.Success(Unit))
            }
        } else {
            todoDao.delete(id)
            emit(ApiResult.Success(Unit))
        }
    }

    /**
     * 离线保存待办
     */
    private suspend fun saveOfflineTodo(request: CreateTodoRequest, errorMsg: String?) {
        val tempId = System.currentTimeMillis()
        val entity = TodoEntity(
            id = tempId,
            userId = 0,
            title = request.title,
            description = request.description,
            priority = request.priority ?: "MEDIUM",
            status = "PENDING",
            dueDate = request.dueDate,
            completedAt = null,
            createdAt = null,
            updatedAt = null
        )
        todoDao.insert(entity)
    }

    /**
     * 离线更新待办
     */
    private suspend fun updateOfflineTodo(id: Long, request: UpdateTodoRequest) {
        val existing = todoDao.getById(id) ?: return
        val updated = existing.copy(
            title = request.title ?: existing.title,
            description = request.description ?: existing.description,
            priority = request.priority ?: existing.priority,
            status = request.status ?: existing.status,
            dueDate = request.dueDate ?: existing.dueDate
        )
        todoDao.insert(updated)
    }

    /**
     * 将 TodoDto 转换为领域模型
     */
    private fun TodoDto.toDomainModel(): TodoItem {
        return createTodoItem(
            id = id,
            title = title,
            description = description,
            priority = priority,
            status = status,
            dueDate = dueDate,
            completedAt = completedAt,
            createdAt = createdAt
        )
    }

    /**
     * 将 TodoDto 转换为实体
     */
    private fun TodoDto.toEntity(): TodoEntity {
        return TodoEntity(
            id = id,
            userId = 0,
            title = title,
            description = description,
            priority = priority ?: "MEDIUM",
            status = status ?: "PENDING",
            dueDate = dueDate,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = null
        )
    }

    /**
     * 将实体转换为领域模型
     */
    private fun TodoEntity.toDomainModel(): TodoItem {
        return createTodoItem(
            id = id,
            title = title,
            description = description,
            priority = priority,
            status = status,
            dueDate = dueDate,
            completedAt = completedAt,
            createdAt = createdAt
        )
    }
}
