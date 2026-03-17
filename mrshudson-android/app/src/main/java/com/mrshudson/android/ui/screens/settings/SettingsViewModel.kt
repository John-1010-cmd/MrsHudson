package com.mrshudson.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.local.datastore.SettingsDataStore
import com.mrshudson.android.data.remote.ServerUrlManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面的 UI 状态
 */
data class SettingsUiState(
    val serverUrl: String = ""
)

/**
 * 设置页面的 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val serverUrl = settingsDataStore.getServerUrl().first()
            _uiState.value = SettingsUiState(serverUrl = serverUrl)
        }
    }

    /**
     * 保存服务器地址
     */
    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            try {
                val trimmedUrl = url.trim()
                settingsDataStore.saveServerUrl(trimmedUrl)
                // 同时更新运行时管理器，使其立即生效
                ServerUrlManager.setServerUrl(trimmedUrl)
                _uiState.value = _uiState.value.copy(serverUrl = trimmedUrl)
                _saveResult.value = "保存成功"
            } catch (e: Exception) {
                _saveResult.value = "保存失败: ${e.message}"
            }
        }
    }

    /**
     * 清除服务器地址（使用默认值）
     */
    fun clearServerUrl() {
        viewModelScope.launch {
            try {
                settingsDataStore.clearServerUrl()
                ServerUrlManager.clearServerUrl()
                _uiState.value = _uiState.value.copy(serverUrl = "")
                _saveResult.value = "已使用默认地址"
            } catch (e: Exception) {
                _saveResult.value = "清除失败: ${e.message}"
            }
        }
    }
}
