package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.WeatherApi
import com.mrshudson.android.data.remote.dto.WeatherDto
import com.mrshudson.android.domain.model.ForecastDay
import com.mrshudson.android.domain.model.Weather
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 天气仓库实现类
 * 使用 WeatherApi 进行网络请求
 */
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val weatherApi: WeatherApi
) : WeatherRepository, BaseApi {

    override fun getCurrentWeather(city: String): Flow<ApiResult<Weather>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = weatherApi.getCurrentWeather(city)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val weatherDto = result.data
                    val weather = weatherDto.toDomainModel()
                    emit(ApiResult.Success(weather))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "获取天气失败，请检查网络连接"))
        }
    }

    override fun getWeatherForecast(city: String, days: Int): Flow<ApiResult<Weather>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = weatherApi.getWeatherForecast(city, days)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val weatherDto = result.data
                    val weather = weatherDto.toDomainModel()
                    emit(ApiResult.Success(weather))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "获取天气预报失败，请检查网络连接"))
        }
    }

    /**
     * 将 WeatherDto 转换为领域模型 Weather
     */
    private fun WeatherDto.toDomainModel(): Weather {
        return Weather(
            city = city ?: "",
            temperature = temperature ?: "",
            weather = weather ?: "",
            humidity = humidity ?: "",
            windDirection = windDirection ?: "",
            windPower = windPower ?: "",
            reportTime = reportTime ?: "",
            forecast = forecast?.map { it.toDomainModel() } ?: emptyList()
        )
    }

    /**
     * 将 ForecastDayDto 转换为领域模型 ForecastDay
     */
    private fun com.mrshudson.android.data.remote.dto.ForecastDayDto.toDomainModel(): ForecastDay {
        return ForecastDay(
            date = date ?: "",
            weather = weather ?: "",
            tempMin = tempMin ?: "",
            tempMax = tempMax ?: ""
        )
    }
}
