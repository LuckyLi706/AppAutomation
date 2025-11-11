package com.lucky.appautomation.activity

import CommandGroupAdapter
import RunState
import SettingsManager
import TaskStateManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lucky.appautomation.AutomationApplication
import com.lucky.appautomation.R
import com.lucky.appautomation.databinding.ActivityMainBinding
import com.lucky.appautomation.db.AppDatabase
import com.lucky.appautomation.db.model.CommandGroup
import com.lucky.appautomation.service.AutomationService
import com.lucky.appautomation.utils.AccessibilityUtils
import com.lucky.appautomation.utils.ServiceUtils.Companion.isServiceRunning
import com.yanzhenjie.recyclerview.OnItemMenuClickListener
import com.yanzhenjie.recyclerview.SwipeMenuCreator
import com.yanzhenjie.recyclerview.SwipeMenuItem
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max


class MainActivity : AppCompatActivity(), CommandGroupAdapter.InteractionCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var commandAdapter: CommandGroupAdapter
    private lateinit var commandGroupFlow: Flow<List<CommandGroup>>
    private lateinit var commandGroupList List<CommandGroup>


    companion object {
        // 【新增】定义一个Action，用于从通知栏触发继续任务
        const val ACTION_CONTINUE_TASK = "com.lucky.automation.main.ACTION_CONTINUE"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 【新增】处理从通知栏发来的 "继续" Intent
        if (intent.action == ACTION_CONTINUE_TASK) {
            val commandName = intent.getStringExtra(AutomationService.EXTRA_COMMAND_NAME)
            if (commandName != null) {
                // 找到对应的 CommandGroupWithCommands 并调用 onContinueClicked
                lifecycleScope.launch {
                    val group = AppDatabase.getInstance(this@MainActivity)
                        .commandGroupDao()
                        .getGroupByName(commandName)
                    group?.let {
                        onContinueClicked(group)
                    }
                }
            }
        }
    }

//    // 【新增】广播接收器，用于接收来自 Service 的状态更新
//    private val automationStateReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            if (intent?.action == AutomationService.BROADCAST_STATE_UPDATE) {
//                val commandName = intent?.getStringExtra("COMMAND_NAME")
//                // 使用 to-do-replace-with-actual-enum-parsing-logic
//                val stateOrdinal = intent?.getIntExtra("STATE", RunState.IDLE.ordinal)
//                val newState = RunState.values().getOrNull(stateOrdinal) ?: RunState.IDLE
//
//                Log.d(
//                    "com.lucky.appautomation.activity.MainActivity",
//                    "Received broadcast: Name=$commandName, State=$newState"
//                )
//
//                // 【核心】调用 Adapter 的方法来同步 UI
//                commandAdapter.updateState(commandName, newState)
//            }
//        }
//    }

    // 【新增】创建权限请求的 Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 用户同意了权限
                Toast.makeText(this, "通知权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                // 用户拒绝了权限，可以给一个提示，说明功能会受限
                Toast.makeText(this, "未开启通知权限，服务状态将无法显示", Toast.LENGTH_LONG).show()
            }
        }

    // 注册一个 Activity Result Launcher 来处理从设置页面返回的结果
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 当用户从设置页返回后，再次检查权限
        if (checkOverlayPermission()) {
            Toast.makeText(this, "悬浮窗权限已获取", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "悬浮窗权限未开启", Toast.LENGTH_SHORT).show()
        }
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // 当从 AddCommandActivity 返回时，Flow 会自动发出新数据，UI 会自动更新，
                // 所以这里理论上不需要手动重新赋值 Flow，但保留亦无害。
                commandGroupFlow =
                    AppDatabase.getInstance(this).commandGroupDao().getAllGroups()
                Log.d(
                    "com.lucky.appautomation.activity.MainActivity",
                    "Returned from AddCommandActivity with RESULT_OK"
                )
            }
        }

    /**
     * 【新增/替换】观察 TaskStateManager 的 LiveData
     */
    private fun observeTaskState() {
        TaskStateManager.taskStateLiveData.observe(this, Observer { taskState ->
            // LiveData 的值发生变化时，这个代码块会被调用
            if (taskState != null) {
                commandAdapter.updateState(taskState.commandName, taskState.runState)
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化数据流
        commandGroupFlow =
            AppDatabase.getInstance(this).commandGroupDao().getAllGroups()

        // 设置 RecyclerView 和 Adapter
        setupRecyclerView()

        // 订阅数据流以更新 UI
        observeAndUpdateUi()

        // 设置其他 UI 组件的监听器
        setupUIListeners()

        askNotificationPermission()

        /// 检测服务是否被杀死
        if (!applicationContext.isServiceRunning(AutomationService::class)) {
            SettingsManager.saveRunningState(this, null, RunState.IDLE)
            TaskStateManager.updateState(null, RunState.IDLE)
        }

        observeTaskState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 【新增】在 Activity 销毁时，注销广播接收器以防内存泄漏
        //unregisterReceiver(automationStateReceiver)
    }

    private fun setupRecyclerView() {
        commandAdapter = CommandGroupAdapter()
        // 【第5步】将 Activity 自身设置为回调
        commandAdapter.setInteractionCallback(this)

        binding.recyclerView.adapter = commandAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeAndUpdateUi() {
        lifecycleScope.launch {
            commandGroupFlow.collectLatest { freshListOfGroups ->
                Log.d(
                    "com.lucky.appautomation.activity.MainActivity",
                    "Flow emitted new list with ${freshListOfGroups.size} items."
                )
                commandGroupList = freshListOfGroups as List<CommandGroup>
                for (item in commandGroupList) {
                    val commands =
                        AppDatabase.getInstance(AutomationApplication.context).commandDao()
                            .getCommandsByGroupName(item.name)
                    (item as CommandGroup).commands = commands
                }

                commandAdapter.submitList(freshListOfGroups)
            }
        }
    }

    private fun setupUIListeners() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }

                else -> false
            }
        }

        binding.fabAddCommand.setOnClickListener {
            startForResult.launch(Intent(this, AddCommandActivity::class.java))
        }
    }

    // --- 【第6步】实现接口的所有方法 ---

    @OptIn(DelicateCoroutinesApi::class)
    override fun onStartClicked(groupWithCommands: CommandGroup) {
        // 在 Activity 中检查权限
        if (AccessibilityUtils.isServiceEnabled(this)) {
            if (SettingsManager.getFloatWindow(this)) {
                if (!checkOverlayPermission()) {
                    requestOverlayPermission()
                    return
                }
            }
            // 权限已开启，启动服务
            GlobalScope.launch {
                val commands =
                    AppDatabase.getInstance(AutomationApplication.context).commandDao()
                        .getCommandsByGroupName(groupWithCommands.name)
                groupWithCommands.commands = commands

                startNotificationService(groupWithCommands)
            }

        } else {
            // 权限未开启，在 Activity 中显示对话框
            showAccessibilityPermissionDialog()
        }
    }

    private fun startNotificationService(groupWithCommands: CommandGroup) {
        val intent = Intent(this, AutomationService::class.java).apply {
            action = AutomationService.ACTION_START
            putExtra(AutomationService.EXTRA_COMMAND_LIST, groupWithCommands)
            putExtra(AutomationService.EXTRA_COMMAND_NAME, groupWithCommands.name)
        }
        startService(intent)
        runOnUiThread {
            commandAdapter.updateState(groupWithCommands.name, RunState.RUNNING)
        }
    }

    override fun onPauseClicked(groupWithCommands: CommandGroup) {
        val intent = Intent(this, AutomationService::class.java).apply {
            action = AutomationService.ACTION_PAUSE
        }
        startService(intent)
        commandAdapter.updateState(groupWithCommands.name, RunState.PAUSED)
    }

    override fun onContinueClicked(groupWithCommands: CommandGroup) {
        // "继续"也需要检查权限，可以直接复用 onStartClicked 的逻辑
        onStartClicked(groupWithCommands)
    }

    override fun onStopClicked(groupWithCommands: CommandGroup) {
        val intent = Intent(this, AutomationService::class.java).apply {
            action = AutomationService.ACTION_STOP
        }
        startService(intent)
        commandAdapter.updateState(null, RunState.IDLE)
    }

    override fun onDeleteClicked(commandGroup: CommandGroup, index: Int) {
        this.showDeleteDialog(commandGroup.name, index)
    }

    /**
     * 这个方法现在属于 Activity，使用 this 作为上下文，绝不会再崩溃。
     */
    private fun showAccessibilityPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("需要开启辅助功能")
            .setMessage("为了执行自动滑动、点击等操作，应用需要您在系统设置中手动开启“${getString(R.string.app_name)}”服务。")
            .setPositiveButton("去设置") { dialog, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(): Boolean {
        // Android 6.0 (API 23) 以上需要动态检查
        return Settings.canDrawOverlays(this)
    }

    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        if (checkOverlayPermission()) return

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun askNotificationPermission() {
        // 这个检查只在 Android 13 (API 33) 及以上版本需要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // 权限已经被授予
            } else {
                // 直接请求权限
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * 【3. 新增方法：显示确认循环的对话框】
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun showDeleteDialog(name: String, index: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除${name}") // 设置弹窗标题
            .setMessage("是否删除此组指令？，删除之后不可恢复") // 设置弹窗主文案
            .setPositiveButton("确定") { dialog, which ->
                // 用户点击了“是”
                // 在这里执行“循环”的保存逻辑
                val intent = Intent(this, AutomationService::class.java).apply {
                    action = AutomationService.ACTION_STOP
                }
                startService(intent)
                commandAdapter.updateState(null, RunState.IDLE)
                GlobalScope.launch {
                    AppDatabase.getInstance(AutomationApplication.context).commandGroupDao()
                        .delete(name)
                    runOnUiThread {
                        commandAdapter.notifyItemRemoved(index) // 通知刷新
                    }
                }


                dialog.dismiss() // 关闭对话框
            }
            .setNegativeButton("取消") { dialog, which ->
                // 用户点击了“否”
                // 在这里执行“不循环”的保存逻辑
                dialog.dismiss() // 关闭对话框
            }
            .setCancelable(true) // (可选) 设置为 true 允许用户通过点击外部或返回键取消对话框
            .show() // 显示弹窗
    }
}