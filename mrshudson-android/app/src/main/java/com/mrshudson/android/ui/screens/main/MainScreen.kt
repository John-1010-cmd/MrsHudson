package com.mrshudson.android.ui.screens.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.mrshudson.android.data.repository.ChatRepository
import com.mrshudson.android.ui.components.chat.AudioPlayer
import com.mrshudson.android.ui.navigation.BottomNavItem
import com.mrshudson.android.ui.screens.calendar.CalendarScreen
import com.mrshudson.android.ui.screens.chat.ChatScreen
import com.mrshudson.android.ui.screens.route.RouteScreen
import com.mrshudson.android.ui.screens.todo.TodoScreen
import com.mrshudson.android.ui.screens.weather.WeatherScreen
import com.mrshudson.android.ui.screens.reminder.ReminderScreen
import com.mrshudson.android.ui.theme.MrsHudsonTheme

/**
 * 主页面
 * 包含底部导航栏和五个主要功能模块的切换
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    // 使用 rememberSaveable 保存当前选中的导航项，切换时保持状态
    var selectedNavItem by rememberSaveable { mutableStateOf(BottomNavItem.CHAT) }
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MrsHudson",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // 设置/菜单按钮
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }

                    // 下拉菜单
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("设置") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onNavigateToSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("退出登录") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                viewModel.logout()
                                onLogout()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                BottomNavItem.allItems().forEach { item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) },
                        selected = selectedNavItem == item,
                        onClick = { selectedNavItem = item },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        // 根据选中的导航项显示对应的内容
        MainContent(
            selectedNavItem = selectedNavItem,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * 主内容区域
 * 根据当前选中的导航项显示对应的页面
 */
@Composable
private fun MainContent(
    selectedNavItem: BottomNavItem,
    modifier: Modifier = Modifier
) {
    when (selectedNavItem) {
        BottomNavItem.CHAT -> ChatScreen(modifier = modifier)
        BottomNavItem.CALENDAR -> CalendarScreen(modifier = modifier)
        BottomNavItem.TODO -> TodoScreen(modifier = modifier)
        BottomNavItem.WEATHER -> WeatherScreen(modifier = modifier)
        BottomNavItem.ROUTE -> RouteScreen(modifier = modifier)
        BottomNavItem.REMINDER -> ReminderScreen(modifier = modifier)
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    MrsHudsonTheme {
        MainScreen()
    }
}
