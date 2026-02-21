package com.loglock

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.loglock.data.LockEventDatabase
import com.loglock.databinding.ActivityMainBinding
import com.loglock.ui.LogAdapter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeEvents()
    }

    override fun onResume() {
        super.onResume()
        updateSetupBanner()
    }

    private fun setupRecyclerView() {
        adapter = LogAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun observeEvents() {
        val dao = LockEventDatabase.getInstance(this).lockEventDao()
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        dao.getRecentEvents(since).observe(this) { events ->
            adapter.submitList(events)
            binding.tvEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateSetupBanner() {
        if (isAccessibilityServiceEnabled()) {
            binding.bannerAccessibility.visibility = View.GONE
        } else {
            binding.bannerAccessibility.visibility = View.VISIBLE
            binding.bannerAccessibility.setOnClickListener { showAccessibilityDialog() }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(
                "LogLock needs the Accessibility permission to detect PIN/password " +
                "attempts on the lock screen.\n\n" +
                "1. Tap Open Settings\n" +
                "2. Find LogLock in the list\n" +
                "3. Toggle it ON\n\n" +
                "On EMUI (Huawei), also go to:\n" +
                "Settings > Battery > App launch\n" +
                "and set LogLock to Manage manually, enabling all three options."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
