package com.loglock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DeviceEventType { BOOT, SHUTDOWN }

@Entity(tableName = "device_events")
data class DeviceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: DeviceEventType,
    val timestamp: Long
)
