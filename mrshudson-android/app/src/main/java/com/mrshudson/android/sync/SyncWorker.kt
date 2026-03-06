package com.mrshudson.android.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrshudson.android.data.local.dao.EventDao
import com.mrshudson.android.data.local.dao.TodoDao
import com.mrshudson.android.data.remote.CalendarApi
import com.mrshudson.android.data.remote.TodoApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 同步工作器
 * 定期在后台同步日历事件和待办事项
 *
 * @property context 应用上下文
 * @property workerParams WorkManager 参数
 * @property calendarApi 日历 API
 * @property todoApi 待办 API
 * @property eventDao 日历事件 DAO
 * @property todoDao 待办事项 DAO
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarApi: CalendarApi,
    private val todoApi: TodoApi,
    private val eventDao: EventDao,
    private val todoDao: TodoDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "periodic_sync_work"

        /**
         * 同步周期（6小时）
         */
        private const val SYNC_INTERVAL_HOURS = 6L

        /**
         * 配置并启动定期同步任务
         * 只在 WiFi 下同步大数据
         *
         * @param context 应用上下文
         */
        @JvmStatic
        fun schedulePeriodicSync(context: Context) {
            // WiFi 下的约束条件
            val wifiOnlyConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi
                .setRequiresBatteryNotLow(true) // 电量不低
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(wifiOnlyConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "定期同步任务已安排（WiFi 下每 $SYNC_INTERVAL_HOURS 小时执行一次）")
        }

        /**
         * 配置并启动定期同步任务（移动数据也可同步）
         *
         * @param context 应用上下文
         * @param wifiOnly 是否只在 WiFi 下同步
         */
        @JvmStatic
        fun schedulePeriodicSync(context: Context, wifiOnly: Boolean) {
            val constraintsBuilder = Constraints.Builder()
                .setRequiresBatteryNotLow(true)

            if (wifiOnly) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            }

            val constraints = constraintsBuilder.build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "定期同步任务已安排（${if (wifiOnly) "WiFi" else "任意网络"}下每 $SYNC_INTERVAL_HOURS 小时执行一次）")
        }

        /**
         * 取消定期同步任务
         *
         * @param context 应用上下文
         */
        @JvmStatic
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "定期同步任务已取消")
        }
    }

    /**
     * 执行后台同步任务
     */
    override suspend fun doWork(): Result {
        Log.d(TAG, "开始后台同步...")

        return try {
            // 同步日历事件
            syncCalendarEvents()

            // 同步待办事项
            syncTodos()

            Log.d(TAG, "后台同步完成")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "后台同步失败: ${e.message}", e)

            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * 同步日历事件
     */
    private suspend fun syncCalendarEvents() {
        try {
            // 从服务器获取日历事件
            val eventsResponse = calendarApi.getEvents()

            if (eventsResponse.isSuccessful) {
                eventsResponse.body()?.let { result ->
                    if (result.code == 200) {
                        result.data?.let { events ->
                            // 清除旧数据并插入新数据
                            eventDao.deleteAll()
                            eventDao.insertAll(events.map { dto ->
                                com.mrshudson.android.data.local.entity.EventEntity(
                                    id = dto.id,
                                    userId = dto.userId,
                                    title = dto.title,
                                    description = dto.description,
                                    startTime = dto.startTime,
                                    endTime = dto.endTime,
                                    location = dto.location,
                                    category = dto.category,
                                    reminderMinutes = dto.reminderMinutes,
                                    isRecurring = dto.isRecurring,
                                    createdAt = dto.createdAt,
                                    updatedAt = dto.updatedAt
                                )
                            })
                            Log.d(TAG, "同步了 ${events.size} 个日历事件")
                        }
                    }
                }
            } else {
                Log.w(TAG, "获取日历事件失败: ${eventsResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步日历事件失败: ${e.message}", e)
        }
    }

    /**
     * 同步待办事项
     */
    private suspend fun syncTodos() {
        try {
            // 从服务器获取待办事项
            val todosResponse = todoApi.getTodos()

            if (todosResponse.isSuccessful) {
                todosResponse.body()?.let { result ->
                    if (result.code == 200) {
                        result.data?.let { todos ->
                            // 清除旧数据并插入新数据
                            todoDao.deleteAll()
                            todoDao.insertAll(todos.map { dto ->
                                com.mrshudson.android.data.local.entity.TodoEntity(
                                    id = dto.id,
                                    userId = dto.userId,
                                    title = dto.title,
                                    description = dto.description,
                                    priority = dto.priority,
                                    status = dto.status,
                                    dueDate = dto.dueDate,
                                    completedAt = dto.completedAt,
                                    createdAt = dto.createdAt,
                                    updatedAt = dto.updatedAt
                                )
                            })
                            Log.d(TAG, "同步了 ${todos.size} 个待办事项")
                        }
                    }
                }
            } else {
                Log.w(TAG, "获取待办事项失败: ${todosResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步待办事项失败: ${e.message}", e)
        }
    }
}
