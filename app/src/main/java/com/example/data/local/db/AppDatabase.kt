package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_coder.db"
                ).build().also { instance = it }
            }
        }
    }
}
