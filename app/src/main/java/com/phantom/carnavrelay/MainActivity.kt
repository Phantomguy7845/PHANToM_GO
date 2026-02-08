package com.phantom.carnavrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val STATUS_CHECK_INTERVAL_MS = 5000L  // Check status every 5 seconds
    }

    private lateinit var prefsManager: PrefsManager
    private lateinit var mainSender: MainSender
    
    private lateinit var tvPairStatus: TextView
    private lateinit var tvPairInfo: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvConnectionState: TextView
    private lateinit var btnScanQR: Button
    private lateinit var btnSendTest: Button
    private lateinit var btnRetryPending: Button
    private lateinit var btnClearPairing: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var btnA11ySettings: Button
    private lateinit var tvA11yToggle: TextView
    private lateinit var tvA11yStatus: TextView
    private lateinit var cardDisplay: MaterialCardView

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "✅ QR scan successful")
            updateUI()
        } else {
            Log.d(TAG, "❌ QR scan cancelled or failed")
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val statusCheckRunnable = object : Runnable {
        override fun run() {
            if (mainSender.isPaired()) {
                checkServerStatus()
            }
            handler.postDelayed(this, STATUS_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ MainActivity.onCreate()")
        setContentView(R.layout.activity_main)

        prefsManager = PrefsManager(this)
        mainSender = MainSender(this)

        initViews()
        updateUI()
        updateA11yUI()
        
        // Check for crash reports
        CrashReporter.checkAndShowCrashReport(this)
    }

    private fun initViews() {
        tvPairStatus = findViewById(R.id.tvPairStatus)
        tvPairInfo = findViewById(R.id.tvPairInfo)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvConnectionState = findViewById(R.id.tvConnectionState)
        btnScanQR = findViewById(R.id.btnScanQR)
        btnSendTest = findViewById(R.id.btnSendTest)
        btnRetryPending = findViewById(R.id.btnRetryPending)
        btnClearPairing = findViewById(R.id.btnClearPairing)
        btnCheckStatus = findViewById(R.id.btnCheckStatus)
        btnA11ySettings = findViewById(R.id.btnA11ySettings)
        tvA11yToggle = findViewById(R.id.tvA11yToggle)
        tvA11yStatus = findViewById(R.id.tvA11yStatus)
        cardDisplay = findViewById(R.id.cardDisplay)

        btnScanQR.setOnClickListener {
            startQRScan()
        }

        btnSendTest.setOnClickListener {
            sendTestLocation()
        }

        btnRetryPending.setOnClickListener {
            retryPending()
        }

        btnClearPairing.setOnClickListener {
            confirmClearPairing()
        }

        btnCheckStatus.setOnClickListener {
            checkServerStatus()
        }

        btnA11ySettings.setOnClickListener {
            showA11ySettings()
        }

        btnSendTest.setOnClickListener {
            sendTestLocation()
        }

        btnRetryPending.setOnClickListener {
            retryPending()
        }

        btnClearPairing.setOnClickListener {
            confirmClearPairing()
        }
        
        btnCheckStatus.setOnClickListener {
            checkServerStatus()
            Toast.makeText(this, "Checking connection...", Toast.LENGTH_SHORT).show()
        }

        cardDisplay.setOnClickListener {
            startActivity(Intent(this, DisplayActivity::class.java))
        }
    }

    private fun updateUI() {
        val isPaired = mainSender.isPaired()
        val isVerified = mainSender.isVerified()
        val pendingCount = mainSender.getPendingCount()
        val currentState = mainSender.getCurrentState()

        // Update connection state display
        val stateEmoji = when (currentState) {
            MainSender.STATE_UNPAIRED -> "❌"
            MainSender.STATE_PAIRING -> "⏳"
            MainSender.STATE_CONNECTING -> "🔄"
            MainSender.STATE_CONNECTED -> "✅"
            MainSender.STATE_AUTH_FAILED -> "⚠️"
            MainSender.STATE_OFFLINE -> "🔴"
            else -> "❓"
        }
        
        val stateText = when (currentState) {
            MainSender.STATE_UNPAIRED -> "Not Paired"
            MainSender.STATE_PAIRING -> "Waiting for Display"
            MainSender.STATE_CONNECTING -> "Connecting..."
            MainSender.STATE_CONNECTED -> "Connected"
            MainSender.STATE_AUTH_FAILED -> "Auth Failed"
            MainSender.STATE_OFFLINE -> "Offline"
            else -> "Unknown"
        }
        
        tvConnectionState.text = "$stateEmoji $stateText"
        tvConnectionState.setTextColor(getStateColor(currentState))

        if (isPaired) {
            val verifiedEmoji = if (isVerified) "✅" else "⏳"
            tvPairStatus.text = "$verifiedEmoji Paired"
            tvPairInfo.text = mainSender.getPairedInfo()
            btnSendTest.isEnabled = isVerified
            btnClearPairing.isEnabled = true
        } else {
            tvPairStatus.text = "❌ Not Paired"
            tvPairInfo.text = "Scan QR code from Display device"
            btnSendTest.isEnabled = false
            btnClearPairing.isEnabled = false
        }

        tvPendingCount.text = "Pending: $pendingCount"
        btnRetryPending.isEnabled = pendingCount > 0
        
        // Show auth failed warning
        if (currentState == MainSender.STATE_AUTH_FAILED) {
            showAuthFailedWarning()
        }
    }
    
    private fun getStateColor(state: String): Int {
        return when (state) {
            MainSender.STATE_CONNECTED -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            MainSender.STATE_AUTH_FAILED -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
            MainSender.STATE_OFFLINE -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            MainSender.STATE_CONNECTING -> ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            else -> ContextCompat.getColor(this, android.R.color.darker_gray)
        }
    }
    
    private fun showAuthFailedWarning() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Authentication Failed")
            .setMessage("The token no longer matches the Display device. This can happen if:\n\n1. The Display device refreshed its token\n2. You're trying to connect to a different device\n\nPlease scan the QR code again to re-pair.")
            .setPositiveButton("Scan QR") { _, _ ->
                startQRScan()
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }
    
    private fun checkServerStatus() {
        Log.d(TAG, "🔍 Checking server status...")
        mainSender.checkServerStatus { success, error ->
            runOnUiThread {
                if (success) {
                    Log.d(TAG, "✅ Server status check passed")
                } else {
                    Log.w(TAG, "❌ Server status check failed: $error")
                }
                updateUI()
            }
        }
    }
    
    private fun showA11ySettings() {
        val isEnabled = PrefsManager.isA11yCaptureEnabled(this)
        val isSystemEnabled = PrefsManager.isA11yCaptureEnabled(this)
        
        val message = if (!isSystemEnabled) {
            "Accessibility service is not enabled in system settings.\n\nPlease enable it first, then you can toggle capture on/off from here."
        } else {
            "Auto-capture is currently ${if (isEnabled) "ON" else "OFF"}.\n\nYou can toggle this setting without going to system settings."
        }
        
        AlertDialog.Builder(this)
            .setTitle("Auto Capture Settings")
            .setMessage(message)
            .setPositiveButton(if (isSystemEnabled) "Toggle" else "Open Settings") { _, _ ->
                if (isSystemEnabled) {
                    // Toggle app-level setting
                    val newState = !isEnabled
                    prefsManager.setA11yCaptureEnabled(newState)
                    updateA11yUI()
                    Log.d(TAG, "A11y capture toggled: $newState")
                } else {
                    // Open system accessibility settings
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open accessibility settings", e)
                        Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateA11yUI() {
        val isEnabled = PrefsManager.isA11yCaptureEnabled(this)
        val isSystemEnabled = PrefsManager.isA11yCaptureEnabled(this)
        
        tvA11yToggle.text = if (isEnabled) "ON" else "OFF"
        tvA11yToggle.setTextColor(if (isEnabled) getColor(R.color.purple_500) else getColor(R.color.text_primary))
        
        tvA11yStatus.text = if (isSystemEnabled) {
            if (isEnabled) "Auto-capturing navigation links" else "Tap to enable auto-capture"
        } else {
            "Enable in system settings first"
        }
    }

    private fun startQRScan() {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            }
            return
        }

        val intent = Intent(this, PairScanActivity::class.java)
        scanLauncher.launch(intent)
    }

    private fun sendTestLocation() {
        Log.d(TAG, "🧪 Sending test location (Bangkok)")
        btnSendTest.isEnabled = false
        
        mainSender.sendTestLocation(object : MainSender.Companion.SendCallback {
            override fun onSuccess() {
                Log.d(TAG, "✅ Test location sent successfully")
                runOnUiThread {
                    btnSendTest.isEnabled = mainSender.isVerified()
                    updateUI()
                }
            }

            override fun onFailure(error: String, queued: Boolean, authFailed: Boolean) {
                Log.e(TAG, "❌ Failed to send test location: $error")
                runOnUiThread {
                    btnSendTest.isEnabled = mainSender.isVerified() && !authFailed
                    updateUI()
                    
                    if (authFailed) {
                        showAuthFailedWarning()
                    }
                }
            }
        })
    }

    private fun retryPending() {
        Log.d(TAG, "🔄 Retrying pending commands")
        mainSender.retryPending(object : MainSender.Companion.SendCallback {
            override fun onSuccess() {
                runOnUiThread {
                    updateUI()
                }
            }

            override fun onFailure(error: String, queued: Boolean, authFailed: Boolean) {
                runOnUiThread {
                    updateUI()
                    
                    if (authFailed) {
                        showAuthFailedWarning()
                    }
                }
            }
        })
    }

    private fun confirmClearPairing() {
        AlertDialog.Builder(this)
            .setTitle("Clear Pairing?")
            .setMessage("This will remove the pairing with the Display device. You'll need to scan QR again to reconnect.")
            .setPositiveButton("Clear") { _, _ ->
                Log.d(TAG, "🗑️ Clearing pairing")
                prefsManager.clearPairing()
                mainSender.setState(MainSender.STATE_UNPAIRED)
                mainSender.clearPending()
                updateUI()
                Toast.makeText(this, "Pairing cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "✅ Camera permission granted")
                    startQRScan()
                } else {
                    Log.w(TAG, "❌ Camera permission denied")
                    Toast.makeText(this, "Camera permission required to scan QR codes", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ MainActivity.onResume()")
        updateUI()
        
        // Start periodic status checks
        handler.post(statusCheckRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ MainActivity.onPause()")
        
        // Stop periodic status checks
        handler.removeCallbacks(statusCheckRunnable)
    }
}
