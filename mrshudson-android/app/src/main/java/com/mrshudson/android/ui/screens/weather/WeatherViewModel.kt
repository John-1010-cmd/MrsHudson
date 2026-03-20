package com.mrshudson.android.ui.screens.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.WeatherRepository
import com.mrshudson.android.domain.model.Weather
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 天气页面的 ViewModel
 * 管理天气数据的状态和业务逻辑
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    /**
     * 天气数据状态
     */
    private val _weatherState = MutableStateFlow<ApiResult<Weather>>(ApiResult.Loading)
    val weatherState: StateFlow<ApiResult<Weather>> = _weatherState.asStateFlow()

    /**
     * 当前选择的城市
     */
    private val _currentCity = MutableStateFlow("北京")
    val currentCity: StateFlow<String> = _currentCity.asStateFlow()

    /**
     * 城市输入框文本
     */
    private val _cityInput = MutableStateFlow("")
    val cityInput: StateFlow<String> = _cityInput.asStateFlow()

    /**
     * 常用城市列表
     */
    val popularCities = listOf(
        "北京", "上海", "广州", "深圳",
        "杭州", "南京", "武汉", "成都",
        "重庆", "西安", "天津", "苏州"
    )

    init {
        // 不再自动加载天气，等待用户主动查询
        // 这样可以避免不必要的网络请求和可能的加载缓慢问题
        _weatherState.value = ApiResult.Success(createEmptyWeather())
    }

    /**
     * 创建空的天气数据（用于初始状态）
     */
    private fun createEmptyWeather(): Weather {
        return Weather(
            city = "",
            temperature = "",
            weather = "",
            humidity = "",
            windDirection = "",
            windPower = "",
            reportTime = "",
            forecast = emptyList()
        )
    }

    /**
     * 加载天气数据
     * 使用协程进行异步网络请求
     */
    fun loadWeather(city: String = _currentCity.value) {
        _currentCity.value = city
        viewModelScope.launch {
            weatherRepository.getWeatherForecast(city, 7).collect { result ->
                _weatherState.value = result
            }
        }
    }

    /**
     * 刷新天气数据
     */
    fun refreshWeather() {
        loadWeather(_currentCity.value)
    }

    /**
     * 搜索城市天气
     * 当用户输入城市名称后点击搜索时调用
     */
    fun searchCity(city: String) {
        if (city.isNotBlank()) {
            _cityInput.value = ""
            loadWeather(city.trim())
        }
    }

    /**
     * 更新城市输入框文本
     */
    fun updateCityInput(input: String) {
        _cityInput.value = input
    }

    /**
     * 选择常用城市
     */
    fun selectCity(city: String) {
        loadWeather(city)
    }
}
