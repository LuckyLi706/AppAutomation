package com.lucky.appautomation.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lucky.appautomation.db.model.Command
import com.lucky.appautomation.db.model.CommandGroup

@Database(
    entities = [CommandGroup::class, Command::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // 提供DAO实例
    abstract fun commandGroupDao(): CommandGroupDao
    abstract fun commandDao(): CommandDao

    // 单例模式，避免重复创建数据库实例
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "command_database" // 数据库名称
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}