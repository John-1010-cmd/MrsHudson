package com.mrshudson.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 天气数据传输对象
 * 对应后端 WeatherDTO 结构
 *
 * @property city 城市名称
 * @property temperature 当前温度
 * @property weather 天气描述
 * @property humidity 湿度
 * @property windDirection 风向
 * @property windPower 风力
 * @property reportTime 报告时间
 * @property forecast 预报数据列表
 */
data class WeatherDto(
    val city: String?,
    val temperature: String?,
    val weather: String?,
    val humidity: String?,
    @SerializedName("windDirection")
    val windDirection: String?,
    @SerializedName("windPower")
    val windPower: String?,
    @SerializedName("reportTime")
    val reportTime: String?,
    val forecast: List<ForecastDayDto>?
)

/**
 * 预报天数数据传输对象
 * 对应后端 ForecastDayDTO 结构
 *
 * @property date 日期
 * @property weather 天气描述
 * @property tempMin 最低温度
 * @property tempMax 最高温度
 */
data class ForecastDayDto(
    val date: String?,
    val weather: String?,
    @SerializedName("tempMin")
    val tempMin: String?,
    @SerializedName("tempMax")
    val tempMax: String?
)
