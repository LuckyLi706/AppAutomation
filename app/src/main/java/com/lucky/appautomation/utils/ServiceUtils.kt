package com.lucky.appautomation.utils

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import kotlin.reflect.KClass

class ServiceUtils {

    companion object {
        /**
         * 检查特定 Service 是否正在运行。
         */
        fun Context.isServiceRunning(serviceClass: KClass<out Service>): Boolean {
            // 强制转换为 ActivityManager
            val manager =
                getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager? ?: return false

            // 检查 manager 是否为空

            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

}