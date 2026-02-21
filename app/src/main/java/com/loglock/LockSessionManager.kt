package com.loglock

import kotlinx.coroutines.Job

object LockSessionManager {
    @Volatile var currentEventId: Long = -1L
    @Volatile var attempts: Int = 0
    var sessionSetupJob: Job? = null

    fun recordAttempt() {
        attempts++
    }
}
