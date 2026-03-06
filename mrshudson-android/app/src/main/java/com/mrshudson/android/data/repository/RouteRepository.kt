package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.domain.model.RoutePlanRequest
import com.mrshudson.android.domain.model.TravelMode
import kotlinx.coroutines.flow.Flow

/**
 * 路线仓库接口
 * 定义路线规划等数据操作
 */
interface RouteRepository {

    /**
     * 规划出行路线
     * 根据起点、终点和出行方式规划路线
     *
     * @param origin 起点地址
     * @param destination 终点地址
     * @param mode 出行方式
     * @return 路线规划结果的 Flow
     */
    fun planRoute(
        origin: String,
        destination: String,
        mode: TravelMode
    ): Flow<ApiResult<String>>
}
