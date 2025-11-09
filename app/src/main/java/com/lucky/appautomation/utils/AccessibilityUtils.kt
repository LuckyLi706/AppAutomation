package com.lucky.appautomation.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.lucky.appautomation.service.MyAccessibilityService

/**
 * 辅助功能相关权限和状态的工具类
 */
object AccessibilityUtils {

    private const val TAG = "AccessibilityUtils"

    /**
     * 检查我们的辅助功能服务 (MyAccessibilityService) 是否已经在系统设置中被用户启用。
     *
     * @param context 上下文环境，用于访问 ContentResolver。
     * @return 如果服务已启用，返回 true；否则返回 false。
     */
    fun isServiceEnabled(context: Context): Boolean {
        // 1. 构建我们服务的唯一标识符，格式为：包名/服务完整类名
        // 这是系统用来识别服务的标准格式。
        val serviceId = "${context.packageName}/${MyAccessibilityService::class.java.canonicalName}"
        Log.d(TAG, "Checking for service ID: $serviceId")

        try {
            // 2. 从系统安全设置中，获取当前所有已启用的辅助功能服务的列表字符串。
            // 这个字符串由多个服务ID组成，以冒号 (:) 分隔。
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            // 如果返回的字符串是 null 或空的，说明没有任何服务被启用。
            if (enabledServices.isNullOrEmpty()) {
                Log.d(TAG, "No accessibility services are enabled.")
                return false
            }

            // 3. 使用一个安全的分割器来遍历已启用的服务列表。
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)

            while (splitter.hasNext()) {
                val enabledServiceId = splitter.next()
                Log.d(TAG, "Found enabled service: $enabledServiceId")
                // 4. 忽略大小写，比较当前遍历到的服务ID是否与我们的服务ID相同。
                if (enabledServiceId.equals(serviceId, ignoreCase = true)) {
                    Log.i(TAG, "Our accessibility service is enabled.")
                    return true // 找到了！说明我们的服务已被启用。
                }
            }
        } catch (e: Exception) {
            // 捕获可能的异常，例如读取系统设置失败。
            Log.e(TAG, "Error while checking accessibility service status.", e)
            return false
        }

        // 如果遍历完所有已启用的服务都没有找到我们的服务，说明它未被启用。
        Log.w(TAG, "Our accessibility service is disabled.")
        return false
    }
}