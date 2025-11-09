package com.lucky.appautomation.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lucky.appautomation.db.model.Command
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    // 插入单条指令
    @Insert
    suspend fun insert(command: Command)

    // 批量插入指令
    @Insert
    suspend fun insertAll(commands: List<Command>)

    // 根据指令组名字删除所有关联指令
    @Query("DELETE FROM commands WHERE groupName = :groupName")
    suspend fun deleteByGroupName(groupName: String)

    // 核心功能：通过指令组名字查询所有关联的指令
    @Query("SELECT * FROM commands WHERE groupName = :groupName ORDER BY id")
    suspend fun getCommandsByGroupName(groupName: String): List<Command>

    // 查询所有指令（可选）
    @Query("SELECT * FROM commands")
    suspend fun getAllCommands(): List<Command>
}