package com.mrshudson.android.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.local.datastore.TokenDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主页面 ViewModel
 * 处理主页面的业务逻辑，如退出登录
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val tokenDataStore: TokenDataStore
) : ViewModel() {

    /**
     * 退出登录
     * 清除本地存储的 token
     */
    fun logout() {
        viewModelScope.launch {
            tokenDataStore.clearTokens()
        }
    }
}
