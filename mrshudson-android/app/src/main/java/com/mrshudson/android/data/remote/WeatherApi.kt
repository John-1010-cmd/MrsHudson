package com.mrshudson.android.data.remote

import com.mrshudson.android.data.remote.dto.WeatherDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 天气相关 API 接口
 * 定义获取当前天气和天气预报等接口
 */
interface WeatherApi {

    /**
     * 获取当前天气
     * 根据城市名称获取实时天气信息
     *
     * @param city 城市名称，如"北京"、"上海"
     * @return 当前天气信息
     */
    @GET("weather/current")
    suspend fun getCurrentWeather(
        @Query("city") city: String
    ): Response<ResultDto<WeatherDto>>

    /**
     * 获取天气预报
     * 根据城市名称获取天气预报，包含当前天气和未来预报
     *
     * @param city 城市名称
     * @param days 预报天数，默认3天
     * @return 天气预报信息
     */
    @GET("weather/forecast")
    suspend fun getWeatherForecast(
        @Query("city") city: String,
        @Query("days") days: Int = 3
    ): Response<ResultDto<WeatherDto>>
}
