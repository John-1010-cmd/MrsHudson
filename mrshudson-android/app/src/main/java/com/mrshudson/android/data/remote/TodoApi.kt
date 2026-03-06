package com.mrshudson.android.data.remote

import com.mrshudson.android.data.remote.dto.CreateTodoRequest
import com.mrshudson.android.data.remote.dto.TodoDto
import com.mrshudson.android.data.remote.dto.UpdateTodoRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 待办事项相关 API 接口
 * 定义待办事项的 CRUD 操作接口
 */
interface TodoApi {

    /**
     * 获取待办事项列表
     * 获取当前用户的待办事项列表，支持按状态筛选
     *
     * @param status 状态筛选（可选）：pending, in_progress, completed
     * @param priority 优先级筛选（可选）：low, medium, high
     * @return 待办事项列表
     */
    @GET("todos")
    suspend fun getTodos(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null
    ): Response<ResultDto<List<TodoDto>>>

    /**
     * 获取单个待办事项详情
     *
     * @param id 待办ID
     * @return 待办事项详情
     */
    @GET("todos/{id}")
    suspend fun getTodoById(
        @Path("id") id: Long
    ): Response<ResultDto<TodoDto>>

    /**
     * 创建待办事项
     * 创建一个新的待办事项
     *
     * @param request 创建待办请求
     * @return 新创建的待办事项信息
     */
    @POST("todos")
    suspend fun createTodo(
        @Body request: CreateTodoRequest
    ): Response<ResultDto<TodoDto>>

    /**
     * 更新待办事项
     * 更新指定的待办事项
     *
     * @param id 待办ID
     * @param request 更新待办请求
     * @return 更新后的待办事项信息
     */
    @PUT("todos/{id}")
    suspend fun updateTodo(
        @Path("id") id: Long,
        @Body request: UpdateTodoRequest
    ): Response<ResultDto<TodoDto>>

    /**
     * 完成待办事项
     * 将指定的待办事项标记为已完成
     *
     * @param id 待办ID
     * @return 完成结果
     */
    @PUT("todos/{id}/complete")
    suspend fun completeTodo(
        @Path("id") id: Long
    ): Response<ResultDto<Unit>>

    /**
     * 删除待办事项
     * 删除指定的待办事项
     *
     * @param id 要删除的待办ID
     * @return 删除结果
     */
    @DELETE("todos/{id}")
    suspend fun deleteTodo(
        @Path("id") id: Long
    ): Response<ResultDto<Unit>>
}
