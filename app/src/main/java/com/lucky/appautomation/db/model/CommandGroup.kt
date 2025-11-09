package com.lucky.appautomation.db.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.Serializable


@Entity(tableName = "command_groups")
data class CommandGroup(
    @PrimaryKey val name: String, // 名字作为主键（唯一标识）
    val isLoop: Boolean, // 是否循环指令
    val loopDelay: String, // 循环延迟时间（单位：毫秒，根据需求调整）
    var isRandom: Boolean = false, /// 是否随机
    var spendTime: String, /// 每个执行的花费时间
    var filterPackageName: String /// 过滤的包名

) : Serializable {
    @Ignore
    var commands: List<Command>? = null
}