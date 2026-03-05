package com.mrshudson.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航项枚举
 * 定义应用主界面的五个主要功能模块
 */
enum class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    CHAT("chat", "对话", Icons.AutoMirrored.Filled.Chat),
    CALENDAR("calendar", "日历", Icons.Default.CalendarToday),
    TODO("todo", "待办", Icons.Default.CheckCircle),
    WEATHER("weather", "天气", Icons.Default.WbSunny),
    ROUTE("route", "路线", Icons.Default.Map);

    companion object {
        /**
         * 获取所有导航项列表
         */
        fun allItems(): List<BottomNavItem> = values().toList()

        /**
         * 根据路由路径查找对应的导航项
         */
        fun fromRoute(route: String?): BottomNavItem {
            return values().find { it.route == route } ?: CHAT
        }
    }
}
