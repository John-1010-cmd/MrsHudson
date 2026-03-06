package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.domain.model.Weather
import kotlinx.coroutines.flow.Flow

/**
 * 天气仓库接口
 * 定义天气查询等数据操作
 */
interface WeatherRepository {

    /**
     * 获取当前天气
     * 根据城市名称获取实时天气信息
     *
     * @param city 城市名称
     * @return 当前天气结果的 Flow
     */
    fun getCurrentWeather(city: String): Flow<ApiResult<Weather>>

    /**
     * 获取天气预报
     * 根据城市名称获取天气预报，包含当前天气和未来预报
     *
     * @param city 城市名称
     * @param days 预报天数，默认7天
     * @return 天气预报结果的 Flow
     */
    fun getWeatherForecast(city: String, days: Int = 7): Flow<ApiResult<Weather>>
}
