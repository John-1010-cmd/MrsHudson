package com.mrshudson.android.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrshudson.android.domain.model.Message
import com.mrshudson.android.domain.model.MessageRole
import com.mrshudson.android.domain.model.TtsStatus

/**
 * 消息气泡组件
 *
 * @param message 消息数据
 * @param onPlay 点击播放（从头开始）
 * @param onPause 点击暂停
 * @param onResume 点击继续
 * @param onToggleThinking 点击切换思考过程展开/折叠状态
 * @param modifier 修饰符
 */
@Composable
fun MessageBubble(
    message: Message,
    onPlay: (() -> Unit)? = null,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onToggleThinking: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.role == MessageRole.USER
    val hasPlaybackHandlers = onPlay != null && onPause != null && onResume != null
    // 是否有思考过程内容（仅 AI 消息且有思考内容时显示）
    val hasThinkingContent = !isUserMessage && message.thinkingContent != null && message.thinkingContent.isNotBlank()

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
            // 思考过程折叠块（仅 assistant 且有 thinkingContent 时显示）
            if (hasThinkingContent) {
                ThinkingBlock(
                    content = message.thinkingContent!!,
                    isExpanded = message.isThinkingExpanded,
                    onToggle = { onToggleThinking?.invoke() },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

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

/**
 * 思考过程折叠块组件
 *
 * @param content 思考内容
 * @param isExpanded 是否展开
 * @param onToggle 切换展开/折叠状态
 * @param modifier 修饰符
 */
@Composable
fun ThinkingBlock(
    content: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        // 可点击的标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧠 思考过程",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        // 可折叠的内容区域
        AnimatedVisibility(visible = isExpanded) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                maxLines = 20,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
