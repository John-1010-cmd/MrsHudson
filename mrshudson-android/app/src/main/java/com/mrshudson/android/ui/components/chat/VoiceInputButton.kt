package com.mrshudson.android.ui.components.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 语音输入按钮状态
 */
enum class VoiceButtonState {
    IDLE,       // 空闲状态
    RECORDING,  // 录音中
    PROCESSING  // 处理中
}

/**
 * 语音输入按钮组件
 * 按住录音，松开发送
 *
 * @param buttonState 当前按钮状态
 * @param onRecordStart 开始录音
 * @param onRecordStop 停止录音
 * @param modifier 修饰符
 */
@Composable
fun VoiceInputButton(
    buttonState: VoiceButtonState,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 动画效果：录音时的脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (buttonState == VoiceButtonState.RECORDING) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_animation"
    )

    // 颜色动画
    val backgroundColor by animateColorAsState(
        targetValue = when (buttonState) {
            VoiceButtonState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            VoiceButtonState.RECORDING -> Color.Red.copy(alpha = 0.8f)
            VoiceButtonState.PROCESSING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        },
        animationSpec = tween(300),
        label = "color_animation"
    )

    val iconColor by animateColorAsState(
        targetValue = when (buttonState) {
            VoiceButtonState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            VoiceButtonState.RECORDING -> Color.White
            VoiceButtonState.PROCESSING -> MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = tween(300),
        label = "icon_color_animation"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(if (buttonState == VoiceButtonState.RECORDING) scale else 1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // 按下开始录音
                        onRecordStart()
                        tryAwaitRelease()
                        // 松开停止录音
                        onRecordStop()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (buttonState) {
                VoiceButtonState.IDLE -> Icons.Default.MicNone
                VoiceButtonState.RECORDING -> Icons.Default.Mic
                VoiceButtonState.PROCESSING -> Icons.Default.Mic
            },
            contentDescription = when (buttonState) {
                VoiceButtonState.IDLE -> "按住说话"
                VoiceButtonState.RECORDING -> "松开发送"
                VoiceButtonState.PROCESSING -> "处理中"
            },
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 录音提示组件
 * 显示在输入框上方，提示用户当前状态
 *
 * @param isRecording 是否正在录音
 * @param recordingText 提示文字
 */
@Composable
fun RecordingIndicator(
    isRecording: Boolean,
    recordingText: String,
    modifier: Modifier = Modifier
) {
    if (isRecording) {
        val infiniteTransition = rememberInfiniteTransition(label = "recording_indicator")

        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha_animation"
        )

        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = alpha))
                .size(8.dp)
        )
    }
}
