package com.mrshudson.android.ui.components.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mrshudson.android.domain.model.AudioPlayState

/**
 * TTS 播放按钮组件
 * 根据播放状态显示不同的按钮：播放、暂停、继续、重试
 *
 * @param playState 当前播放状态
 * @param hasAudio 是否有音频URL
 * @param onPlay 点击播放（从头开始）
 * @param onPause 点击暂停
 * @param onResume 点击继续
 * @param modifier 修饰符
 */
@Composable
fun TtsButton(
    playState: AudioPlayState,
    hasAudio: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (playState == AudioPlayState.PLAYING) 360f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "rotation"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (playState) {
            AudioPlayState.LOADING -> {
                // 加载中显示加载动画
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AudioPlayState.PLAYING -> {
                // 播放中显示暂停按钮
                IconButtonCircle(
                    icon = Icons.Default.Pause,
                    contentDescription = "暂停",
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onPause
                )
                // 显示重新播放按钮
                IconButtonCircle(
                    icon = Icons.Default.Replay,
                    contentDescription = "重新播放",
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onPlay,
                    rotation = rotation
                )
            }

            AudioPlayState.PAUSED -> {
                // 暂停中显示继续按钮和重新播放按钮
                IconButtonCircle(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "继续",
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    iconColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onResume
                )
                IconButtonCircle(
                    icon = Icons.Default.Replay,
                    contentDescription = "重新播放",
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onPlay
                )
            }

            AudioPlayState.IDLE -> {
                // 未播放显示播放按钮（根据是否有音频显示不同图标）
                IconButtonCircle(
                    icon = if (hasAudio) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = if (hasAudio) "播放语音" else "生成语音",
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    iconColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onPlay
                )
            }
        }
    }
}

/**
 * 圆形图标按钮
 */
@Composable
private fun IconButtonCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation)
        )
    }
}
