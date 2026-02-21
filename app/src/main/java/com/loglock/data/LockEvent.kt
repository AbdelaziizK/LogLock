package com.loglock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lock_events")
data class LockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lockTime: Long,
    val unlockTime: Long = 0L,
    val attempts: Int = 0,       // Total PIN/password attempts (failed + final successful)
    val wasSuccessful: Boolean = false,
    val isOngoing: Boolean = true
)
