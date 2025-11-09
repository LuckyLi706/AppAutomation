import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

// 数据类保持不变
data class TaskState(
    val commandName: String?,
    val runState: RunState
)

object TaskStateManager {

    // 【核心修改】使用 MutableLiveData
    private val _taskStateLiveData = MutableLiveData<TaskState>()

    // 暴露一个不可变的 LiveData 供外部观察
    val taskStateLiveData: LiveData<TaskState> = _taskStateLiveData

    /**
     * 更新状态的方法，Service会调用它。
     * LiveData 需要在主线程更新，如果 service 在后台线程改变状态，
     * 需要使用 postValue。
     */
    fun updateState(commandName: String?, runState: RunState) {
        // 使用 postValue 可以在任何线程安全地更新 LiveData
        _taskStateLiveData.postValue(TaskState(commandName, runState))
    }

    /**
     * 从 SharedPreferences 初始化状态
     */
    fun initState(context: Context) {
        val name = SettingsManager.getActiveCommandName(context)
        val state = SettingsManager.getCurrentRunState(context)
        // 初始值可以直接用 value，因为它在主线程被调用
        _taskStateLiveData.value = TaskState(name, state)
    }
}