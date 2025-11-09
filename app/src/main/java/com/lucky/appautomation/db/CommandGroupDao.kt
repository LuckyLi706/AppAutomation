package com.lucky.appautomation.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucky.appautomation.db.model.CommandGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandGroupDao {
    // 插入指令组（如果已存在则替换）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: CommandGroup)

    // 删除指令组
    @Query("DELETE FROM command_groups WHERE name = :name")
    suspend fun delete(name: String)

    // 查询所有指令组（返回Flow可观察数据变化）
    @Query("SELECT * FROM command_groups ORDER BY name")
    fun getAllGroups(): Flow<List<CommandGroup>>

    // 根据名字查询单个指令组
    @Query("SELECT * FROM command_groups WHERE name = :name LIMIT 1")
    suspend fun getGroupByName(name: String): CommandGroup?
}