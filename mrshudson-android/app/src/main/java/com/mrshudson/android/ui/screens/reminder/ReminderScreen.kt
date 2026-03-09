package com.mrshudson.android.ui.screens.reminder

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mrshudson.android.domain.model.Reminder
import com.mrshudson.android.domain.model.ReminderType

/**
 * 提醒页面
 * 显示提醒列表，支持筛选、标记已读、延迟提醒等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    modifier: Modifier = Modifier,
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示错误信息
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("提醒中心") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (uiState.unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllAsRead() }) {
                            Text("全部已读")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 筛选 Chips
            FilterChipsRow(
                currentFilter = uiState.currentFilter,
                unreadCount = uiState.unreadCount,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            // 提醒列表
            if (uiState.isLoading && uiState.reminders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredReminders = viewModel.getFilteredReminders()

                if (filteredReminders.isEmpty()) {
                    EmptyReminderState(
                        filter = uiState.currentFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = filteredReminders,
                            key = { it.id }
                        ) { reminder ->
                            ReminderItem(
                                reminder = reminder,
                                onMarkRead = { viewModel.markAsRead(reminder.id) },
                                onSnooze = { minutes -> viewModel.snooze(reminder.id, minutes) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 筛选 Chips 行
 */
@Composable
private fun FilterChipsRow(
    currentFilter: ReminderFilter,
    unreadCount: Int,
    onFilterSelected: (ReminderFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentFilter == ReminderFilter.ALL,
            onClick = { onFilterSelected(ReminderFilter.ALL) },
            label = { Text("全部") }
        )
        FilterChip(
            selected = currentFilter == ReminderFilter.EVENT,
            onClick = { onFilterSelected(ReminderFilter.EVENT) },
            label = { Text("日程") }
        )
        FilterChip(
            selected = currentFilter == ReminderFilter.TODO,
            onClick = { onFilterSelected(ReminderFilter.TODO) },
            label = { Text("待办") }
        )
        FilterChip(
            selected = currentFilter == ReminderFilter.UNREAD,
            onClick = { onFilterSelected(ReminderFilter.UNREAD) },
            label = { Text("未读($unreadCount)") }
        )
    }
}

/**
 * 提醒列表项
 */
@Composable
private fun ReminderItem(
    reminder: Reminder,
    onMarkRead: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* 点击可以跳转到详情 */ },
        colors = CardDefaults.cardColors(
            containerColor = if (!reminder.isRead) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 类型图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(reminder.type.toColor().copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = reminder.type.toIcon(),
                    contentDescription = null,
                    tint = reminder.type.toColor(),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!reminder.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                }

                if (!reminder.content.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reminder.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminder.formattedRemindTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = reminder.type.toDisplayString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 操作菜单
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (!reminder.isRead) {
                        DropdownMenuItem(
                            text = { Text("标记已读") },
                            onClick = {
                                showMenu = false
                                onMarkRead()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("延迟5分钟") },
                        onClick = {
                            showMenu = false
                            onSnooze(5)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("延迟15分钟") },
                        onClick = {
                            showMenu = false
                            onSnooze(15)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("延迟1小时") },
                        onClick = {
                            showMenu = false
                            onSnooze(60)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyReminderState(
    filter: ReminderFilter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when (filter) {
                ReminderFilter.EVENT -> "暂无日程提醒"
                ReminderFilter.TODO -> "暂无待办提醒"
                ReminderFilter.UNREAD -> "暂无未读提醒"
                else -> "暂无提醒"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "创建日程或待办时会自动生成提醒",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 扩展函数：获取类型对应的图标
 */
private fun ReminderType.toIcon(): ImageVector {
    return when (this) {
        ReminderType.EVENT -> Icons.Default.CalendarMonth
        ReminderType.TODO -> Icons.Default.CheckCircle
        ReminderType.WEATHER -> Icons.Default.Error
        ReminderType.SYSTEM -> Icons.Default.Notifications
    }
}

/**
 * 扩展函数：获取类型对应的颜色
 */
private fun ReminderType.toColor(): Color {
    return when (this) {
        ReminderType.EVENT -> Color(0xFF1890FF)  // 蓝色
        ReminderType.TODO -> Color(0xFFFA8C16)   // 橙色
        ReminderType.WEATHER -> Color(0xFF52C41A) // 绿色
        ReminderType.SYSTEM -> Color(0xFF722ED1) // 紫色
    }
}
