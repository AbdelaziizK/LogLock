package com.loglock.ui

import com.loglock.data.DeviceEvent
import com.loglock.data.LockEvent

sealed class LogItem {
    data class Lock(val event: LockEvent) : LogItem()
    data class Device(val event: DeviceEvent) : LogItem()

    val timestamp: Long get() = when (this) {
        is Lock   -> event.lockTime
        is Device -> event.timestamp
    }
}
