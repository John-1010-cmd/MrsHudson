package com.mrshudson.android.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步状态
 */
sealed class SyncState {
    /**
     * 空闲状态
     */
    data object Idle : SyncState()

    /**
     * 同步中
     */
    data object Syncing : SyncState()

    /**
     * 同步成功
     */
    data class Success(val timestamp: Long = System.currentTimeMillis()) : SyncState()

    /**
     * 同步失败
     */
    data class Error(val message: String) : SyncState()
}

/**
 * 网络状态管理器
 * 监听网络状态变化，触发同步操作
 *
 * @property context 应用上下文
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    /**
     * 当前网络是否可用
     */
    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    /**
     * 当前同步状态
     */
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * 当前是否正在使用 WiFi
     */
    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    /**
     * 网络状态变化回调
     */
    private var onNetworkRestored: (() -> Unit)? = null

    /**
     * 注册网络状态监听器
     */
    fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkAvailable.value = true
                    // 网络恢复时触发同步
                    onNetworkRestored?.invoke()
                }

                override fun onLost(network: Network) {
                    _isNetworkAvailable.value = false
                    _isWifiConnected.value = false
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    _isWifiConnected.value = networkCapabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    )
                }
            }
        )

        // 初始化网络状态
        checkNetworkState()
    }

    /**
     * 注销网络状态监听器
     */
    fun unregisterNetworkCallback() {
        connectivityManager?.unregisterNetworkCallback(object : ConnectivityManager.NetworkCallback() {})
    }

    /**
     * 检查当前网络状态
     */
    fun checkNetworkState() {
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)

        _isNetworkAvailable.value = capabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) == true

        _isWifiConnected.value = capabilities?.hasTransport(
            NetworkCapabilities.TRANSPORT_WIFI
        ) == true
    }

    /**
     * 设置网络恢复回调
     */
    fun setOnNetworkRestoredCallback(callback: () -> Unit) {
        onNetworkRestored = callback
    }

    /**
     * 更新同步状态
     */
    fun updateSyncState(state: SyncState) {
        _syncState.value = state
    }

    /**
     * 观察网络状态变化
     */
    fun observeNetworkState(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, callback)

        // 发送初始状态
        trySend(_isNetworkAvailable.value)

        awaitClose {
            connectivityManager?.unregisterNetworkCallback(callback)
        }
    }
}
