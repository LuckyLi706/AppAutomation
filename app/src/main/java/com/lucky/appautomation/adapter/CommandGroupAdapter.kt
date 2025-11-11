import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lucky.appautomation.AutomationApplication
import com.lucky.appautomation.activity.AddCommandActivity
import com.lucky.appautomation.databinding.ListItemCommandGroupBinding
import com.lucky.appautomation.db.model.CommandGroup

/**
 * 定义指令组的运行状态
 */
enum class RunState {
    IDLE,       // 空闲 (显示“启动”)
    RUNNING,    // 运行中 (显示“暂停”和“结束”)
    PAUSED      // 已暂停 (显示“继续”和“结束”)
}

class CommandGroupAdapter :
    ListAdapter<CommandGroup, CommandGroupAdapter.CommandGroupViewHolder>(CommandGroupDiffCallback()) {

    // 回调接口，用于将点击事件通知给 Activity/Fragment
    interface InteractionCallback {
        fun onStartClicked(commandGroup: CommandGroup)
        fun onPauseClicked(commandGroup: CommandGroup)
        fun onContinueClicked(commandGroup: CommandGroup)
        fun onStopClicked(commandGroup: CommandGroup)
        fun onDeleteClicked(commandGroup: CommandGroup, index: Int)
    }

    private var callback: InteractionCallback? = null

    // 状态管理变量
    private var activeCommandName: String? = null
    private var currentState: RunState = RunState.IDLE

    // 设置回调的方法
    fun setInteractionCallback(callback: InteractionCallback) {
        this.callback = callback
    }

    /**
     * 公开方法，用于从外部（如Activity）同步状态，并刷新UI
     */
    fun updateState(commandName: String?, newState: RunState) {
        val oldActiveName = activeCommandName
        val oldState = currentState

        activeCommandName = commandName
        currentState = newState

        // 如果状态没有实际变化，则不执行任何操作
        if (oldActiveName == activeCommandName && oldState == currentState) {
            return
        }

        // 刷新之前处于活动状态的项
        if (oldActiveName != null && oldActiveName != activeCommandName) {
            notifyItemByName(oldActiveName)
        }
        // 刷新当前活动状态的项
        activeCommandName?.let {
            notifyItemByName(it)
        }
    }

    /**
     * 根据指令组名称找到其位置并刷新
     */
    private fun notifyItemByName(name: String) {
        val position = currentList.indexOfFirst { it.name == name }
        if (position != -1) {
            notifyItemChanged(position, PAYLOAD_STATE_CHANGE)
        }
    }

    inner class CommandGroupViewHolder(private val binding: ListItemCommandGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * 主绑定方法，根据状态分发到不同设置函数
         */
        fun bind(commandGroup: CommandGroup, index: Int) {
            // 绑定静态信息，如标题和循环状态
            bindStaticInfo(commandGroup)

            // 根据是否为活动项，决定其当前状态 (IDLE, RUNNING, or PAUSED)
            val stateForThisItem = if (commandGroup.name == activeCommandName) {
                currentState
            } else {
                RunState.IDLE
            }

            // 根据状态更新UI和设置监听器
            updateUiForState(stateForThisItem, commandGroup, index)

            binding.root.setOnClickListener {
                if (stateForThisItem != RunState.IDLE) {
                    Toast.makeText(
                        AutomationApplication.context,
                        "Item is running",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val intent =
                        Intent(AutomationApplication.context, AddCommandActivity::class.java)
                    intent.putExtra("groupName", commandGroup.name)
                    intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                    AutomationApplication.context.startActivity(intent)
                }
            }
        }

        /**
         * 绑定不会随运行状态变化的信息
         */
        private fun bindStaticInfo(commandGroup: CommandGroup) {
            binding.tvCommandTitle.text = commandGroup.name
            binding.tvLoopStatus.text = if (commandGroup.isLoop) "循环执行" else "执行一次"
        }

        /**
         * 【核心修改】根据状态更新UI，并只在需要时设置点击监听器
         */
        fun updateUiForState(state: RunState, commandGroup: CommandGroup, index: Int) {
            // 根据当前 item 的状态，配置正确的按钮和事件
            when (state) {
                RunState.IDLE -> setupIdleState(state, commandGroup, index)
                RunState.RUNNING -> setupRunningState(commandGroup, index)
                RunState.PAUSED -> setupPausedState(commandGroup, index)
            }
        }

        /**
         * 状态一：空闲。显示“启动”按钮。
         */
        private fun setupIdleState(state: RunState, commandGroup: CommandGroup, index: Int) {
            binding.btnStartCommand.visibility = View.VISIBLE
            binding.btnDeleteCommand.visibility = View.VISIBLE
            binding.groupRunningButtons.visibility = View.GONE
            binding.groupPausedButtons.visibility = View.GONE

            binding.btnStartCommand.setOnClickListener {

                if (TaskStateManager.taskStateLiveData.value?.runState != RunState.IDLE) {
                    Toast.makeText(
                        AutomationApplication.context,
                        "请先停止上一个任务",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                callback?.onStartClicked(commandGroup)
            }
            binding.btnDeleteCommand.setOnClickListener {
                callback?.onDeleteClicked(commandGroup, index)
            }
        }

        /**
         * 状态二：运行中。显示“暂停”和“结束”按钮。
         */
        private fun setupRunningState(commandGroup: CommandGroup, index: Int) {
            binding.btnStartCommand.visibility = View.GONE
            binding.btnDeleteCommand.visibility = View.GONE
            binding.groupRunningButtons.visibility = View.VISIBLE
            binding.groupPausedButtons.visibility = View.GONE

            binding.btnPauseCommand.setOnClickListener { callback?.onPauseClicked(commandGroup) }
            binding.btnStopRunningCommand.setOnClickListener { callback?.onStopClicked(commandGroup) }
        }

        /**
         * 状态三：已暂停。显示“继续”和“结束”按钮。
         */
        private fun setupPausedState(commandGroup: CommandGroup, index: Int) {
            binding.btnStartCommand.visibility = View.GONE
            binding.btnDeleteCommand.visibility = View.GONE
            binding.groupRunningButtons.visibility = View.GONE
            binding.groupPausedButtons.visibility = View.VISIBLE

            binding.btnContinueCommand.setOnClickListener { callback?.onContinueClicked(commandGroup) }
            binding.btnStopPausedCommand.setOnClickListener { callback?.onStopClicked(commandGroup) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandGroupViewHolder {
        val binding =
            ListItemCommandGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommandGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommandGroupViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    /**
     * 【性能优化】使用带 payload 的 onBindViewHolder
     * 当只有状态更新时，我们只更新UI，而不重新绑定所有数据
     */
    override fun onBindViewHolder(
        holder: CommandGroupViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_STATE_CHANGE)) {
            val commandGroup = getItem(position)
            val state = if (commandGroup.name == activeCommandName) currentState else RunState.IDLE
            holder.updateUiForState(state, commandGroup, position)
        } else {
            // 如果没有 payload，执行全量绑定
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // DiffUtil 使用 name 作为唯一标识，并比较内容
    class CommandGroupDiffCallback : DiffUtil.ItemCallback<CommandGroup>() {
        override fun areItemsTheSame(oldItem: CommandGroup, newItem: CommandGroup): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: CommandGroup, newItem: CommandGroup): Boolean {
            // 比较数据内容是否相同，如果CommandGroup是data class，这会自动实现
            return oldItem == newItem
        }
    }

    companion object {
        // 定义一个 payload，用于局部刷新
        private const val PAYLOAD_STATE_CHANGE = "STATE_CHANGE"
    }
}