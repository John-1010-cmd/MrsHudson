package com.mrshudson.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mrshudson.android.ui.screens.login.LoginScreen
import com.mrshudson.android.ui.screens.main.MainScreen

/**
 * MrsHudson 应用导航图
 * 定义所有页面路由和导航逻辑
 *
 * @param navController 导航控制器
 * @param startDestination 起始目的地
 * @param modifier 修饰符
 */
@Composable
fun MrsHudsonNavHost(
    navController: NavHostController,
    startDestination: String = NavRoutes.LOGIN,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 登录页面
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    // 登录成功后导航到主页面，并清除登录页面的回退栈
                    navController.navigate(NavRoutes.MAIN) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // 主页面（包含底部导航）
        composable(NavRoutes.MAIN) {
            MainScreen(
                onLogout = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.MAIN) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    // TODO: 导航到设置页面
                    // navController.navigate(NavRoutes.SETTINGS)
                }
            )
        }
    }
}
