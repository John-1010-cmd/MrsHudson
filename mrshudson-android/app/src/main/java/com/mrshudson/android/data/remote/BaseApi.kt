package com.mrshudson.android.data.remote

import retrofit2.Response

/**
 * API 基础接口
 * 定义通用的 API 响应处理方法，所有 API 接口建议继承此接口
 */
interface BaseApi {

    /**
     * 将 Retrofit Response 转换为 ApiResult
     * 统一处理 HTTP 响应状态和数据解析
     *
     * @param T 响应数据类型
     * @param response Retrofit 响应对象
     * @return 封装后的 ApiResult
     */
    fun <T> handleResponse(response: Response<T>): ApiResult<T> {
        return if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.success(body)
            } else {
                ApiResult.error(response.code(), "响应体为空")
            }
        } else {
            val errorMessage = response.errorBody()?.string() ?: "请求失败"
            ApiResult.error(response.code(), errorMessage)
        }
    }

    /**
     * 处理后端统一响应格式的结果
     * 适用于后端返回 ResultDto<T> 格式的情况
     *
     * @param T 实际业务数据类型
     * @param response 后端统一响应对象
     * @return 提取业务数据后的 ApiResult
     */
    fun <T> handleResultResponse(response: Response<ResultDto<T>>): ApiResult<T> {
        return if (response.isSuccessful) {
            val result = response.body()
            if (result != null) {
                when (result.code) {
                    200 -> {
                        val data = result.data
                        if (data != null) {
                            ApiResult.success(data)
                        } else {
                            ApiResult.error(200, "数据为空")
                        }
                    }
                    else -> ApiResult.error(result.code, result.message)
                }
            } else {
                ApiResult.error(response.code(), "响应体为空")
            }
        } else {
            val errorMessage = response.errorBody()?.string() ?: "请求失败"
            ApiResult.error(response.code(), errorMessage)
        }
    }
}

/**
 * 后端统一响应格式数据类
 * 对应后端 Result<T> 结构
 *
 * @param T 实际业务数据类型
 * @property code 业务状态码（200 表示成功）
 * @property data 业务数据
 * @property message 提示信息
 */
data class ResultDto<T>(
    val code: Int,
    val data: T?,
    val message: String
)
