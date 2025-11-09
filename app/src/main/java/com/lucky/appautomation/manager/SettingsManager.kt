import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings.Global.putInt
import android.provider.Settings.Global.putString
import androidx.appcompat.app.AppCompatDelegate

/**
 * 设置管理器 (Singleton)
 *
 * 负责应用内所有设置项的读取和保存，使用 SharedPreferences 进行持久化。
 * 所有与设置相关的 Key 和默认值都在这里统一管理。
 */
object SettingsManager {

    private const val PREFS_NAME = "app_settings_prefs"

    // --- 定义所有设置项的 Key ---
    private const val KEY_THEME = "key_theme_mode"
    private const val KEY_FLOAT_WINDOW = "key_is_show_float_window"
    private const val KEY_ACTIVE_COMMAND_NAME = "active_command_name"
    private const val KEY_CURRENT_RUN_STATE = "current_run_state"

    private const val KEY_LAUNCH_DELAY = "key_launch_delay"
    private const val KEY_SPEND_TIME = "key_launch_delay_spend_time"
    private const val KEY_COMMAND_DELAY_RANDOM_ENABLED = "key_command_delay_random_enabled"
    private const val KEY_COMMAND_DELAY_FIXED = "key_command_delay_fixed"
    private const val KEY_COMMAND_DELAY_MIN = "key_command_delay_min"
    private const val KEY_COMMAND_DELAY_MAX = "key_command_delay_max"

    /**
     * 获取 SharedPreferences 实例。
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- 公开的 API ---

    // region Theme Settings
    fun applyAndSaveTheme(context: Context, themeMode: Int) {
        AppCompatDelegate.setDefaultNightMode(themeMode)
        getPreferences(context).edit().putInt(KEY_THEME, themeMode).apply()
    }

    fun loadAndApplyTheme(context: Context) {
        val savedTheme =
            getPreferences(context).getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
    }
    // endregion

    fun saveFloatWindow(context: Context, isShowFloatWindow: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_FLOAT_WINDOW, isShowFloatWindow).apply()
    }

    fun getFloatWindow(context: Context): Boolean {
        val savedIsShowFloatWindow =
            getPreferences(context).getBoolean(KEY_FLOAT_WINDOW, false)
        return savedIsShowFloatWindow
    }

    // region Delay Settings
    fun saveLaunchDelay(context: Context, delay: Int) {
        getPreferences(context).edit().putInt(KEY_LAUNCH_DELAY, delay).apply()
    }

    fun getLaunchDelay(context: Context): Int {
        // 【默认值: 2000】
        return getPreferences(context).getInt(KEY_LAUNCH_DELAY, 2000)
    }

    fun saveSpendTime(context: Context, spendTime: Int) {
        // 【默认值: 2000】
        getPreferences(context).edit().putInt(KEY_SPEND_TIME, spendTime).apply()
    }

    fun getSpendTime(context: Context): Int {
        // 【默认值: 2000】
        return getPreferences(context).getInt(KEY_SPEND_TIME, 1000)
    }

    /**
     * 【新增】保存当前运行状态
     */
    fun saveRunningState(context: Context, commandName: String?, runState: RunState) {
        getPreferences(context).edit().apply {
            putString(KEY_ACTIVE_COMMAND_NAME, commandName)
            putInt(KEY_CURRENT_RUN_STATE, runState.ordinal)
            apply()
        }
    }

    /**
     * 【新增】读取当前活动的任务名
     */
    fun getActiveCommandName(context: Context): String? {
        return getPreferences(context).getString(KEY_ACTIVE_COMMAND_NAME, null)
    }

    /**
     * 【新增】读取当前运行状态
     */
    fun getCurrentRunState(context: Context): RunState {
        val ordinal = getPreferences(context).getInt(KEY_CURRENT_RUN_STATE, RunState.IDLE.ordinal)
        // 安全地从 ordinal 转换回 enum
        return RunState.values().getOrElse(ordinal) { RunState.IDLE }
    }

    /**
     * 用于封装指令延迟相关的多个设置值，方便传递。
     */
    data class CommandDelaySettings(
        val isRandom: Boolean,
        val fixed: Int,
        val min: Int,
        val max: Int
    )

    fun saveCommandDelay(
        context: Context,
        isRandomEnabled: Boolean,
        fixed: Int,
        min: Int,
        max: Int
    ) {
        getPreferences(context).edit().apply {
            putBoolean(KEY_COMMAND_DELAY_RANDOM_ENABLED, isRandomEnabled)
            putInt(KEY_COMMAND_DELAY_FIXED, fixed)
            putInt(KEY_COMMAND_DELAY_MIN, min)
            putInt(KEY_COMMAND_DELAY_MAX, max)
            apply()
        }
    }

    fun getCommandDelay(context: Context): CommandDelaySettings {
        val prefs = getPreferences(context)
        return CommandDelaySettings(
            // 【默认值: 未选中】
            isRandom = prefs.getBoolean(KEY_COMMAND_DELAY_RANDOM_ENABLED, false),
            // 【默认值: 1000】
            fixed = prefs.getInt(KEY_COMMAND_DELAY_FIXED, 1000),
            // 【默认值: 1000】
            min = prefs.getInt(KEY_COMMAND_DELAY_MIN, 1000),
            // 【默认值: 2000】
            max = prefs.getInt(KEY_COMMAND_DELAY_MAX, 2000)
        )
    }
    // endregion
}