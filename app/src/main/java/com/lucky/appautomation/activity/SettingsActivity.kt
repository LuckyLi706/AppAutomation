package com.lucky.appautomation.activity

import SettingsManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import com.lucky.appautomation.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var isProgrammaticChange = false // 标志位，防止代码修改UI时触发循环监听

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadSettingsAndApplyDefaults() // 加载已有设置，或应用默认值
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * 从 SettingsManager 加载所有设置。
     * SettingsManager 内部已经处理了首次加载时返回默认值的逻辑。
     */
    private fun loadSettingsAndApplyDefaults() {
        isProgrammaticChange = true // 开始代码修改，禁止监听器响应

        // 1. 加载主题设置
        // 默认值由 MyApplication 和 SettingsManager 里的 MODE_NIGHT_FOLLOW_SYSTEM 决定
        val isNightMode =
            SettingsManager.loadAndApplyTheme(this) == AppCompatDelegate.MODE_NIGHT_YES
        binding.tvThemeSubtitle.text = if (isNightMode) "夜晚模式" else "白天模式"
        binding.switchTheme.isChecked = isNightMode

        // 2. 加载启动延迟
        // 默认值 2000 由 SettingsManager.getLaunchDelay() 提供
        binding.etLaunchDelay.setText(SettingsManager.getLaunchDelay(this).toString())

        // 默认值 1000 由 SettingsManager.getLaunchDelay() 提供
        binding.etSpendTime.setText(SettingsManager.getSpendTime(this).toString())
        binding.switchFloatWindow.isChecked = SettingsManager.getFloatWindow(this)


        // 3. 加载指令延迟
        // 默认值（未选中, 固定1000, 范围1000-2000）由 SettingsManager.getCommandDelay() 提供
        val settings = SettingsManager.getCommandDelay(this)
        binding.cbRandomDelay.isChecked = settings.isRandom
        binding.etCommandDelayFixed.setText(settings.fixed.toString())
        binding.etCommandDelayMin.setText(settings.min.toString())
        binding.etCommandDelayMax.setText(settings.max.toString())

        // 根据加载的状态更新UI的可见性
        updateCommandDelayUi(settings.isRandom)

        isProgrammaticChange = false // 代码修改结束
    }

    /**
     * 为所有可交互的UI控件设置监听器
     */
    private fun setupListeners() {
        // --- 主题切换监听 ---
        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            val mode =
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.applyAndSaveTheme(this, mode)
        }
        binding.rowTheme.setOnClickListener { binding.switchTheme.toggle() }

        binding.switchFloatWindow.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            SettingsManager.saveFloatWindow(this, isChecked)
        }

        binding.rowFloatWindow.setOnClickListener { binding.switchFloatWindow.toggle() }

        // --- 启动延迟监听 ---
        binding.etLaunchDelay.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticChange) return
                // 用户清空时，保存为0
                val delay = s.toString().toIntOrNull() ?: 0
                SettingsManager.saveLaunchDelay(this@SettingsActivity, delay)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })


        binding.etSpendTime.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticChange) return
                // 用户清空时，保存为0
                val spendTime = s.toString().toIntOrNull() ?: 0
                SettingsManager.saveSpendTime(this@SettingsActivity, spendTime)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // --- 指令延迟监听 ---
        binding.cbRandomDelay.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            updateCommandDelayUi(isChecked)
            saveCommandDelaySettings() // 保存设置
        }

        // 为所有3个指令延迟输入框添加文本变化监听
        val commandDelayTextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticChange) return
                validateAndSaveCommandDelay()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.etCommandDelayFixed.addTextChangedListener(commandDelayTextWatcher)
        binding.etCommandDelayMin.addTextChangedListener(commandDelayTextWatcher)
        binding.etCommandDelayMax.addTextChangedListener(commandDelayTextWatcher)
    }

    /**
     * 根据复选框状态，更新UI的可见性
     */
    private fun updateCommandDelayUi(isRandom: Boolean) {
        binding.layoutFixedDelay.isVisible = !isRandom
        binding.layoutRandomDelayInputs.isVisible = isRandom
    }

    /**
     * 验证并保存指令延迟设置
     */
    private fun validateAndSaveCommandDelay() {
        // 仅在随机模式下才需要验证范围
        if (binding.cbRandomDelay.isChecked) {
            val min = binding.etCommandDelayMin.text.toString().toIntOrNull() ?: 0
            var max = binding.etCommandDelayMax.text.toString().toIntOrNull() ?: 0

            if (max < min) {
                max = min
                isProgrammaticChange = true // 阻止 setText 触发的循环
                binding.etCommandDelayMax.setText(max.toString())
                isProgrammaticChange = false
                binding.etCommandDelayMax.setError("必须大于等于最小值")
            } else {
                binding.etCommandDelayMax.error = null
            }
        }

        saveCommandDelaySettings()
    }

    /**
     * 一个统一的方法，用于读取UI并保存指令延迟的全部设置
     */
    private fun saveCommandDelaySettings() {
        val settings = SettingsManager.CommandDelaySettings(
            isRandom = binding.cbRandomDelay.isChecked,
            fixed = binding.etCommandDelayFixed.text.toString().toIntOrNull() ?: 0,
            min = binding.etCommandDelayMin.text.toString().toIntOrNull() ?: 0,
            max = binding.etCommandDelayMax.text.toString().toIntOrNull() ?: 0
        )
        // 修正：确保保存的min/max值在逻辑上是合理的，即使UI上没有实时更新
        val validatedMax =
            if (settings.isRandom && settings.max < settings.min) settings.min else settings.max

        SettingsManager.saveCommandDelay(
            this,
            settings.isRandom,
            settings.fixed,
            settings.min,
            validatedMax
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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
}