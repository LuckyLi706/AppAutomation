package com.lucky.appautomation

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

class AutomationApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context // 全局 Context 引用
    }

    override fun onCreate() {
        super.onCreate()
        // 【新增】应用启动时，从 SharedPreferences 初始化全局状态
        TaskStateManager.initState(this)
        context = applicationContext
        // 在应用启动时，使用 SettingsManager 来加载并应用主题。
        // 代码非常简洁，只调用一个方法即可。
        SettingsManager.loadAndApplyTheme(this)
    }

}