package com.mrshudson.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mrshudson.android.sync.SyncState

/**
 * 离线状态提示条
 * 显示网络状态、同步进度和同步结果
 *
 * @param isOffline 是否处于离线状态
 * @param syncState 同步状态
 * @param modifier 修饰符
 */
@Composable
fun OfflineBanner(
    isOffline: Boolean,
    syncState: SyncState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOffline || syncState !is SyncState.Idle,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val (backgroundColor, text, icon) = when {
            // 离线状态
            isOffline -> Triple(
                Color(0xFFFF9800), // 橙色
                "当前处于离线模式，数据已缓存",
                Icons.Default.CloudOff
            )
            // 同步中
            syncState is SyncState.Syncing -> Triple(
                Color(0xFF2196F3), // 蓝色
                "正在同步数据...",
                null
            )
            // 同步成功
            syncState is SyncState.Success -> Triple(
                Color(0xFF4CAF50), // 绿色
                "数据同步完成",
                Icons.Default.CheckCircle
            )
            // 同步失败
            syncState is SyncState.Error -> Triple(
                Color(0xFFF44336), // 红色
                "同步失败: ${syncState.message}",
                Icons.Default.CloudOff
            )
            // 空闲状态
            else -> return@AnimatedVisibility
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (syncState) {
                    is SyncState.Syncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = icon!!,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 简化的离线提示组件
 * 仅显示离线状态
 *
 * @param isOffline 是否处于离线状态
 * @param modifier 修饰符
 */
@Composable
fun SimpleOfflineBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOffline,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFF9800))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "当前处于离线模式",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
