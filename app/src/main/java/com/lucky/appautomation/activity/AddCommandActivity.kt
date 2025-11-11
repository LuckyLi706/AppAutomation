package com.lucky.appautomation.activity

import SettingsManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.lucky.appautomation.AutomationApplication.Companion.context
import com.lucky.appautomation.adapter.SingleCommandAdapter
import com.lucky.appautomation.databinding.ActivityAddCommandBinding // 确保导入你的 ViewBinding 文件
import com.lucky.appautomation.db.AppDatabase
import com.lucky.appautomation.db.model.Command
import com.lucky.appautomation.db.model.CommandGroup
import com.lucky.appautomation.enum.CommandType
import com.lucky.appautomation.model.PackageInfoModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AddCommandActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCommandBinding
    private lateinit var singleCommandAdapter: SingleCommandAdapter
    private var stepsList = mutableListOf<Command>()
    private val packageInfoList = mutableListOf<PackageInfoModel>()
    private lateinit var dropdownAdapter: ArrayAdapter<String>
    private lateinit var group: CommandGroup
    private var packageIndex: Int = 0
    private var packageInfo = mutableListOf<String>()

    /// 是否来自于主页的item
    public var groupName: String = ""


    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCommandBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 设置 Toolbar
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 2. 初始化 RecyclerView
        setupRecyclerView()

        // 3. 设置底部按钮的点击事件
        setupClickListeners()

        setupPackageInfo()

        GlobalScope.launch(Dispatchers.IO) {
            iniIntentData()
        }
    }

    private suspend fun iniIntentData() {
        val intent = intent
        val groupName = intent.getStringExtra("groupName")
        groupName?.let { it ->
            this.groupName = groupName
            val groupData = AppDatabase.getInstance(this).commandGroupDao().getGroupByName(it)
            groupData?.let {
                val commands =
                    AppDatabase.getInstance(this).commandDao().getCommandsByGroupName(groupName)
                stepsList = commands.toMutableList()
                runOnUiThread {
                    group = groupData
                    if (group.filterPackageName.isNotEmpty()) {
                        this.packageInfoList.forEach {
                            if (it.packageName == group.filterPackageName) {
                                this.packageIndex = this.packageInfoList.indexOf(it) + 1
                                return@forEach
                            }
                        }
                    }
                    binding.etCommandName.setText(groupName)
                    binding.dropdownPackage.setText(packageInfo[this.packageIndex], false)
                    singleCommandAdapter.addSteps(stepsList)
                }
            }
        }
    }

    private fun setupPackageInfo() {
        this.getThirdPartyApps()
        packageInfoList.forEach {
            packageInfo.add("${it.appName}(${it.packageName})")
        }
        packageInfo.add(0, "不指定")
        dropdownAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            packageInfo // 确保这里的字符串和 when 判断里的一致
        )
        binding.dropdownPackage.setAdapter(dropdownAdapter)
        binding.dropdownPackage.setText(packageInfo[0], false)
        binding.dropdownPackage.setOnItemClickListener { parent, _, position, _ ->
            packageIndex = position
        }
    }

    // 封装 RecyclerView 的初始化逻辑
    private fun setupRecyclerView() {
        // 创建适配器实例
        singleCommandAdapter = SingleCommandAdapter(this, stepsList)

        // 找到 RecyclerView 控件并进行设置
        binding.rvCommandSteps.apply {
            adapter = singleCommandAdapter
            layoutManager = LinearLayoutManager(this@AddCommandActivity)
        }
    }

    // 新增：处理底部按钮的点击事件
    private fun setupClickListeners() {
        // "添加步骤" 按钮
        binding.btnAddStep.setOnClickListener {
            addStepToList()
        }

        // "保存指令" 按钮
        binding.btnSaveCommand.setOnClickListener {
            // 在这里处理保存逻辑
            // 例如：获取指令名称，遍历 stepsList 里的所有数据，然后存入数据库或文件
            val commandName = binding.etCommandName.text.toString()
            if (commandName.isBlank()) {
                Toast.makeText(this, "请输入指令名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (stepsList.isEmpty()) {
                Toast.makeText(this, "请至少添加一个指令", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            for ((index, command) in stepsList.withIndex()) {
                when (command.commandName) {
                    CommandType.SWIPE.name -> {
                        if (command.startX.isBlank() || command.startY.isBlank() || command.endX.isBlank() || command.endY.isBlank()) {
                            Toast.makeText(this, "滑动指令输入内容不能为空", Toast.LENGTH_SHORT)
                                .show()
                            return@setOnClickListener
                        }
                    }

                    CommandType.CLICK.name -> {
                        if (command.startX.isBlank() || command.startY.isBlank()) {
                            Toast.makeText(this, "点击指令输入内容不能为空", Toast.LENGTH_SHORT)
                                .show()
                            return@setOnClickListener
                        }
                    }

                    CommandType.SWIPE.name -> {
                        if (command.text.isBlank()) {
                            Toast.makeText(this, "文本指令输入内容不能为空", Toast.LENGTH_SHORT)
                                .show()
                            return@setOnClickListener
                        }
                    }
                }
            }
            // 在实际应用中，这里会执行真正的保存操作
            showLoopConfirmationDialog()
        }
    }

    // 新增：将添加步骤的逻辑提取成一个方法
    private fun addStepToList() {
        singleCommandAdapter.addStep(
            Command(
                commandName = CommandType.SWIPE.name,
                groupName = binding.etCommandName.text.toString()
            )
        )
        // 滚动到列表末尾，确保新添加的项可见
        binding.rvCommandSteps.smoothScrollToPosition(singleCommandAdapter.itemCount - 1)
    }

    // 5. 处理菜单项的点击事件
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // 返回按钮
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 【3. 新增方法：显示确认循环的对话框】
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun showLoopConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("循环执行") // 设置弹窗标题
            .setMessage("是否需要循环执行此组指令？") // 设置弹窗主文案
            .setPositiveButton("是，循环执行") { dialog, which ->
                // 用户点击了“是”
                // 在这里执行“循环”的保存逻辑
                GlobalScope.launch {
                    saveCommands(isLooping = true)
                }
                dialog.dismiss() // 关闭对话框
            }
            .setNegativeButton("否，执行一次") { dialog, which ->
                // 用户点击了“否”
                // 在这里执行“不循环”的保存逻辑
                GlobalScope.launch {
                    saveCommands(isLooping = false)
                }
                dialog.dismiss() // 关闭对话框
            }
            .setCancelable(true) // (可选) 设置为 true 允许用户通过点击外部或返回键取消对话框
            .show() // 显示弹窗
    }

    /**
     * 【4. 新增方法：封装最终的保存逻辑】
     * @param isLooping 用户是否选择了循环
     */
    private suspend fun saveCommands(isLooping: Boolean) {
        val groupName = binding.etCommandName.text.toString().trim()
        val settings = SettingsManager.getCommandDelay(this)

        val groupCommand = CommandGroup(
            name = groupName,
            isLoop = isLooping,
            isRandom = settings.isRandom,
            loopDelay = if (settings.isRandom) (settings.min.toString() + "-" + settings.max.toString()) else settings.fixed.toString(),
            spendTime = SettingsManager.getSpendTime(this).toString(),
            filterPackageName = if (packageIndex == 0) "" else packageInfoList[packageIndex - 1].packageName
        )
        AppDatabase.getInstance(this).commandGroupDao().insert(groupCommand)

        // 1. 更新所有步骤的 groupName
        stepsList.forEach { it.groupName = groupName }

        // 2. 在这里，你可以将 isLooping 这个布尔值与指令组一起保存
        // 例如，你的指令组数据模型可能有一个 `loop: Boolean` 字段

        stepsList.forEach {
            AppDatabase.getInstance(this).commandDao().insert(it)
        }

        // 在实际应用中，这里会执行真正的数据库或文件保存操作
        // e.g., viewModel.saveCommandGroup(groupName, stepsList, isLooping)

        runOnUiThread {
            val loopStatusText = if (isLooping) "循环执行" else "执行一次"
            Toast.makeText(this, "指令 '$groupName' ($loopStatusText) 已保存!", Toast.LENGTH_SHORT)
                .show()
        }
        // 保存成功后关闭页面
        setResult(RESULT_OK)
        finish()
    }

    /**
     * 【核心代码】重写 dispatchTouchEvent 方法，实现点击空白处隐藏键盘的功能。
     *
     * @param ev 触摸事件对象
     * @return 事件是否被消费
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // 只在触摸事件为“按下”时触发判断
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            // 获取当前获得焦点的视图
            val v = currentFocus

            // 判断当前焦点是否是 EditText
            if (v is EditText) {
                val outRect = Rect()
                // 获取这个 EditText 在屏幕上的可见区域
                v.getGlobalVisibleRect(outRect)

                // 判断用户点击的位置（ev.rawX, ev.rawY）是否在 EditText 的区域内
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    // 如果点击的不是 EditText，则清除焦点
                    v.clearFocus()
                    // 获取输入法管理器服务
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    // 隐藏软键盘
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        // 调用父类的实现，确保事件继续分发
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 获取所有第三方应用（排除系统应用）
     * @return 包含第三方应用名称和包名的列表
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun getThirdPartyApps() {
        val pm: PackageManager = context.packageManager
        // 1. 获取所有已安装的应用信息
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in allApps) {
            // 2. 核心过滤逻辑：判断是否为第三方应用
            // (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 表示它不是一个系统应用。
            // (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 表示它是可更新的系统应用（如 Chrome, Gmail），我们也算作第三方。
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {

                // 3. 过滤掉自身应用
                if (appInfo.packageName == this.packageName) {
                    continue
                }

                // 4. 获取应用的名称和图标
                val appName = appInfo.loadLabel(pm).toString()
                val icon = appInfo.loadIcon(pm)

                packageInfoList.add(PackageInfoModel(appName, appInfo.packageName))

            }
        }
    }
}