package com.loglock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loglock.data.DeviceEvent
import com.loglock.data.DeviceEventType
import com.loglock.data.LockEventDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootShutdownReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> DeviceEventType.BOOT
            Intent.ACTION_SHUTDOWN       -> DeviceEventType.SHUTDOWN
            else -> return
        }

        // goAsync() lets us do a short DB insert without ANR risk.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = LockEventDatabase.getInstance(context)
                db.deviceEventDao().insert(
                    DeviceEvent(eventType = type, timestamp = System.currentTimeMillis())
                )
            } finally {
                pending.finish()
            }
        }
    }
}
