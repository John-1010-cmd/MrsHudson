package com.mrshudson.android.di

import com.mrshudson.android.data.remote.AuthApi
import com.mrshudson.android.data.remote.CalendarApi
import com.mrshudson.android.data.remote.ChatApi
import com.mrshudson.android.data.remote.PushApi
import com.mrshudson.android.data.remote.RouteApi
import com.mrshudson.android.data.remote.TodoApi
import com.mrshudson.android.data.remote.WeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
     * 基础 URL
     * 10.0.2.2 是 Android 模拟器访问宿主机 localhost 的特殊地址
     */
    private const val BASE_URL = "http://10.0.2.2:8080/api/"

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
     * 提供 OkHttpClient 实例
     * 配置连接超时、读取超时和日志拦截器
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
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
     * 提供 Retrofit 实例
     * 配置基础 URL、OkHttp 客户端和 Gson 转换器
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gsonConverterFactory: GsonConverterFactory
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(gsonConverterFactory)
            .build()
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
}
