package com.lucky.appautomation.service

import RunState
import SettingsManager
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.lucky.appautomation.R
import com.lucky.appautomation.activity.MainActivity
import com.lucky.appautomation.db.AppDatabase
import com.lucky.appautomation.db.model.CommandGroup
import kotlinx.coroutines.*
import kotlin.random.Random

class AutomationService : Service() {

    // 使用 SupervisorJob，一个子协程的失败不会影响其他子协程
    private val serviceJob = SupervisorJob()

    // 创建一个与服务生命周期绑定的协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // 用于持有当前正在运行的自动化任务的 Job，以便可以取消它
    private var automationJob: Job? = null

    // 状态变量，用于存储当前活动任务的唯一标识（name）
    private var activeCommandName: String? = null
    private var currentState: RunState = RunState.IDLE


    companion object {
        // 定义各种 Action，用于 Intent 通信
        const val ACTION_START = "com.lucky.automation.ACTION_START"
        const val ACTION_PAUSE = "com.lucky.automation.ACTION_PAUSE"
        const val ACTION_STOP = "com.lucky.automation.ACTION_STOP"
        const val ACTION_CONTINUE = "com.lucky.automation.ACTION_CONTINUE"

        const val EXTRA_LOG_MESSAGE = "LOG_MESSAGE"

        // 定义 Intent Extra 的 Key
        const val EXTRA_COMMAND_LIST = "COMMAND_GROUP"
        const val EXTRA_COMMAND_NAME = "COMMAND_NAME"
        const val EXTRA_STATE = "STATE"

        private const val NOTIFICATION_ID = 1026
        private const val CHANNEL_ID = "AutomationChannel"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d("com.lucky.appautomation.service.AutomationService", "Service Created")
        // 它只需要一个唯一的标签名即可
        // 初始化 MediaSession
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            "com.lucky.appautomation.service.AutomationService",
            "onStartCommand received action: ${intent?.action}"
        )

        // 【核心修改】处理服务被系统重启的情况
//        if (intent == null) {
//            handleServiceRestart()
//            return START_STICKY
//        }

        when (intent?.action) {
            ACTION_START -> {
                // 这个分支现在只处理从 MainActivity 来的首次启动
                val commandGroup = intent.getSerializableExtra(EXTRA_COMMAND_LIST) as? CommandGroup
                val commandName = intent.getStringExtra(EXTRA_COMMAND_NAME)

                if (commandName != null && commandGroup != null) {
                    this.activeCommandName = commandName
                    startOrContinueAutomation(commandGroup)
                } else {
                    Log.w("", "Start action received without valid commands or name.")
                    stopSelf() // 如果数据不完整，直接停止服务
                }
            }

            // 【新增】处理“继续”按钮的逻辑
            ACTION_CONTINUE -> {
                if (currentState == RunState.PAUSED && activeCommandName != null) {
                    // 异步从数据库加载任务数据并恢复
                    serviceScope.launch {
                        val commandGroup = AppDatabase.getInstance(this@AutomationService)
                            .commandGroupDao()
                            .getGroupByName(activeCommandName!!)
                        if (commandGroup != null) {
                            val commands = AppDatabase.getInstance(this@AutomationService)
                                .commandDao()
                                .getCommandsByGroupName(activeCommandName!!)
                            commandGroup.commands = commands
                            startOrContinueAutomation(commandGroup)
                        } else {
                            Log.e("", "Failed to resume, command group not found.")
                            stopServiceAndTask()
                        }
                    }
                }
            }

            ACTION_PAUSE -> pauseAutomation()
            ACTION_STOP -> stopServiceAndTask()
        }

        // START_STICKY 表示如果服务被系统杀死，系统会尝试重新创建服务，但不会重新传递最后一个 Intent
        return START_STICKY
    }

    private fun handleServiceRestart() {
        Log.d("", "Service was restarted by the system.")
        // 从 SharedPreferences 中恢复上一次的状态
        val lastCommandName = SettingsManager.getActiveCommandName(this)
        val lastState = SettingsManager.getCurrentRunState(this)

        // 如果上次的状态是 RUNNING 或 PAUSED，说明需要恢复任务
        if (lastCommandName != null && (lastState == RunState.RUNNING || lastState == RunState.PAUSED)) {
            Log.i("", "Attempting to restore task: $lastCommandName")
            // 异步从数据库重新加载任务信息
            serviceScope.launch {
                val commandGroup = AppDatabase.getInstance(this@AutomationService)
                    .commandGroupDao()
                    .getGroupByName(lastCommandName)

                if (commandGroup != null) {
                    // 成功加载，恢复任务
                    activeCommandName = lastCommandName
                    if (lastState == RunState.RUNNING) {
                        startOrContinueAutomation(commandGroup)
                    } else { // PAUSED
                        // 如果是暂停状态，只需更新UI和状态，不启动协程
                        currentState = RunState.PAUSED
                        updateNotification()
                        ///broadcastStateUpdate()
                    }
                } else {
                    Log.e("", "Failed to restore task. CommandGroup not found in DB.")
                    stopServiceAndTask() // 找不到任务信息，直接停止
                }
            }
        } else {
            // 没有需要恢复的任务，服务可以安全停止
            stopSelf()
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startOrContinueAutomation(commandGroup: CommandGroup) {
        FloatingLogService.log(this, "开启任务")
        if (currentState == RunState.RUNNING) {
            Log.d(
                "com.lucky.appautomation.service.AutomationService",
                "Task is already running. Ignoring start command."
            )
            return
        }

        if (SettingsManager.getFloatWindow(this)) {
            // 【修改】发送“显示悬浮窗”的命令
            val showIntent = Intent(this, FloatingLogService::class.java).apply {
                action = FloatingLogService.ACTION_SHOW
            }
            startService(showIntent)
        }

        currentState = RunState.RUNNING
        SettingsManager.saveRunningState(this, activeCommandName, currentState)
        TaskStateManager.updateState(activeCommandName, currentState)

        // 将服务提升为前台服务，并显示通知
        startForeground(NOTIFICATION_ID, createNotification())

        automationJob = serviceScope.launch {
            Log.d(
                "com.lucky.appautomation.service.AutomationService", "Automation coroutine started."
            )
            var commandIndex = 0
            while (isActive) { // 使用 isActive 来检查协程是否应该继续运行

                MyAccessibilityService.instance?.performCommand(
                    commandGroup = commandGroup, commandIndex
                )

                // 移动到下一个指令，或从头开始循环
                commandIndex = (commandIndex + 1) % commandGroup.commands?.size!!

                if ((commandIndex % commandGroup.commands?.size!!) == 0 && commandIndex != 0) {
                    if (!commandGroup.isLoop) {
                        stopServiceAndTask()
                    }
                }

                if (commandGroup.isRandom) {
                    val randomValue = getRandomIntInRange(
                        commandGroup.loopDelay.split("-")[0].toInt(),
                        commandGroup.loopDelay.split("-")[1].toInt()
                    )
                    delay(randomValue + commandGroup.spendTime.toLong()) // 步骤之间的延迟
                } else {
                    delay(commandGroup.loopDelay.toLong() + commandGroup.spendTime.toLong()) // 步骤之间的延迟
                }
            }
            Log.d(
                "com.lucky.appautomation.service.AutomationService",
                "Automation coroutine finished."
            )
        }
    }

    private fun pauseAutomation() {
        FloatingLogService.log(this, "任务暂停")
        if (currentState != RunState.RUNNING) return

        currentState = RunState.PAUSED
        SettingsManager.saveRunningState(this, activeCommandName, currentState)
        TaskStateManager.updateState(activeCommandName, currentState)

        automationJob?.cancel() // 取消正在执行的协程任务
        updateNotification()    // 更新通知UI
        Log.d("com.lucky.appautomation.service.AutomationService", "Task paused.")
    }

    private fun stopServiceAndTask() {
        FloatingLogService.log(this, "任务结束")

        // 【修改】发送“隐藏悬浮窗”的命令
        val hideIntent = Intent(this, FloatingLogService::class.java).apply {
            action = FloatingLogService.ACTION_HIDE
        }
        startService(hideIntent)

        currentState = RunState.IDLE
        SettingsManager.saveRunningState(this, null, currentState)
        TaskStateManager.updateState(null, currentState)

        automationJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE) // 停止前台服务并移除通知
        stopSelf() // 停止服务自身
        Log.d("com.lucky.appautomation.service.AutomationService", "Service and task stopped.")
    }

    /**
     * 创建并返回一个使用 MediaStyle 的前台服务通知。
     * 该通知会自动适配系统的浅色/深色主题，并将操作按钮显示在右侧。
     */
    private fun createNotification(): Notification {

        val statusText = if (currentState == RunState.RUNNING) "运行中" else "已暂停"

        // --- 1. 创建所有交互所需的 PendingIntent ---

        // 点击通知主体时，返回到 MainActivity
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // “暂停”按钮的 PendingIntent
        val pausePendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // “继续”按钮的 PendingIntent
        val continuePendingIntent = PendingIntent.getService(
            this, 2,
            // 【重要】使用唯一的 Action 来区分
            Intent(this, AutomationService::class.java).apply {
                action = Companion.ACTION_CONTINUE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // “结束”按钮的 PendingIntent
        val stopPendingIntent = PendingIntent.getService(
            this, 3,
            Intent(this, AutomationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        // --- 2. 【核心】创建 RemoteViews 并绑定数据和事件 ---

        // --- 2. 【核心修改】创建 RemoteViews 并绑定到新布局 ---
        val customView = RemoteViews(packageName, R.layout.custom_notification_media_style).apply {
            // 绑定标题和状态文本
            setTextViewText(R.id.tv_notification_title, activeCommandName ?: "自动化任务")
            setTextViewText(R.id.tv_notification_status, statusText)

            // 【新】根据状态绑定“暂停/继续”按钮的图标、文字和点击事件
            if (currentState == RunState.RUNNING) {
                // 设置图标和文字为“暂停”
                setImageViewResource(R.id.iv_pause_continue, R.drawable.ic_pause)
                setTextViewText(R.id.tv_pause_continue, "暂停")
                // 将点击事件绑定到外层 LinearLayout
                setOnClickPendingIntent(R.id.btn_notification_pause_continue, pausePendingIntent)
            } else { // PAUSED 状态
                // 设置图标和文字为“继续”
                setImageViewResource(R.id.iv_pause_continue, R.drawable.ic_play_arrow)
                setTextViewText(R.id.tv_pause_continue, "继续")
                // 将点击事件绑定到外层 LinearLayout
                setOnClickPendingIntent(R.id.btn_notification_pause_continue, continuePendingIntent)
            }

            // 始终将结束按钮的点击事件绑定到外层 LinearLayout
            setOnClickPendingIntent(R.id.btn_notification_stop, stopPendingIntent)
        }
        // --- 3. 【核心】构建最终的 Notification ---

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round) // 必须！这是显示在状态栏左上角的小图标
            .setOngoing(true)
            .setContentIntent(activityPendingIntent) // 设置通知主体（除按钮外）的点击事件

            // 【关键步骤】应用自定义视图
            .setCustomContentView(customView)
            .setCustomBigContentView(customView) // 为了在展开时也保持一致，使用同一个视图
            .setPriority(NotificationManager.IMPORTANCE_HIGH)

            // 【重要】使用这个样式来包装自定义视图，它会自动处理背景、圆角等
            // 使你的自定义布局看起来更像一个原生通知
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    /**
     * 辅助方法，用于更新通知
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "自动化任务状态",
            NotificationManager.IMPORTANCE_HIGH // 设置为 LOW，避免声音和振动，只在状态栏显示图标
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // 当服务销毁时，取消所有在该作用域中启动的协程
        Log.d("com.lucky.appautomation.service.AutomationService", "Service Destroyed")
    }

    /**
     * 生成 [min, max] 之间的随机整数（包含 min 和 max 本身）
     * @param min 最小值（闭区间）
     * @param max 最大值（闭区间）
     * @return 随机整数
     */
    fun getRandomIntInRange(min: Int, max: Int): Int {
        // 校验参数合法性：确保 min <= max，否则抛出异常
        require(min <= max) { "最小值不能大于最大值：min = $min, max = $max" }
        // nextInt(range) 生成 [0, range) 的随机数，加上 min 后映射到 [min, max]
        return min + Random.nextInt(max - min + 1)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}