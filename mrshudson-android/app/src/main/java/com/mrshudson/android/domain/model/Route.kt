package com.mrshudson.android.domain.model

/**
 * 出行方式枚举
 */
enum class TravelMode(val value: String, val displayName: String) {
    WALKING("walking", "步行"),
    DRIVING("driving", "驾车"),
    TRANSIT("transit", "公交");

    companion object {
        fun fromValue(value: String): TravelMode {
            return entries.find { it.value == value } ?: DRIVING
        }
    }
}

/**
 * 路线规划结果领域模型
 * 用于 UI 层显示路线信息
 *
 * @property origin 起点
 * @property destination 终点
 * @property mode 出行方式
 * @property result 路线详情文本
 */
data class Route(
    val origin: String,
    val destination: String,
    val mode: TravelMode,
    val result: String
)

/**
 * 路线规划请求
 *
 * @property origin 起点地址
 * @property destination 终点地址
 * @property mode 出行方式
 */
data class RoutePlanRequest(
    val origin: String,
    val destination: String,
    val mode: TravelMode
)
