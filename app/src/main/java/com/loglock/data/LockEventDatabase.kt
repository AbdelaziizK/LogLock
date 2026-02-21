package com.loglock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class DeviceEventTypeConverters {
    @TypeConverter fun fromType(type: DeviceEventType): String = type.name
    @TypeConverter fun toType(name: String): DeviceEventType = DeviceEventType.valueOf(name)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS device_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventType TEXT NOT NULL,
                timestamp INTEGER NOT NULL
               )"""
        )
    }
}

@Database(
    entities = [LockEvent::class, DeviceEvent::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DeviceEventTypeConverters::class)
abstract class LockEventDatabase : RoomDatabase() {

    abstract fun lockEventDao(): LockEventDao
    abstract fun deviceEventDao(): DeviceEventDao

    companion object {
        @Volatile private var INSTANCE: LockEventDatabase? = null

        fun getInstance(context: Context): LockEventDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LockEventDatabase::class.java,
                    "loglock.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
