package com.mrshudson.android.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.repository.AuthRepository
import com.mrshudson.android.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录状态密封类
 * 表示登录操作的不同状态
 */
sealed class LoginUiState {
    /**
     * 初始状态/空闲状态
     */
    data object Idle : LoginUiState()

    /**
     * 登录中状态
     */
    data object Loading : LoginUiState()

    /**
     * 登录成功状态
     *
     * @property user 登录成功的用户信息
     */
    data class Success(val user: User) : LoginUiState()

    /**
     * 登录失败状态
     *
     * @property message 错误信息
     */
    data class Error(val message: String) : LoginUiState()
}

/**
 * 登录事件
 * 用于一次性事件，如显示 Snackbar
 */
sealed class LoginEvent {
    /**
     * 显示错误提示
     */
    data class ShowError(val message: String) : LoginEvent()

    /**
     * 导航到主页面
     */
    data object NavigateToMain : LoginEvent()
}

/**
 * 登录 ViewModel
 * 管理登录页面的业务逻辑和状态
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    /**
     * 登录 UI 状态
     */
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * 登录事件流
     */
    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    /**
     * 用户名输入
     */
    private val _username = MutableStateFlow("admin")
    val username: StateFlow<String> = _username.asStateFlow()

    /**
     * 密码输入
     */
    private val _password = MutableStateFlow("admin")
    val password: StateFlow<String> = _password.asStateFlow()

    /**
     * 密码可见性
     */
    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    /**
     * 更新用户名
     *
     * @param value 新的用户名
     */
    fun onUsernameChange(value: String) {
        _username.value = value
    }

    /**
     * 更新密码
     *
     * @param value 新的密码
     */
    fun onPasswordChange(value: String) {
        _password.value = value
    }

    /**
     * 切换密码可见性
     */
    fun togglePasswordVisibility() {
        _isPasswordVisible.update { !it }
    }

    /**
     * 检查登录状态
     * 用于自动登录检查
     */
    val isLoggedIn = authRepository.isLoggedIn()

    /**
     * 执行登录
     * 验证输入并调用仓库进行登录
     */
    fun login() {
        val currentUsername = _username.value.trim()
        val currentPassword = _password.value

        // 验证输入
        if (currentUsername.isEmpty()) {
            viewModelScope.launch {
                _events.emit(LoginEvent.ShowError("请输入用户名"))
            }
            return
        }

        if (currentPassword.isEmpty()) {
            viewModelScope.launch {
                _events.emit(LoginEvent.ShowError("请输入密码"))
            }
            return
        }

        // 执行登录
        viewModelScope.launch {
            authRepository.login(currentUsername, currentPassword)
                .collect { result ->
                    when (result) {
                        is com.mrshudson.android.data.remote.ApiResult.Loading -> {
                            _uiState.value = LoginUiState.Loading
                        }
                        is com.mrshudson.android.data.remote.ApiResult.Success -> {
                            _uiState.value = LoginUiState.Success(result.data)
                            _events.emit(LoginEvent.NavigateToMain)
                        }
                        is com.mrshudson.android.data.remote.ApiResult.Error -> {
                            val errorMessage = when (result.code) {
                                401 -> "用户名或密码错误"
                                403 -> "账号已被禁用"
                                -1 -> result.message
                                else -> "登录失败：${result.message}"
                            }
                            _uiState.value = LoginUiState.Error(errorMessage)
                            _events.emit(LoginEvent.ShowError(errorMessage))
                        }
                    }
                }
        }
    }

    /**
     * 重置状态
     * 在导航后调用，避免重复触发
     */
    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
