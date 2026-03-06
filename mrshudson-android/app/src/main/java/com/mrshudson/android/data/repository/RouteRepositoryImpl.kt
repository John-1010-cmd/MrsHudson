package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.RouteApi
import com.mrshudson.android.data.remote.dto.RouteRequestDto
import com.mrshudson.android.domain.model.TravelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 路线仓库实现类
 * 使用 RouteApi 进行网络请求
 */
@Singleton
class RouteRepositoryImpl @Inject constructor(
    private val routeApi: RouteApi
) : RouteRepository, BaseApi {

    override fun planRoute(
        origin: String,
        destination: String,
        mode: TravelMode
    ): Flow<ApiResult<String>> = flow {
        emit(ApiResult.Loading)
        try {
            val request = RouteRequestDto(
                origin = origin,
                destination = destination,
                mode = mode.value
            )
            val response = routeApi.planRoute(request)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val routeResponse = result.data
                    emit(ApiResult.Success(routeResponse.result ?: ""))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "路线规划失败，请检查网络连接"))
        }
    }
}
