package com.mrshudson.android.data.remote

import com.mrshudson.android.data.remote.dto.RouteRequestDto
import com.mrshudson.android.data.remote.dto.RouteResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 路线规划相关 API 接口
 * 定义路线规划等接口
 */
interface RouteApi {

    /**
     * 规划出行路线
     * 根据起点、终点和出行方式规划路线
     *
     * @param request 路线规划请求，包含起点、终点和出行方式
     * @return 路线规划结果
     */
    @POST("route/plan")
    suspend fun planRoute(
        @Body request: RouteRequestDto
    ): Response<ResultDto<RouteResponseDto>>
}
