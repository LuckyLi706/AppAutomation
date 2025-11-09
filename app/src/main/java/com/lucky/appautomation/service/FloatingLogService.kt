package com.lucky.appautomation.service

import SettingsManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lucky.appautomation.AutomationApplication
import com.lucky.appautomation.R

class FloatingLogService : Service() {

    companion object {
        // --- 控制命令 Actions ---
        const val ACTION_SHOW = "com.lucky.automation.log.ACTION_SHOW"
        const val ACTION_HIDE = "com.lucky.automation.log.ACTION_HIDE"

        // --- 日志广播 Actions ---
        const val ACTION_LOG = "com.lucky.automation.log.ACTION_LOG"
        const val EXTRA_LOG_MESSAGE = "EXTRA_LOG_MESSAGE"

        /**
         * 【全局调用入口】
         * 一个方便的静态方法，用于在任何地方发送日志。
         * @param context 上下文
         * @param message 日志消息
         */
        fun log(context: Context, message: String) {
            if (!SettingsManager.getFloatWindow(AutomationApplication.context)) {
                return
            }
            // 【核心修改】创建一个指向自己的定向 Intent
            val intent = Intent(context, FloatingLogService::class.java).apply {
                // 我们不再需要自定义 Action，直接用 Intent 来区分
                action = ACTION_LOG // 继续使用 Action 作为区分内部逻辑的标志
                putExtra(EXTRA_LOG_MESSAGE, message)
            }
            // 【核心修改】使用 startService 来发送定向 Intent
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var logTextView: TextView? = null
    private var scrollView: ScrollView? = null

    // 创建一个广播接收器，专门用来接收日志消息
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_LOG) {
                val message = intent.getStringExtra(EXTRA_LOG_MESSAGE)
                if (message != null) {
                    addLogToView(message)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // 注册广播接收器
        val intentFilter = IntentFilter(ACTION_LOG)
        ContextCompat.registerReceiver(
            this,
            logReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                // 如果悬浮窗不存在，则创建
                if (floatingView == null) {
                    showFloatingWindow()
                }
            }

            ACTION_HIDE -> stopSelf()
            ACTION_LOG -> {
                val message = intent.getStringExtra(EXTRA_LOG_MESSAGE)
                if (message != null) {
                    if (floatingView == null) {
                        showFloatingWindow()
                    }
                    addLogToView(message)
                }
            }
        }
        return START_NOT_STICKY // 如果服务被杀死，不自动重启
    }

    private fun addLogToView(message: String) {
        logTextView?.post {
            logTextView?.append("\n> $message")
            scrollView?.post {
                scrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 服务销毁时，移除悬浮窗并注销广播接收器
        floatingView?.let { windowManager.removeView(it) }
        unregisterReceiver(logReceiver)
    }


    private fun showFloatingWindow() {
        // 【核心修复】添加卫兵，防止重复创建和添加
        if (floatingView != null) {
            return
        }
        // ... (获取 WindowManager, LayoutInflater 的代码不变) ...
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_log_view, null)

        // 初始化视图
        logTextView = floatingView?.findViewById(R.id.tv_log_content)
        scrollView = floatingView?.findViewById(R.id.scroll_view)
        logTextView?.text = ""

        // 【1. 实现关闭功能】
        val closeButton: ImageView? = floatingView?.findViewById(R.id.iv_close_window)
        closeButton?.setOnClickListener {
            stopSelf() // 点击关闭按钮，停止服务
        }

        // 创建 LayoutParams
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 获取屏幕尺寸
        val screenWidth: Int
        val screenHeight: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            screenWidth = windowMetrics.bounds.width()
            screenHeight = windowMetrics.bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }

        // 根据比例计算尺寸
        val windowWidth = screenWidth * 2 / 3
        val windowHeight = screenHeight / 4

        val params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight, // dp to px conversion might be needed for precision
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - windowWidth) / 2 // 让悬浮窗初始位置水平居中
            y = 100
        }
        // 添加视图到窗口
        windowManager.addView(floatingView, params)

        // 【2. 实现拖拽功能】
        val contentContainer: View? = floatingView?.findViewById(R.id.ll_content_container)
        contentContainer?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 记录触摸点的初始位置和悬浮窗的初始位置
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true // 返回 true 表示消费了此事件
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // 计算触摸点的移动距离
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY

                        // 更新悬浮窗的位置
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()

                        // 应用新的位置参数
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        // (可选) 可以在这里添加吸附到屏幕边缘的逻辑
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null
}