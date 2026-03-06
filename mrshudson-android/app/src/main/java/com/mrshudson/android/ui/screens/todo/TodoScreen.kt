package com.mrshudson.android.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mrshudson.android.domain.model.TodoItem
import com.mrshudson.android.domain.model.TodoPriority
import com.mrshudson.android.domain.model.TodoStatus
import com.mrshudson.android.ui.components.OfflineBanner
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 待办事项页面
 * 显示待办列表，支持创建、编辑、完成、删除待办事项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    modifier: Modifier = Modifier,
    viewModel: TodoViewModel = hiltViewModel()
) {
    val todos by viewModel.todos.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showTodoDialog by viewModel.showTodoDialog.collectAsState()
    val editingTodo by viewModel.editingTodo.collectAsState()
    val isOffline by viewModel.isNetworkAvailable.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("待办事项") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateTodoDialog() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加待办")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 离线提示条
            OfflineBanner(
                isOffline = !isOffline,
                syncState = syncState
            )

            // 筛选标签
            FilterChips(
                selectedStatus = filterStatus,
                onStatusSelected = { viewModel.filterByStatus(it) }
            )

            // 待办列表
            if (todos.isEmpty()) {
                EmptyTodoList()
            } else {
                TodoList(
                    todos = todos,
                    onComplete = { viewModel.completeTodo(it.id) },
                    onUncomplete = { viewModel.uncompleteTodo(it.id) },
                    onEdit = { viewModel.showEditTodoDialog(it) },
                    onDelete = { viewModel.deleteTodo(it.id) }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // 创建/编辑待办对话框
        if (showTodoDialog) {
            TodoDialog(
                todo = editingTodo,
                onDismiss = { viewModel.dismissTodoDialog() },
                onSave = { title, description, priority, dueDate ->
                    viewModel.saveTodo(title, description, priority, dueDate)
                }
            )
        }
    }
}

/**
 * 筛选标签
 */
@Composable
private fun FilterChips(
    selectedStatus: String?,
    onStatusSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onStatusSelected(null) },
            label = { Text("全部") }
        )
        FilterChip(
            selected = selectedStatus == "pending",
            onClick = { onStatusSelected("pending") },
            label = { Text("待处理") }
        )
        FilterChip(
            selected = selectedStatus == "in_progress",
            onClick = { onStatusSelected("in_progress") },
            label = { Text("进行中") }
        )
        FilterChip(
            selected = selectedStatus == "completed",
            onClick = { onStatusSelected("completed") },
            label = { Text("已完成") }
        )
    }
}

/**
 * 空待办列表
 */
@Composable
private fun EmptyTodoList() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无待办事项",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 待办列表
 */
@Composable
private fun TodoList(
    todos: List<TodoItem>,
    onComplete: (TodoItem) -> Unit,
    onUncomplete: (TodoItem) -> Unit,
    onEdit: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(todos, key = { it.id }) { todo ->
            TodoCard(
                todo = todo,
                onComplete = { onComplete(todo) },
                onUncomplete = { onUncomplete(todo) },
                onEdit = { onEdit(todo) },
                onDelete = { onDelete(todo) }
            )
        }
    }
}

/**
 * 待办卡片
 */
@Composable
private fun TodoCard(
    todo: TodoItem,
    onComplete: () -> Unit,
    onUncomplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = todo.status == TodoStatus.COMPLETED
    val isOverdue = todo.isOverdue()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 完成状态图标
            IconButton(
                onClick = { if (isCompleted) onUncomplete() else onComplete() }
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isCompleted) "标记为未完成" else "标记为已完成",
                    tint = if (isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else if (isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 待办内容
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        color = if (isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // 优先级标签
                    PriorityBadge(priority = todo.priority)
                }

                todo.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 截止日期
                todo.formattedDueDate()?.let { dueDate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dueDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 操作按钮
            Column {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 优先级徽章
 */
@Composable
private fun PriorityBadge(priority: TodoPriority) {
    val backgroundColor = Color(priority.toColor())
    val textColor = Color.White

    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = priority.toDisplayString(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

/**
 * 待办创建/编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoDialog(
    todo: TodoItem?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, LocalDateTime?) -> Unit
) {
    var title by remember { mutableStateOf(todo?.title ?: "") }
    var description by remember { mutableStateOf(todo?.description ?: "") }
    var priority by remember { mutableStateOf(todo?.priority ?: TodoPriority.MEDIUM) }
    var dueDate by remember { mutableStateOf(todo?.formattedDueDate() ?: "") }
    var priorityExpanded by remember { mutableStateOf(false) }

    val isEditing = todo != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "编辑待办" else "创建待办")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = priorityExpanded,
                    onExpandedChange = { priorityExpanded = !priorityExpanded }
                ) {
                    OutlinedTextField(
                        value = priority.toDisplayString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("优先级") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        TodoPriority.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.toDisplayString()) },
                                onClick = {
                                    priority = p
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("截止日期 (MM/dd HH:mm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("如: 03/15 18:00") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val dueDateTime = parseDueDate(dueDate)
                        onSave(
                            title,
                            description.ifBlank { null },
                            priority.toApiString(),
                            dueDateTime
                        )
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun parseDueDate(dateStr: String): LocalDateTime? {
    if (dateStr.isBlank()) return null

    return try {
        // 尝试解析 MM/dd HH:mm 格式
        val parts = dateStr.split(" ")
        if (parts.size == 2) {
            val dateParts = parts[0].split("/")
            val timeParts = parts[1].split(":")

            if (dateParts.size == 2 && timeParts.size == 2) {
                val month = dateParts[0].toInt()
                val day = dateParts[1].toInt()
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()

                val now = java.time.LocalDateTime.now()
                val year = now.year

                // 如果日期已过，默认为明年
                val actualYear = if (month < now.monthValue || (month == now.monthValue && day < now.dayOfMonth)) {
                    year + 1
                } else {
                    year
                }

                java.time.LocalDateTime.of(actualYear, month, day, hour, minute)
            } else null
        } else null
    } catch (e: Exception) {
        null
    }
}
