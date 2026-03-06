package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.PushApi
import com.mrshudson.android.data.remote.RegisterDeviceRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 推送仓库实现类
 * 使用 PushApi 进行网络请求
 */
@Singleton
class PushRepositoryImpl @Inject constructor(
    private val pushApi: PushApi
) : PushRepository, BaseApi {

    companion object {
        private const val PLATFORM_ANDROID = "android"
    }

    override fun registerDevice(deviceToken: String): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        try {
            val request = RegisterDeviceRequest(
                deviceToken = deviceToken,
                platform = PLATFORM_ANDROID
            )
            val response = pushApi.registerDevice(request)

            if (response.isSuccessful) {
                val result = response.body()
                if (result != null && result.code == 200) {
                    emit(ApiResult.Success(Unit))
                } else {
                    emit(ApiResult.Error(result?.code ?: -1, result?.message ?: "注册失败"))
                }
            } else {
                emit(ApiResult.Error(response.code(), "请求失败"))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "注册设备失败"))
        }
    }

    override fun unregisterDevice(): Flow<ApiResult<Unit>> = flow {
        // TODO: 实现取消注册功能，如果后端有对应接口
        emit(ApiResult.Success(Unit))
    }
}
