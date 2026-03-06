package com.mrshudson.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mrshudson.android.MainActivity
import com.mrshudson.android.R
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.PushRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging 服务
 *
 * 重要提示：
 * 此服务需要 Firebase 项目配置才能正常工作。
 * 要启用 FCM 功能，需要：
 * 1. 在 Firebase Console 创建项目
 * 2. 下载 google-services.json 并放置在 app/ 目录下
 * 3. 在 build.gradle.kts 中应用 com.google.gms.google-services 插件
 *
 * 在完成上述配置之前，此服务不会接收推送消息。
 * 代码框架已准备好，一旦配置完成即可使用。
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var pushRepository: PushRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val CHANNEL_ID = "mrshudson_push_channel"
        private const val CHANNEL_NAME = "推送通知"
        private const val CHANNEL_DESCRIPTION = "Mrs Hudson 推送通知通道"

        /**
         * 保存设备 Token 的偏好设置键名
         */
        const val PREFS_NAME = "mrshudson_fcm_prefs"
        const val KEY_DEVICE_TOKEN = "device_token"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * 接收到新的 FCM Token 时调用
     * 当设备首次注册或 Token 刷新时触发
     *
     * @param token 新的设备 Token
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 保存 Token 到本地
        saveTokenToPrefs(token)
        // 上报 Token 到服务器
        reportTokenToServer(token)
    }

    /**
     * 接收到推送消息时调用
     *
     * @param remoteMessage 接收到的远程消息
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 处理通知消息
        remoteMessage.notification?.let { notification ->
            showNotification(
                title = notification.title ?: getString(R.string.app_name),
                body = notification.body ?: ""
            )
        }

        // 处理数据消息
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        }
    }

    /**
     * 保存 Token 到本地偏好设置
     *
     * @param token 设备 Token
     */
    private fun saveTokenToPrefs(token: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    /**
     * 上报 Token 到服务器
     *
     * @param token 设备 Token
     */
    private fun reportTokenToServer(token: String) {
        serviceScope.launch {
            try {
                val result = pushRepository.registerDevice(token).first()
                if (result is ApiResult.Success) {
                    android.util.Log.d("FcmService", "Device token registered successfully")
                } else if (result is ApiResult.Error) {
                    android.util.Log.w("FcmService", "Failed to register token: ${result.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("FcmService", "Error registering device token", e)
            }
        }
    }

    /**
     * 处理数据消息
     *
     * @param data 消息数据
     */
    private fun handleDataMessage(data: Map<String, String>) {
        // 根据数据内容执行不同操作
        // 例如：刷新数据、跳转页面等
        android.util.Log.d("FcmService", "Received data message: $data")
    }

    /**
     * 创建通知渠道
     * Android 8.0 及以上需要创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示本地通知
     *
     * @param title 通知标题
     * @param body 通知内容
     */
    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
