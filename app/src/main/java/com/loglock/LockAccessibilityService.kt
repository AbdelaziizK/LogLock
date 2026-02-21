package com.loglock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.loglock.data.LockEvent
import com.loglock.data.LockEventDatabase
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class LockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: LockEventDatabase
    private var lastPinTextLength = 0
    // Timestamp of the last digit-button click; used as a timing signal when the
    // PIN view cannot be found in the accessibility tree (e.g. EMUI custom views).
    private var lastDigitClickTime = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenLocked()
                Intent.ACTION_USER_PRESENT -> onScreenUnlocked()
            }
        }
    }

    override fun onServiceConnected() {
        db = LockEventDatabase.getInstance(this)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = (AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    or AccessibilityEvent.TYPE_VIEW_CLICKED
                    or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            packageNames = null
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        serviceScope.launch {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            db.lockEventDao().deleteOlderThan(cutoff)
        }
    }

    // -------------------------------------------------------------------------
    // Screen lock / unlock
    // -------------------------------------------------------------------------

    private fun onScreenLocked() {
        // Snapshot the previous session's state before resetting
        val prevJob = LockSessionManager.sessionSetupJob
        val prevAttempts = LockSessionManager.attempts

        // Reset immediately so accessibility events start fresh for the new session
        LockSessionManager.attempts = 0
        LockSessionManager.sessionSetupJob = null
        lastPinTextLength = 0
        lastDigitClickTime = 0L

        LockSessionManager.sessionSetupJob = serviceScope.launch {
            // Close the previous session (screen went off before unlocking)
            prevJob?.join()
            val prevId = LockSessionManager.currentEventId
            if (prevId != -1L) {
                db.lockEventDao().getById(prevId)?.let { prev ->
                    db.lockEventDao().update(
                        prev.copy(attempts = prevAttempts, isOngoing = false)
                    )
                }
            }

            // Create the new lock event and record its ID
            val newId = db.lockEventDao().insert(
                LockEvent(lockTime = System.currentTimeMillis())
            )
            LockSessionManager.currentEventId = newId
        }
    }

    private fun onScreenUnlocked() {
        // If the PIN field had content when the unlock fired, the user's final
        // successful attempt was never counted (ACTION_USER_PRESENT arrives before
        // the field clears). Count it now.
        if (lastPinTextLength > 0) {
            LockSessionManager.recordAttempt()
        }

        // Capture state before any reset
        val capturedJob = LockSessionManager.sessionSetupJob
        val capturedAttempts = LockSessionManager.attempts

        // Clear attempt counter and job reference; leave currentEventId for the coroutine to read
        LockSessionManager.attempts = 0
        LockSessionManager.sessionSetupJob = null
        lastPinTextLength = 0
        lastDigitClickTime = 0L

        serviceScope.launch {
            // Ensure the insert job has finished so currentEventId is valid
            capturedJob?.join()

            val id = LockSessionManager.currentEventId
            LockSessionManager.currentEventId = -1L  // Mark session as closed

            if (id == -1L) return@launch

            db.lockEventDao().getById(id)?.let { event ->
                db.lockEventDao().update(
                    event.copy(
                        unlockTime = System.currentTimeMillis(),
                        attempts = capturedAttempts,
                        wasSuccessful = true,
                        isOngoing = false
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // PIN / password attempt detection
    // -------------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val km = getSystemService(KeyguardManager::class.java) ?: return
        if (!km.isKeyguardLocked) return

        when (event.eventType) {

            // ── Path A: numpad button clicked ────────────────────────────────
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val desc = event.contentDescription?.toString()?.trim() ?: ""
                val text = event.text.firstOrNull()?.toString()?.trim() ?: ""
                val label = desc.ifEmpty { text }
                val first = label.firstOrNull()
                when {
                    first != null && first.isDigit() -> {
                        lastPinTextLength++
                        lastDigitClickTime = System.currentTimeMillis()
                    }
                    desc.contains("delete", ignoreCase = true)
                    || desc.contains("backspace", ignoreCase = true)
                    || desc.contains("erase", ignoreCase = true) -> {
                        if (lastPinTextLength > 0) lastPinTextLength--
                    }
                    else -> {
                        // Non-digit, non-delete click while digits entered = submit button.
                        if (lastPinTextLength > 0) {
                            LockSessionManager.recordAttempt()
                            lastPinTextLength = 0
                            lastDigitClickTime = 0L
                        }
                    }
                }
            }

            // ── Path B: text-change (works if the PIN view fires this event) ─
            // Only reacts to a clear-to-zero; does NOT update lastPinTextLength
            // for non-zero lengths so it doesn't interfere with Path A tracking.
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val currentLength = event.text.sumOf { it?.length ?: 0 }
                if (currentLength == 0 && lastPinTextLength > 0) {
                    LockSessionManager.recordAttempt()
                    lastPinTextLength = 0
                    lastDigitClickTime = 0L
                }
            }

            // ── Path C: window-content change ────────────────────────────────
            // Primary: find the PIN view (isPassword && !isEditable) and read
            //   its length — works on standard AOSP and some OEMs.
            // Fallback: if the PIN view cannot be found, use a timing heuristic —
            //   a content-change event that arrives 200–4 000 ms after the last
            //   digit click is almost certainly the wrong-PIN response on
            //   auto-submit keypads (e.g. EMUI with no OK button).
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pinLength = pinLengthFromWindows()
                if (pinLength != null) {
                    if (pinLength == 0 && lastPinTextLength > 0) {
                        LockSessionManager.recordAttempt()
                        lastPinTextLength = 0
                        lastDigitClickTime = 0L
                    } else {
                        lastPinTextLength = pinLength
                    }
                } else if (lastPinTextLength > 0 && lastDigitClickTime > 0L) {
                    val elapsed = System.currentTimeMillis() - lastDigitClickTime
                    if (elapsed in 200L..4000L) {
                        // PIN view not in tree but content changed shortly after digits
                        // were entered — treat as wrong-PIN clear.
                        LockSessionManager.recordAttempt()
                        lastPinTextLength = 0
                        lastDigitClickTime = 0L
                    }
                }
            }

            else -> return
        }
    }

    /**
     * Searches all accessible windows for a password-type, non-editable view
     * (the PIN dot display). Returns the number of entered digits, or null if
     * the view cannot be found in any window.
     *
     * Uses [AccessibilityNodeInfo.isPassword] instead of class-name matching so
     * it works regardless of OEM-specific class names (e.g. Huawei/EMUI).
     * The [AccessibilityNodeInfo.isEditable] check excludes real text-input fields.
     */
    private fun pinLengthFromWindows(): Int? {
        // Try the active window first (cheapest), then all windows.
        val roots = sequence {
            rootInActiveWindow?.let { yield(it) }
            windows?.forEach { w -> w.root?.let { yield(it) } }
        }
        for (root in roots) {
            val result = pinLengthInSubtree(root)
            root.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun pinLengthInSubtree(node: AccessibilityNodeInfo): Int? {
        // The PIN dot display is a password-type view that is NOT user-editable.
        // This matches PasswordTextView and its OEM equivalents without needing
        // the exact class name.
        if (node.isPassword && !node.isEditable) {
            return node.text?.length ?: 0
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = pinLengthInSubtree(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {}
        serviceScope.cancel()
    }
}
