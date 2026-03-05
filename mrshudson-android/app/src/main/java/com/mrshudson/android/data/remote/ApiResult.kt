package com.mrshudson.android.data.remote

/**
 * 网络请求结果密封类
 * 封装了网络请求的各种状态，便于 UI 层统一处理
 *
 * @param T 请求成功时返回的数据类型
 */
sealed class ApiResult<out T> {

    /**
     * 请求成功状态
     *
     * @property data 请求返回的数据
     */
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * 请求错误状态
     *
     * @property code 错误码（HTTP 状态码或业务错误码）
     * @property message 错误信息描述
     */
    data class Error(
        val code: Int,
        val message: String
    ) : ApiResult<Nothing>()

    /**
     * 请求加载中状态
     * 用于显示加载动画或骨架屏
     */
    data object Loading : ApiResult<Nothing>()

    companion object {
        /**
         * 快速创建 Success 状态的辅助方法
         */
        fun <T> success(data: T): ApiResult<T> = Success(data)

        /**
         * 快速创建 Error 状态的辅助方法
         */
        fun error(code: Int, message: String): ApiResult<Nothing> = Error(code, message)

        /**
         * 快速创建 Loading 状态的辅助方法
         */
        fun loading(): ApiResult<Nothing> = Loading
    }
}

/**
 * 检查请求是否成功
 */
fun <T> ApiResult<T>.isSuccess(): Boolean = this is ApiResult.Success

/**
 * 检查请求是否失败
 */
fun <T> ApiResult<T>.isError(): Boolean = this is ApiResult.Error

/**
 * 检查是否处于加载中状态
 */
fun <T> ApiResult<T>.isLoading(): Boolean = this is ApiResult.Loading

/**
 * 获取成功时的数据，如果失败则返回 null
 */
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

/**
 * 获取成功时的数据，如果失败则返回默认值
 *
 * @param default 失败时返回的默认值
 */
fun <T> ApiResult<T>.getOrDefault(default: @UnsafeVariance T): T = getOrNull() ?: default

/**
 * 如果请求成功，对数据执行指定操作
 *
 * @param action 成功时要执行的操作
 * @return 返回当前 ApiResult 实例，支持链式调用
 */
inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) {
        action(data)
    }
    return this
}

/**
 * 如果请求失败，执行指定操作
 *
 * @param action 失败时要执行的操作
 * @return 返回当前 ApiResult 实例，支持链式调用
 */
inline fun <T> ApiResult<T>.onError(action: (code: Int, message: String) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) {
        action(code, message)
    }
    return this
}

/**
 * 如果处于加载中状态，执行指定操作
 *
 * @param action 加载时要执行的操作
 * @return 返回当前 ApiResult 实例，支持链式调用
 */
inline fun <T> ApiResult<T>.onLoading(action: () -> Unit): ApiResult<T> {
    if (this is ApiResult.Loading) {
        action()
    }
    return this
}

/**
 * 将当前结果转换为另一种类型
 *
 * @param transform 转换函数
 * @return 转换后的 ApiResult
 */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> {
    return when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Error -> this
        is ApiResult.Loading -> this
    }
}
