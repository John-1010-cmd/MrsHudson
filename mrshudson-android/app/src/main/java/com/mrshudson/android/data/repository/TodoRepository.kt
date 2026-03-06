package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.domain.model.TodoItem
import kotlinx.coroutines.flow.Flow

/**
 * 待办事项仓库接口
 * 定义待办事项的数据操作
 */
interface TodoRepository {

    /**
     * 获取待办事项列表
     * 获取当前用户的待办事项列表，支持按状态筛选
     *
     * @param status 状态筛选（可选）：pending, in_progress, completed
     * @param priority 优先级筛选（可选）：low, medium, high
     * @return 待办事项列表结果的 Flow
     */
    fun getTodos(status: String?, priority: String?): Flow<ApiResult<List<TodoItem>>>

    /**
     * 获取单个待办事项详情
     *
     * @param id 待办ID
     * @return 待办事项详情结果的 Flow
     */
    fun getTodoById(id: Long): Flow<ApiResult<TodoItem>>

    /**
     * 创建待办事项
     * 创建一个新的待办事项
     *
     * @param title 待办标题
     * @param description 待办描述
     * @param priority 优先级
     * @param dueDate 截止日期
     * @return 创建结果的 Flow
     */
    fun createTodo(
        title: String,
        description: String?,
        priority: String?,
        dueDate: String?
    ): Flow<ApiResult<TodoItem>>

    /**
     * 更新待办事项
     * 更新指定的待办事项
     *
     * @param id 待办ID
     * @param title 待办标题
     * @param description 待办描述
     * @param priority 优先级
     * @param status 状态
     * @param dueDate 截止日期
     * @return 更新结果的 Flow
     */
    fun updateTodo(
        id: Long,
        title: String?,
        description: String?,
        priority: String?,
        status: String?,
        dueDate: String?
    ): Flow<ApiResult<TodoItem>>

    /**
     * 完成待办事项
     * 将指定的待办事项标记为已完成
     *
     * @param id 待办ID
     * @return 完成结果
     */
    fun completeTodo(id: Long): Flow<ApiResult<Unit>>

    /**
     * 删除待办事项
     * 删除指定的待办事项
     *
     * @param id 要删除的待办ID
     * @return 删除结果
     */
    fun deleteTodo(id: Long): Flow<ApiResult<Unit>>
}
