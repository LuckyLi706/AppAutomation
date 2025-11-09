package com.lucky.appautomation.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.lucky.appautomation.databinding.ListItemSingleCommandBinding
import com.lucky.appautomation.db.model.Command

class SingleCommandAdapter(private val context: Context, private val steps: MutableList<Command>) :
    RecyclerView.Adapter<SingleCommandAdapter.CommandStepViewHolder>() {

    inner class CommandStepViewHolder(val binding: ListItemSingleCommandBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dropdownAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_dropdown_item_1line,
            listOf("SWIPE", "CLICK", "INPUT") // 确保这里的字符串和 when 判断里的一致
        )

        // 【新】为每一个 EditText 创建一个 TextWatcher 变量
        // 这样做是为了在复用时能够移除它们，防止监听器错乱
        private val textWatchers = mutableListOf<Pair<EditText, TextWatcher>>()

        init {
            binding.dropdownCommandType.setAdapter(dropdownAdapter)
        }

        fun bind(step: Command) {
            // =================================================================
            // 【关键步骤 1】: 移除旧的监听器，防止复用时发生混乱
            // =================================================================
            removeAllWatchers()

            // =================================================================
            // 【关键步骤 2】: 将数据模型 (step) 的内容设置到 UI 控件上
            // =================================================================
            binding.dropdownCommandType.setText(step.commandName, false)
            // SWIPE
            binding.etSwipeStartX.setText(step.startX)
            binding.etSwipeStartY.setText(step.startY)
            binding.etSwipeEndX.setText(step.endX)
            binding.etSwipeEndY.setText(step.endY)
            // CLICK
            binding.etClickX.setText(step.startX)
            binding.etClickY.setText(step.startY)
            // INPUT
            binding.etInputText.setText(step.text)

            updateVisibility(step.commandName)

            // =================================================================
            // 【关键步骤 3】: 为 UI 控件设置新的监听器
            // =================================================================
            // 下拉框选择监听
            binding.dropdownCommandType.setOnItemClickListener { parent, _, position, _ ->
                val selectedType = parent.getItemAtPosition(position).toString()
                // 确保 position 有效再操作
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    steps[adapterPosition].commandName = selectedType // 更新数据模型
                    updateVisibility(selectedType) // 更新UI
                }
            }

            // 为所有 EditText 添加 TextWatcher
            addWatcher(binding.etSwipeStartX) { s -> steps[adapterPosition].startX = s }
            addWatcher(binding.etSwipeStartY) { s -> steps[adapterPosition].startY = s }
            addWatcher(binding.etSwipeEndX) { s -> steps[adapterPosition].endX = s }
            addWatcher(binding.etSwipeEndY) { s -> steps[adapterPosition].endY = s }
            addWatcher(binding.etClickX) { s -> steps[adapterPosition].startX = s }
            addWatcher(binding.etClickY) { s -> steps[adapterPosition].startY = s }
            addWatcher(binding.etInputText) { s -> steps[adapterPosition].text = s }
        }

        // 辅助方法：更新UI可见性
        private fun updateVisibility(type: String) {
            binding.groupSwipe.visibility = if (type == "SWIPE") View.VISIBLE else View.GONE
            binding.groupClick.visibility = if (type == "CLICK") View.VISIBLE else View.GONE
            binding.groupInput.visibility = if (type == "INPUT") View.VISIBLE else View.GONE
        }

        // 【新】辅助方法：创建一个 TextWatcher 并附加到 EditText 上
        private fun addWatcher(editText: EditText, onTextChanged: (String) -> Unit) {
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // 确保 position 有效
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onTextChanged(s.toString())
                    }
                }
            }
            editText.addTextChangedListener(watcher)
            // 将 EditText 和它的 Watcher 保存起来，以便后续移除
            textWatchers.add(editText to watcher)
        }

        // 【新】辅助方法：移除所有已添加的 TextWatcher
        private fun removeAllWatchers() {
            textWatchers.forEach { (editText, watcher) ->
                editText.removeTextChangedListener(watcher)
            }
            textWatchers.clear()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandStepViewHolder {
        val binding =
            ListItemSingleCommandBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommandStepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommandStepViewHolder, position: Int) {
        holder.bind(steps[position])
    }

    override fun getItemCount(): Int = steps.size

    fun addStep(command: Command) {
        steps.add(command)
        notifyItemInserted(steps.size - 1)
    }
}