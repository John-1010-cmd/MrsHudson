package com.mrshudson.android.data.repository

import com.mrshudson.android.data.local.datastore.TokenDataStore
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.AuthApi
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.dto.LoginRequest
import com.mrshudson.android.data.remote.dto.UserDto
import com.mrshudson.android.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证仓库接口
 * 定义用户认证相关的数据操作
 */
interface AuthRepository {

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录结果的 Flow，成功时返回 User
     */
    fun login(username: String, password: String): Flow<ApiResult<User>>

    /**
     * 用户登出
     *
     * @return 登出结果的 Flow
     */
    fun logout(): Flow<ApiResult<Unit>>

    /**
     * 检查用户是否已登录
     *
     * @return 是否已登录的 Flow
     */
    fun isLoggedIn(): Flow<Boolean>

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户的 Flow，未登录时返回 null
     */
    fun getCurrentUser(): Flow<User?>
}

/**
 * 认证仓库实现类
 * 使用 AuthApi 进行网络请求，使用 TokenDataStore 管理 Token
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore
) : AuthRepository, BaseApi {

    override fun login(username: String, password: String): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val request = LoginRequest(username, password)
            val response = authApi.login(request)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    // 保存 Token
                    val loginResponse = result.data
                    tokenDataStore.saveTokens(
                        loginResponse.accessToken,
                        loginResponse.refreshToken
                    )
                    // 登录成功后获取用户信息
                    val userResult = fetchCurrentUser()
                    emit(userResult)
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "登录失败，请检查网络连接"))
        }
    }

    override fun logout(): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = authApi.logout()
            val result = handleResultResponse(response)

            // 无论服务端是否成功，都清除本地 Token
            tokenDataStore.clearTokens()

            emit(result)
        } catch (e: Exception) {
            // 即使网络请求失败，也清除本地 Token
            tokenDataStore.clearTokens()
            emit(ApiResult.Error(-1, e.message ?: "登出失败"))
        }
    }

    override fun isLoggedIn(): Flow<Boolean> {
        return tokenDataStore.isLoggedIn()
    }

    override fun getCurrentUser(): Flow<User?> = flow {
        try {
            val isLoggedIn = tokenDataStore.isLoggedIn()
            isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    val result = fetchCurrentUser()
                    when (result) {
                        is ApiResult.Success -> emit(result.data)
                        is ApiResult.Error -> emit(null)
                        is ApiResult.Loading -> { /* ignore */ }
                    }
                } else {
                    emit(null)
                }
            }
        } catch (e: Exception) {
            emit(null)
        }
    }

    /**
     * 从服务器获取当前用户信息
     *
     * @return 获取用户结果的 ApiResult
     */
    private suspend fun fetchCurrentUser(): ApiResult<User> {
        return try {
            val response = authApi.getCurrentUser()
            val result = handleResultResponse(response)
            when (result) {
                is ApiResult.Success -> {
                    val user = result.data.toDomainModel()
                    ApiResult.Success(user)
                }
                is ApiResult.Error -> result
                is ApiResult.Loading -> ApiResult.Loading
            }
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "获取用户信息失败")
        }
    }

    /**
     * 将 UserDto 转换为领域模型 User
     */
    private fun UserDto.toDomainModel(): User {
        return User(
            id = id,
            username = username
        )
    }
}
