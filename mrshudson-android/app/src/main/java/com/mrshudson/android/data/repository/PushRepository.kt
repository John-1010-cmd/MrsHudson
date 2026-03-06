package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import kotlinx.coroutines.flow.Flow

/**
 * 推送仓库接口
 * 定义设备推送相关的数据操作
 */
interface PushRepository {

    /**
     * 注册设备 Token 到服务器
     *
     * @param deviceToken 设备的 FCM Token
     * @return 注册结果的 Flow
     */
    fun registerDevice(deviceToken: String): Flow<ApiResult<Unit>>

    /**
     * 取消注册设备
     *
     * @return 取消注册结果的 Flow
     */
    fun unregisterDevice(): Flow<ApiResult<Unit>>
}
