package com.mrshudson.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.mrshudson.android.domain.model.Conversation
import com.mrshudson.android.ui.components.chat.MessageBubble
import com.mrshudson.android.ui.components.chat.VoiceButtonState
import com.mrshudson.android.ui.components.chat.VoiceInputButton
import com.mrshudson.android.ui.components.chat.VoiceRecognizer
import com.mrshudson.android.ui.components.chat.VoiceRecognizerCallback

/**
 * AI 对话页面
 * 提供完整的对话功能，包括会话列表、消息收发、语音输入、TTS播放
 *
 * @param modifier 修饰符
 * @param viewModel ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var voiceButtonState by remember { mutableStateOf(VoiceButtonState.IDLE) }

    // 音频播放器
    val audioPlayer = viewModel.audioPlayer

    // 麦克风权限
    val micPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    // 获取本地 Context
    val context = LocalContext.current

    // 语音识别器 - 使用 remember 和 LocalContext
    val voiceRecognizer = remember(context) { VoiceRecognizer(context) }

    // 创建语音识别回调
    val voiceCallback = remember {
        object : VoiceRecognizerCallback() {
            override fun onResult(text: String) {
                // 在主线程更新状态
                voiceButtonState = VoiceButtonState.PROCESSING
                // 将识别的文字添加到输入框
                if (text.isNotBlank()) {
                    inputText = text
                    // 自动发送消息（使用流式响应）
                    viewModel.sendMessageStream(text)
                    inputText = ""
                }
                voiceButtonState = VoiceButtonState.IDLE
            }

            override fun onError(errorMessage: String) {
                voiceButtonState = VoiceButtonState.IDLE
            }

            override fun onReadyForSpeech() {
                voiceButtonState = VoiceButtonState.RECORDING
            }

            override fun onEndOfSpeech() {
                voiceButtonState = VoiceButtonState.PROCESSING
            }

            override fun onRecording(volume: Int) {
                // 可以用于显示音量动画
            }
        }
    }

    // 初始化语音识别器
    LaunchedEffect(Unit) {
        voiceRecognizer.initialize()
        voiceRecognizer.setCallback(voiceCallback)
    }

    // 清理语音识别器
    DisposableEffect(Unit) {
        onDispose {
            voiceRecognizer.destroy()
        }
    }

    // 设置音频播放器回调
    LaunchedEffect(audioPlayer) {
        audioPlayer.setOnStateChangedListener { updatedMessage ->
            viewModel.updateMessageAudioState(updatedMessage)
        }
    }

    // 显示错误信息
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // 自动滚动到底部
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // 使用 Box 替代 Scaffold，避免与 MainScreen 的 Scaffold 嵌套冲突
    Box(modifier = modifier.fillMaxSize()) {
        // 错误提示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // 左侧会话列表
            ConversationList(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                onConversationClick = { viewModel.selectConversation(it) },
                onDeleteConversation = { viewModel.deleteConversation(it) },
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxSize()
            )

            // 右侧对话区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.currentConversationTitle.ifEmpty { "AI 对话" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.createNewConversation() }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "新建会话",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 消息列表
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (uiState.messages.isEmpty() && !uiState.isLoading) {
                        // 空状态
                        EmptyChatState(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = uiState.messages,
                                key = { it.id }
                            ) { message ->
                                MessageBubble(
                                    message = message,
                                    onPlay = { audioPlayer.replay(message) },
                                    onPause = { audioPlayer.pause(message) },
                                    onResume = { audioPlayer.resume(message) },
                                    onToggleThinking = { viewModel.toggleThinking(message.id) }
                                )
                            }

                            // 加载中显示
                            if (uiState.isSending) {
                                item {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "  AI 正在回复...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 加载指示器
                    if (uiState.isLoading && uiState.messages.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                // 输入框
                MessageInput(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessageStream(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !uiState.isSending,
                    voiceButtonState = voiceButtonState,
                    onVoiceRecordStart = {
                        // 检查权限
                        if (micPermissionState.status.isGranted) {
                            voiceRecognizer.startListening()
                        } else if (micPermissionState.status.shouldShowRationale) {
                            // 显示 rationale 后再次请求
                            micPermissionState.launchPermissionRequest()
                        } else {
                            // 首次请求权限
                            micPermissionState.launchPermissionRequest()
                        }
                    },
                    onVoiceRecordStop = {
                        if (micPermissionState.status.isGranted) {
                            voiceRecognizer.stopListening()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
    }
}

/**
 * 会话列表组件
 */
@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    currentConversationId: Long?,
    onConversationClick: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp)
    ) {
        if (conversations.isEmpty()) {
            Text(
                text = "暂无会话",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else {
            conversations.forEach { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isSelected = conversation.id == currentConversationId,
                    onClick = { onConversationClick(conversation.id) },
                    onDelete = { onDeleteConversation(conversation.id) }
                )
            }
        }
    }
}

/**
 * 会话列表项
 */
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.displayTitle(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (conversation.lastMessageAt != null) {
                    Text(
                        text = conversation.formattedLastMessageTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除会话",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 空对话状态
 */
@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "开始新对话",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "发送消息开始与 AI 交流",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 消息输入框
 */
@Composable
private fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    voiceButtonState: VoiceButtonState = VoiceButtonState.IDLE,
    onVoiceRecordStart: () -> Unit = {},
    onVoiceRecordStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入消息...") },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = { if (value.isNotBlank()) onSend() }
            ),
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 语音输入按钮
        VoiceInputButton(
            buttonState = voiceButtonState,
            onRecordStart = onVoiceRecordStart,
            onRecordStop = onVoiceRecordStop
        )

        Spacer(modifier = Modifier.width(4.dp))

        // 发送按钮
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (enabled && value.isNotBlank()) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
