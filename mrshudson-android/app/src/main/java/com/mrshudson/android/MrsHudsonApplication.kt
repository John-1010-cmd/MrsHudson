package com.mrshudson.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MrsHudsonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 应用初始化逻辑
    }
}
