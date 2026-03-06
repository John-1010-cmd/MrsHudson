package com.mrshudson.android.ui.screens.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.RouteRepository
import com.mrshudson.android.domain.model.TravelMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 路线规划页面的 ViewModel
 * 管理路线数据的状态和业务逻辑
 */
@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeRepository: RouteRepository
) : ViewModel() {

    /**
     * 路线规划结果状态
     */
    private val _routeState = MutableStateFlow<ApiResult<String>>(ApiResult.Loading)
    val routeState: StateFlow<ApiResult<String>> = _routeState.asStateFlow()

    /**
     * 起点输入
     */
    private val _origin = MutableStateFlow("")
    val origin: StateFlow<String> = _origin.asStateFlow()

    /**
     * 终点输入
     */
    private val _destination = MutableStateFlow("")
    val destination: StateFlow<String> = _destination.asStateFlow()

    /**
     * 当前选择的出行方式
     */
    private val _selectedMode = MutableStateFlow(TravelMode.DRIVING)
    val selectedMode: StateFlow<TravelMode> = _selectedMode.asStateFlow()

    /**
     * 出行方式列表
     */
    val travelModes = TravelMode.entries

    /**
     * 更新起点输入
     */
    fun updateOrigin(input: String) {
        _origin.value = input
    }

    /**
     * 更新终点输入
     */
    fun updateDestination(input: String) {
        _destination.value = input
    }

    /**
     * 选择出行方式
     */
    fun selectTravelMode(mode: TravelMode) {
        _selectedMode.value = mode
    }

    /**
     * 交换起点和终点
     */
    fun swapLocations() {
        val temp = _origin.value
        _origin.value = _destination.value
        _destination.value = temp
    }

    /**
     * 执行路线规划
     * 验证输入后调用仓库方法进行路线规划
     */
    fun planRoute() {
        val originText = _origin.value.trim()
        val destinationText = _destination.value.trim()

        // 输入验证
        if (originText.isEmpty()) {
            _routeState.value = ApiResult.Error(-1, "请输入起点地址")
            return
        }
        if (destinationText.isEmpty()) {
            _routeState.value = ApiResult.Error(-1, "请输入终点地址")
            return
        }

        viewModelScope.launch {
            routeRepository.planRoute(
                origin = originText,
                destination = destinationText,
                mode = _selectedMode.value
            ).collect { result ->
                _routeState.value = result
            }
        }
    }

    /**
     * 清除路线结果
     */
    fun clearRoute() {
        _routeState.value = ApiResult.Loading
    }

    /**
     * 重试上一次的路线规划
     */
    fun retry() {
        planRoute()
    }

    /**
     * 重置所有输入
     */
    fun reset() {
        _origin.value = ""
        _destination.value = ""
        _selectedMode.value = TravelMode.DRIVING
        _routeState.value = ApiResult.Loading
    }
}
