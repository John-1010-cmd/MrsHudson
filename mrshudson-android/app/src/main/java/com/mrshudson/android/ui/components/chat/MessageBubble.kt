package com.mrshudson.android.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrshudson.android.domain.model.Message
import com.mrshudson.android.domain.model.TtsStatus

/**
 * 消息气泡组件
 *
 * @param message 消息数据
 * @param onPlay 点击播放（从头开始）
 * @param onPause 点击暂停
 * @param onResume 点击继续
 * @param modifier 修饰符
 */
@Composable
fun MessageBubble(
    message: Message,
    onPlay: (() -> Unit)? = null,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.isUserMessage()
    val hasPlaybackHandlers = onPlay != null && onPause != null && onResume != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUserMessage) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(
            horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start
        ) {
            // 消息气泡
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUserMessage) 16.dp else 4.dp,
                            bottomEnd = if (isUserMessage) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUserMessage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUserMessage) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = message.formattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUserMessage) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }

            // AI 消息下方：TTS 状态区域（遵循 SSE_TTS_UNIFIED_SPEC.md 规范）
            if (!isUserMessage) {
                when (message.ttsStatus) {
                    TtsStatus.SYNTHESIZING -> {
                        // content_done 已收到，TTS 后台合成中
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "语音合成中...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TtsStatus.READY -> {
                        // audio_done 收到且有 URL，显示播放按钮
                        if (hasPlaybackHandlers) {
                            TtsButton(
                                playState = message.audioPlayState,
                                hasAudio = message.hasAudio(),
                                onPlay = { onPlay?.invoke() },
                                onPause = { onPause?.invoke() },
                                onResume = { onResume?.invoke() },
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    // TIMEOUT / ERROR / NOAUDIO / NO_AUDIO / PENDING：不显示任何内容
                    else -> {}
                }
            }
        }

        if (isUserMessage) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondary)
                    .padding(8.dp)
            ) {
                Text(
                    text = "我",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    }
}
