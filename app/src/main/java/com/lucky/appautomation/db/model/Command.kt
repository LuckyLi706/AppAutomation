package com.lucky.appautomation.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(
    tableName = "commands",
    // 可选：添加外键约束，确保关联的指令组存在（删除组时可级联删除指令）
    foreignKeys = [ForeignKey(
        entity = CommandGroup::class,
        parentColumns = ["name"],
        childColumns = ["groupName"],
        onDelete = ForeignKey.CASCADE // 当指令组被删除时，关联的指令也会被删除
    )]
)

data class Command(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 自增主键
    var groupName: String, // 关联的指令组名字（与CommandGroup的name对应）
    var commandName: String, // 指令名称
    var startX: String = "", // 开始的x坐标
    var startY: String = "", // 开始的x坐标
    var endX: String = "", // 开始的x坐标
    var endY: String = "", // 开始的x坐标
    var text: String = "", // 文本输入
) : Serializable