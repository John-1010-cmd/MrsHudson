package com.mrshudson.android.di

import android.content.Context
import com.mrshudson.android.data.remote.AuthApi
import com.mrshudson.android.data.remote.CalendarApi
import com.mrshudson.android.data.remote.ChatApi
import com.mrshudson.android.data.remote.PushApi
import com.mrshudson.android.data.remote.ReminderApi
import com.mrshudson.android.data.remote.RouteApi
import com.mrshudson.android.data.remote.ServerUrlManager
import com.mrshudson.android.data.remote.TodoApi
import com.mrshudson.android.data.remote.UrlRewriteInterceptor
import com.mrshudson.android.data.remote.WeatherApi
import com.mrshudson.android.data.local.datastore.SettingsDataStore
import com.mrshudson.android.data.local.datastore.TokenDataStore
import com.mrshudson.android.data.remote.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络模块
 * 提供 Retrofit 和 OkHttp 客户端实例
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 默认基础 URL
     * 10.0.2.2 是 Android 模拟器访问宿主机 localhost 的特殊地址
     */
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/"

    /**
     * 获取基础 URL
     * 优先从 assets/config.properties 读取 api.baseurl 配置
     */
    private fun getBaseUrl(context: Context): String {
        // 默认 URL（模拟器专用）
        var baseUrl = DEFAULT_BASE_URL

        try {
            // 从 assets/config.properties 读取
            val inputStream = context.assets.open("config.properties")
            val tempFile = File(context.cacheDir, "config.properties")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val properties = Properties()
            tempFile.inputStream().use { stream ->
                properties.load(stream)
            }
            tempFile.delete()

            val customUrl = properties.getProperty("api.baseurl")
            if (!customUrl.isNullOrBlank()) {
                baseUrl = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                if (!baseUrl.endsWith("api/")) {
                    baseUrl = "${baseUrl}api/"
                }
            }
        } catch (e: Exception) {
            // 读取失败，使用默认 URL（模拟器地址）
        }

        return baseUrl
    }

    /**
     * 提供 HttpLoggingInterceptor 实例
     * 用于日志记录
     */
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    /**
     * 提供 AuthInterceptor 实例
     * 负责为每个请求添加 JWT Token 认证头
     */
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        @ApplicationContext context: Context
    ): AuthInterceptor {
        return AuthInterceptor(TokenDataStore(context))
    }

    /**
     * 提供 OkHttpClient 实例（用于普通 API 请求）
     * 配置连接超时、读取超时和认证拦截器
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        urlRewriteInterceptor: UrlRewriteInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(urlRewriteInterceptor)  // 添加 URL 重写拦截器
            .addInterceptor(authInterceptor)  // 添加认证拦截器
            .addInterceptor(loggingInterceptor)  // 添加日志拦截器
            .build()
    }
    
    /**
     * 提供 SSE 专用的 OkHttpClient 实例
     * SSE 需要更长的读取超时（60s+），因为服务器推送事件可能间隔较长
     * 注意：SSE 连接使用 EventSource，它会保持连接开放直到收到数据
     */
    @Provides
    @Singleton
    @javax.inject.Named("sse")
    fun provideSseOkHttpClient(
        authInterceptor: AuthInterceptor,
        urlRewriteInterceptor: UrlRewriteInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            // SSE 连接保持时间更长，connectTimeout 保持 30s
            .connectTimeout(30, TimeUnit.SECONDS)
            // readTimeout 设置为 0 表示永不超时（由 SseClient 内部管理超时）
            // 或者设置一个很大的值，确保不先于 SseClient 的 60s 超时触发
            .readTimeout(0, TimeUnit.SECONDS) // 0 = 无超时，由 SseClient 内部管理
            .writeTimeout(30, TimeUnit.SECONDS)
            // SSE 不需要日志拦截器，避免日志过多
            .addInterceptor(urlRewriteInterceptor)
            .addInterceptor(authInterceptor)
            .build()
    }

    /**
     * 提供 GsonConverterFactory 实例
     * 用于 JSON 序列化和反序列化
     */
    @Provides
    @Singleton
    fun provideGsonConverterFactory(): GsonConverterFactory {
        return GsonConverterFactory.create()
    }

    /**
     * 提供 Base URL 字符串
     * 用于 SseClient 和其他需要知道服务器地址的组件
     */
    @Provides
    @Singleton
    fun provideBaseUrl(@ApplicationContext context: Context): String {
        return getBaseUrl(context)
    }

    /**
     * 提供 Retrofit 实例
     * 配置基础 URL、OkHttp 客户端和 Gson 转换器
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        gsonConverterFactory: GsonConverterFactory
    ): Retrofit {
        // 初始化时从 SettingsDataStore 加载保存的服务器地址
        initializeServerUrl(context)

        return Retrofit.Builder()
            .baseUrl(getBaseUrl(context))
            .client(okHttpClient)
            .addConverterFactory(gsonConverterFactory)
            .build()
    }

    /**
     * 初始化服务器地址
     * 从 SettingsDataStore 加载保存的服务器地址到 ServerUrlManager
     * 注意：由于 Hilt 初始化时无法使用协程，改为在首次网络请求时动态加载
     * 见 UrlRewriteInterceptor 中的延迟初始化逻辑
     */
    private fun initializeServerUrl(context: Context) {
        // 不再在此处同步读取，改为在 UrlRewriteInterceptor 中延迟初始化
    }

    /**
     * 提供 AuthApi 实例
     */
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    /**
     * 提供 ChatApi 实例
     */
    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi {
        return retrofit.create(ChatApi::class.java)
    }

    /**
     * 提供 WeatherApi 实例
     */
    @Provides
    @Singleton
    fun provideWeatherApi(retrofit: Retrofit): WeatherApi {
        return retrofit.create(WeatherApi::class.java)
    }

    /**
     * 提供 CalendarApi 实例
     */
    @Provides
    @Singleton
    fun provideCalendarApi(retrofit: Retrofit): CalendarApi {
        return retrofit.create(CalendarApi::class.java)
    }

    /**
     * 提供 TodoApi 实例
     */
    @Provides
    @Singleton
    fun provideTodoApi(retrofit: Retrofit): TodoApi {
        return retrofit.create(TodoApi::class.java)
    }

    /**
     * 提供 RouteApi 实例
     */
    @Provides
    @Singleton
    fun provideRouteApi(retrofit: Retrofit): RouteApi {
        return retrofit.create(RouteApi::class.java)
    }

    /**
     * 提供 PushApi 实例
     */
    @Provides
    @Singleton
    fun providePushApi(retrofit: Retrofit): PushApi {
        return retrofit.create(PushApi::class.java)
    }

    /**
     * 提供 ReminderApi 实例
     */
    @Provides
    @Singleton
    fun provideReminderApi(retrofit: Retrofit): ReminderApi {
        return retrofit.create(ReminderApi::class.java)
    }

    /**
     * 提供 UrlRewriteInterceptor 实例
     * 用于动态修改请求的服务器地址
     */
    @Provides
    @Singleton
    fun provideUrlRewriteInterceptor(
        @ApplicationContext context: Context
    ): UrlRewriteInterceptor {
        return UrlRewriteInterceptor(context)
    }
}
