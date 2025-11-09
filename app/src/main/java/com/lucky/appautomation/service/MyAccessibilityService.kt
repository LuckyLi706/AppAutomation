package com.lucky.appautomation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lucky.appautomation.AutomationApplication
import com.lucky.appautomation.db.model.CommandGroup
import com.lucky.appautomation.enum.CommandType

class MyAccessibilityService : AccessibilityService() {

    companion object {
        // 使用一个伴生对象来持有服务的实例，方便其他组件调用
        var instance: MyAccessibilityService? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 我们在这里可以监听窗口变化等事件，但对于主动派发手势，这里可以为空
        if (event == null) return

        // 【核心代码】从事件对象中获取包名
        val packageName = event.packageName?.toString()

        // 我们可以只关心那些表示窗口状态或内容变化的事件，避免过于频繁的日志打印
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {

            }
        }
    }

    // ... 其他代码 ...

    /**
     * 【新增】一个公开方法，用于主动获取当前活动窗口的包名。
     * 可以在服务的其他地方，甚至从外部（如 AutomationService）调用。
     *
     * @return 当前前台应用的包名，如果获取失败则返回 null。
     */
    fun getCurrentAppPackageName(): String? {
        // getRootInActiveWindow() 返回当前活动窗口的根节点信息
        val rootNode = rootInActiveWindow

        // 如果屏幕关闭或处于某些特殊状态，可能获取不到根节点
        if (rootNode != null) {
            val packageName = rootNode.packageName?.toString()
            Log.d("AccessibilityLog", "主动查询到的包名是: $packageName")
            return packageName
        } else {
            Log.w("AccessibilityLog", "无法获取到当前活动窗口的根节点")
            return null
        }
    }

    override fun onInterrupt() {
        Log.d("AccessibilityLog", "辅助功能服务被中断")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AccessibilityLog", "辅助功能服务已连接")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("AccessibilityLog", "辅助功能服务已销毁")
    }

    fun performCommand(commandGroup: CommandGroup, index: Int = 0) {

        if (commandGroup.filterPackageName.isNotEmpty() && commandGroup.filterPackageName != this.getCurrentAppPackageName()) {
            FloatingLogService.log(
                this,
                "过滤非目标包名：${commandGroup.filterPackageName}，当前包名：${this.getCurrentAppPackageName()}"
            )
            return
        }

        val command = commandGroup.commands?.get(index)
        when (command?.commandName) {
            CommandType.SWIPE.toString() -> {
                val path = Path().apply {
                    moveTo(command.startX.toFloat(), command.startY.toFloat())
                    lineTo(command.endX.toFloat(), command.endY.toFloat())
                }
                FloatingLogService.log(
                    AutomationApplication.context,
                    "滑动操作开始，startX：${command.startX}, startY：${command.startY}, endX：${command.endX}, endY：${command.endY}"
                )
                val gesture = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0,
                            commandGroup.spendTime.toLong()
                        )
                    )
                    .build()

                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        FloatingLogService.log(AutomationApplication.context, "滑动操作成功完成")
                        Log.d("AccessibilityLog", "滑动操作成功完成")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.d("AccessibilityLog", "滑动操作被取消")
                    }
                }, null)
            }

            CommandType.CLICK.toString() -> {
                // 1. 创建一个路径 (Path) 对象
                val path = Path().apply {
                    // 移动到指定的点击坐标
                    moveTo(command.startX.toFloat(), command.startY.toFloat())
                }

                // 2. 创建一个手势描述 (GestureDescription)
                // 参数：路径 (Path), 开始时间 (startTime), 持续时间 (duration)
                // 点击是一个瞬时操作，所以持续时间可以很短，例如 1ms 或 10ms。
                val gestureDescription = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, 1L))
                    .build()

                // 3. 派遣（执行）手势
                // dispatchGesture 会返回一个布尔值，表示系统是否成功接收了这个手势请求。
                val dispatched =
                    dispatchGesture(gestureDescription, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            FloatingLogService.log(
                                AutomationApplication.context,
                                "点击手势成功完成"
                            )
                            Log.i("AccessibilityLog", "点击手势成功完成。")
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            super.onCancelled(gestureDescription)
                            Log.w("AccessibilityLog", "点击手势被取消。")
                        }
                    }, null) // 最后一个参数是 Handler，传 null 表示在主线程处理回调
            }

            CommandType.INOUT.toString() -> {
                // 1. 获取当前活动窗口的根节点
                val rootNode = rootInActiveWindow ?: return
                // 2. 查找当前获得焦点的节点 (通常就是用户正在输入的那个输入框)
                val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focusedNode == null) {
                    Log.w("AccessibilityLog", "输入文本失败：未找到任何获得焦点的输入框。")
                    return
                }
                // 3. 检查节点是否真的是一个输入框 (可选但推荐)
                if (!focusedNode.isEditable || !focusedNode.isFocused) {
                    Log.w("AccessibilityLog", "输入文本失败：找到的焦点节点不是一个可编辑的输入框。")
                    return
                }
                Log.d(
                    "AccessibilityLog",
                    "准备在节点 ${focusedNode.className} 中输入文本: '${command.text}'"
                )
                // 4. 创建一个包含要设置的文本的 Bundle
                val arguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        command.text
                    )
                }
                // 5. 执行 ACTION_SET_TEXT 操作
                // 这个操作会直接将输入框的文本替换为我们提供的新文本。
                FloatingLogService.log(AutomationApplication.context, "输入成功完成")
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        }
    }
}