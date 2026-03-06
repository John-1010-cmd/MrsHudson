package com.mrshudson.android.domain.model

/**
 * 天气领域模型
 * 用于 UI 层展示天气信息
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
data class Weather(
    val city: String,
    val temperature: String,
    val weather: String,
    val humidity: String,
    val windDirection: String,
    val windPower: String,
    val reportTime: String,
    val forecast: List<ForecastDay>
)

/**
 * 预报天数领域模型
 * 用于 UI 层显示天气预报
 *
 * @property date 日期
 * @property weather 天气描述
 * @property tempMin 最低温度
 * @property tempMax 最高温度
 */
data class ForecastDay(
    val date: String,
    val weather: String,
    val tempMin: String,
    val tempMax: String
)

/**
 * 天气图标映射
 * 根据天气描述返回对应的图标资源
 */
fun getWeatherIcon(weather: String): String {
    return when {
        weather.contains("晴") -> "sunny"
        weather.contains("多云") -> "cloudy"
        weather.contains("阴") -> "overcast"
        weather.contains("雨") -> "rainy"
        weather.contains("雪") -> "snowy"
        weather.contains("雾") || weather.contains("霾") -> "foggy"
        weather.contains("雷") -> "thunderstorm"
        weather.contains("风") -> "windy"
        else -> "sunny"
    }
}
