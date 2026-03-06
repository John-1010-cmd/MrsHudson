package com.mrshudson.android.data.remote.dto

/**
 * 路线规划请求数据传输对象
 * 对应后端 RouteRequest 结构
 *
 * @property origin 起点地址
 * @property destination 终点地址
 * @property mode 出行方式：walking(步行)、driving(驾车)、transit(公交)
 */
data class RouteRequestDto(
    val origin: String,
    val destination: String,
    val mode: String
)

/**
 * 路线规划响应数据传输对象
 * 对应后端 RouteResponse 结构
 *
 * @property result 路线规划结果文本
 */
data class RouteResponseDto(
    val result: String?
)
