package com.phantom.carnavrelay

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val PREF_OVERLAY_ENABLED = "overlay_enabled"
        private const val MAX_LOG_ENTRIES = 500
    }

    private lateinit var prefsManager: PrefsManager
    private lateinit var overlayController: OverlayController
    private lateinit var overlaySwitch: Switch
    private lateinit var overlayStatusText: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var logText: TextView
    private lateinit var copyLogButton: Button
    private lateinit var clearLogButton: Button
    private lateinit var grantPermissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "⚙️ SettingsActivity onCreate")
        setContentView(R.layout.activity_settings)
        
        prefsManager = PrefsManager(this)
        overlayController = OverlayController(this)
        initViews()
        setupClickListeners()
        updateOverlayStatus()
        loadLogs()
    }

    private fun initViews() {
        overlaySwitch = findViewById(R.id.overlaySwitch)
        overlayStatusText = findViewById(R.id.overlayStatusText)
        logScrollView = findViewById(R.id.logScrollView)
        logText = findViewById(R.id.logText)
        copyLogButton = findViewById(R.id.copyLogButton)
        clearLogButton = findViewById(R.id.clearLogButton)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
    }

    private fun setupClickListeners() {
        overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "🔄 Overlay toggle changed: $isChecked")
            overlayController.setOverlayEnabled(isChecked)
            updateOverlayStatus()
            
            // Haptic feedback
            overlaySwitch.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        copyLogButton.setOnClickListener {
            copyLogToClipboard()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        clearLogButton.setOnClickListener {
            clearLogs()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        grantPermissionButton.setOnClickListener {
            requestOverlayPermission()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun updateOverlayStatus() {
        val hasPermission = overlayController.hasOverlayPermission()
        val isEnabled = overlayController.isOverlayEnabled()

        overlaySwitch.isChecked = isEnabled && hasPermission
        overlaySwitch.isEnabled = hasPermission

        when {
            !hasPermission -> {
                overlayStatusText.text = "Permission Required"
                overlayStatusText.setTextColor(ContextCompat.getColor(this, R.color.aurora_warning))
                grantPermissionButton.visibility = View.VISIBLE
            }
            isEnabled -> {
                overlayStatusText.text = "Overlay Enabled"
                overlayStatusText.setTextColor(ContextCompat.getColor(this, R.color.aurora_success))
                grantPermissionButton.visibility = View.GONE
            }
            else -> {
                overlayStatusText.text = "Overlay Disabled"
                overlayStatusText.setTextColor(ContextCompat.getColor(this, R.color.aurora_disconnected))
                grantPermissionButton.visibility = View.GONE
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        Toast.makeText(this, "Grant overlay permission and return", Toast.LENGTH_LONG).show()
    }

    private fun loadLogs() {
        val logs = PhantomLog.getLogs(MAX_LOG_ENTRIES)
        logText.text = logs.joinToString("\n")
        logScrollView.post {
            logScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun copyLogToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("PHANTOM_GO_LOGS", logText.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun clearLogs() {
        AlertDialog.Builder(this)
            .setTitle("Clear Logs")
            .setMessage("Are you sure you want to clear all logs?")
            .setPositiveButton("Clear") { _, _ ->
                PhantomLog.clearLogs()
                logText.text = ""
                Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateOverlayStatus()
        loadLogs() // Refresh logs
    }
}
