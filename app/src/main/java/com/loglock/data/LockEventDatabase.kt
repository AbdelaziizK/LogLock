package com.loglock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LockEvent::class], version = 1, exportSchema = false)
abstract class LockEventDatabase : RoomDatabase() {

    abstract fun lockEventDao(): LockEventDao

    companion object {
        @Volatile private var INSTANCE: LockEventDatabase? = null

        fun getInstance(context: Context): LockEventDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LockEventDatabase::class.java,
                    "loglock.db"
                ).build().also { INSTANCE = it }
            }
    }
}
