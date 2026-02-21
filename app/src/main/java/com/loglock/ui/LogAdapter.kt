package com.loglock.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.loglock.R
import com.loglock.data.LockEvent
import com.loglock.databinding.ItemLogEntryBinding
import java.text.SimpleDateFormat
import java.util.*

class LogAdapter : ListAdapter<LockEvent, LogAdapter.ViewHolder>(DiffCallback()) {

    private val dateFmt = SimpleDateFormat("EEE MMM d  HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(private val b: ItemLogEntryBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(event: LockEvent) {
            b.tvLockTime.text = "Locked:   ${dateFmt.format(Date(event.lockTime))}"

            when {
                event.isOngoing -> {
                    b.tvStatus.text = "Currently locked"
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
                        b.tvStatus.text = "Unlocked: —"
                        b.tvDuration.text = ""
                    }

                    b.tvAttempts.text = when {
                        event.attempts == 0 && event.wasSuccessful -> "Unlocked directly (no PIN entry detected)"
                        event.attempts == 1 && event.wasSuccessful -> "1 attempt — unlocked"
                        event.attempts > 1 && event.wasSuccessful ->
                            "${event.attempts - 1} failed + 1 successful attempt"
                        event.attempts == 0 -> "No attempts recorded"
                        else -> "${event.attempts} attempt(s) — not unlocked"
                    }

                    val cardColor = when {
                        !event.wasSuccessful && event.attempts > 0 ->
                            ContextCompat.getColor(b.root.context, R.color.card_failed)
                        event.attempts > 2 ->
                            ContextCompat.getColor(b.root.context, R.color.card_warning)
                        else ->
                            ContextCompat.getColor(b.root.context, R.color.card_normal)
                    }
                    b.root.setCardBackgroundColor(cardColor)
                }
            }
        }

        private fun formatDuration(secs: Long): String = when {
            secs < 60 -> "${secs}s"
            secs < 3600 -> "${secs / 60}m ${secs % 60}s"
            else -> "${secs / 3600}h ${(secs % 3600) / 60}m"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<LockEvent>() {
        override fun areItemsTheSame(a: LockEvent, b: LockEvent) = a.id == b.id
        override fun areContentsTheSame(a: LockEvent, b: LockEvent) = a == b
    }
}
