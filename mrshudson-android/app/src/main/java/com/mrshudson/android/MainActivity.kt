package com.mrshudson.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.mrshudson.android.ui.navigation.MrsHudsonNavHost
import com.mrshudson.android.ui.navigation.NavRoutes
import com.mrshudson.android.ui.screens.login.LoginViewModel
import com.mrshudson.android.ui.theme.MrsHudsonTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MrsHudsonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MrsHudsonApp()
                }
            }
        }
    }
}

/**
 * MrsHudson 应用主入口
 * 处理自动登录逻辑和导航
 */
@Composable
fun MrsHudsonApp(
    viewModel: LoginViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState(initial = false)
    var isCheckingAuth by remember { mutableStateOf(true) }

    // 检查登录状态，确定起始页面
    LaunchedEffect(isLoggedIn) {
        if (isCheckingAuth) {
            isCheckingAuth = false
        }
    }

    if (!isCheckingAuth) {
        val startDestination = if (isLoggedIn) {
            NavRoutes.MAIN
        } else {
            NavRoutes.LOGIN
        }

        MrsHudsonNavHost(
            navController = navController,
            startDestination = startDestination
        )
    }
}
