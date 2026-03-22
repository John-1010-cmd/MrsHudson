package com.mrshudson.android.ui.screens.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mrshudson.android.ui.navigation.BottomNavItem
import com.mrshudson.android.ui.screens.calendar.CalendarScreen
import com.mrshudson.android.ui.screens.chat.ChatScreen
import com.mrshudson.android.ui.screens.route.RouteScreen
import com.mrshudson.android.ui.screens.todo.TodoScreen
import com.mrshudson.android.ui.screens.weather.WeatherScreen
import com.mrshudson.android.ui.screens.reminder.ReminderScreen
import com.mrshudson.android.ui.theme.MrsHudsonTheme
import kotlinx.coroutines.launch

/**
 * 主页面
 * 包含侧边栏、底部导航栏和五个主要功能模块的切换
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

    // 侧边栏状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 侧边栏内容
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                DrawerContent(
                    selectedNavItem = selectedNavItem,
                    onNavItemSelected = { item ->
                        selectedNavItem = item
                        scope.launch { drawerState.close() }
                    },
                    onLogout = {
                        viewModel.logout()
                        onLogout()
                    },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    }
                )
            }
        },
        gesturesEnabled = drawerState.isOpen
    ) {
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
                    navigationIcon = {
                        // 侧边栏菜单按钮
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "打开菜单"
                            )
                        }
                    },
                    actions = {
                        // 设置/更多菜单按钮
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
}

/**
 * 侧边栏内容
 */
@Composable
private fun DrawerContent(
    selectedNavItem: BottomNavItem,
    onNavItemSelected: (BottomNavItem) -> Unit,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        // 顶部标题
        Text(
            text = "MrsHudson",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 会话列表（占位）
        Text(
            text = "会话列表",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Text(
            text = "（暂无会话数据）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 导航菜单
        Text(
            text = "导航",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        BottomNavItem.allItems().forEach { item ->
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) },
                selected = selectedNavItem == item,
                onClick = { onNavItemSelected(item) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 用户信息
        RowScope {
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "用户"
                    )
                },
                label = { Text("用户信息") },
                selected = false,
                onClick = { },
                modifier = Modifier.fillMaxWidth(0.5f)
            )

            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置"
                    )
                },
                label = { Text("设置") },
                selected = false,
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth(0.5f)
            )
        }

        // 退出登录
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "退出登录"
                )
            },
            label = { Text("退出登录") },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.padding(horizontal = 8.dp)
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
