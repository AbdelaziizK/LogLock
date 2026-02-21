package com.loglock.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.loglock.R
import com.loglock.data.DeviceEventType
import com.loglock.data.LockEvent
import com.loglock.databinding.ItemDeviceEventBinding
import com.loglock.databinding.ItemLogEntryBinding
import java.text.SimpleDateFormat
import java.util.*

class LogAdapter : ListAdapter<LogItem, RecyclerView.ViewHolder>(DiffCallback()) {

    private val dateFmt = SimpleDateFormat("EEE MMM d  HH:mm:ss", Locale.getDefault())

    companion object {
        private const val TYPE_LOCK   = 0
        private const val TYPE_DEVICE = 1
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is LogItem.Lock   -> TYPE_LOCK
        is LogItem.Device -> TYPE_DEVICE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            TYPE_LOCK -> LockViewHolder(
                ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> DeviceViewHolder(
                ItemDeviceEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
        when (val item = getItem(position)) {
            is LogItem.Lock   -> (holder as LockViewHolder).bind(item.event)
            is LogItem.Device -> (holder as DeviceViewHolder).bind(item)
        }

    // ── Lock event card ───────────────────────────────────────────────────────

    inner class LockViewHolder(private val b: ItemLogEntryBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(event: LockEvent) {
            b.tvLockTime.text = "Locked:   ${dateFmt.format(Date(event.lockTime))}"

            when {
                event.isOngoing -> {
                    b.tvStatus.text   = "Currently locked"
                    b.tvAttempts.text = ""
                    b.tvDuration.text = ""
                    b.root.setCardBackgroundColor(
                        ContextCompat.getColor(b.root.context, R.color.card_ongoing)
                    )
                }
                else -> {
                    if (event.unlockTime > 0) {
                        b.tvStatus.text = "Unlocked: ${dateFmt.format(Date(event.unlockTime))}"
                        val secs = (event.unlockTime - event.lockTime) / 1000
                        b.tvDuration.text = "Duration: ${formatDuration(secs)}"
                    } else {
                        b.tvStatus.text   = "Unlocked: —"
                        b.tvDuration.text = ""
                    }

                    b.tvAttempts.text = when {
                        event.attempts == 0 && event.wasSuccessful  -> "Unlocked directly (no PIN entry detected)"
                        event.attempts == 1 && event.wasSuccessful  -> "1 attempt — unlocked"
                        event.attempts > 1  && event.wasSuccessful  ->
                            "${event.attempts - 1} failed + 1 successful attempt"
                        event.attempts == 0 -> "No attempts recorded"
                        else                -> "${event.attempts} attempt(s) — not unlocked"
                    }

                    b.root.setCardBackgroundColor(
                        ContextCompat.getColor(
                            b.root.context,
                            when {
                                !event.wasSuccessful && event.attempts > 0 -> R.color.card_failed
                                event.attempts > 2                         -> R.color.card_warning
                                else                                       -> R.color.card_normal
                            }
                        )
                    )
                }
            }
        }

        private fun formatDuration(secs: Long) = when {
            secs < 60   -> "${secs}s"
            secs < 3600 -> "${secs / 60}m ${secs % 60}s"
            else        -> "${secs / 3600}h ${(secs % 3600) / 60}m"
        }
    }

    // ── Device event card ─────────────────────────────────────────────────────

    inner class DeviceViewHolder(private val b: ItemDeviceEventBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: LogItem.Device) {
            val event = item.event
            when (event.eventType) {
                DeviceEventType.BOOT -> {
                    b.tvDeviceIcon.text      = "▶"
                    b.tvDeviceEventType.text = "Device started"
                    b.root.setCardBackgroundColor(
                        ContextCompat.getColor(b.root.context, R.color.card_boot)
                    )
                }
                DeviceEventType.SHUTDOWN -> {
                    b.tvDeviceIcon.text      = "■"
                    b.tvDeviceEventType.text = "Device shut down"
                    b.root.setCardBackgroundColor(
                        ContextCompat.getColor(b.root.context, R.color.card_shutdown)
                    )
                }
            }
            b.tvDeviceEventTime.text = dateFmt.format(Date(event.timestamp))
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    class DiffCallback : DiffUtil.ItemCallback<LogItem>() {
        override fun areItemsTheSame(a: LogItem, b: LogItem) = when {
            a is LogItem.Lock   && b is LogItem.Lock   -> a.event.id == b.event.id
            a is LogItem.Device && b is LogItem.Device -> a.event.id == b.event.id
            else -> false
        }
        override fun areContentsTheSame(a: LogItem, b: LogItem) = a == b
    }
}
