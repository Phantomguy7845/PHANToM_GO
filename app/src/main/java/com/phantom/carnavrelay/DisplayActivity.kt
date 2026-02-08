package com.phantom.carnavrelay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import java.util.EnumMap

class DisplayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var prefsManager: PrefsManager
    private lateinit var tvIpPort: TextView
    private lateinit var tvTokenHint: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvPairedInfo: TextView
    private lateinit var btnRefreshToken: Button
    private lateinit var btnCopyToken: Button
    private lateinit var btnShowQR: Button
    private lateinit var btnResetPairing: Button
    private lateinit var layoutQR: LinearLayout
    private lateinit var layoutConnected: LinearLayout
    private lateinit var switchDisplayMode: MaterialSwitch
    private lateinit var tvDisplayModeStatus: TextView
    private lateinit var tvServerInfo: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnBatterySettings: Button

    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HttpServer.ACTION_PAIRING_CHANGED) {
                val paired = intent.getBooleanExtra("paired", false)
                Log.d(TAG, "📢 Received PAIRING_CHANGED broadcast: paired=$paired")
                runOnUiThread {
                    updateUI()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ DisplayActivity.onCreate()")
        setContentView(R.layout.activity_display)

        prefsManager = PrefsManager(this)

        initViews()
        checkPermissions()
        updateUI()
        startHttpServer()
    }

    private fun initViews() {
        tvIpPort = findViewById(R.id.tvIpPort)
        tvTokenHint = findViewById(R.id.tvTokenHint)
        ivQrCode = findViewById(R.id.ivQrCode)
        tvStatus = findViewById(R.id.tvStatus)
        tvPairedInfo = findViewById(R.id.tvPairedInfo)
        btnRefreshToken = findViewById(R.id.btnRefreshToken)
        btnCopyToken = findViewById(R.id.btnCopyToken)
        btnShowQR = findViewById(R.id.btnShowQR)
        btnResetPairing = findViewById(R.id.btnResetPairing)
        layoutQR = findViewById(R.id.layoutQR)
        layoutConnected = findViewById(R.id.layoutConnected)
        switchDisplayMode = findViewById(R.id.switchDisplayMode)
        tvDisplayModeStatus = findViewById(R.id.tvDisplayModeStatus)
        tvServerInfo = findViewById(R.id.tvServerInfo)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        btnBatterySettings = findViewById(R.id.btnBatterySettings)

        btnRefreshToken.setOnClickListener {
            refreshToken()
        }

        btnCopyToken.setOnClickListener {
            copyTokenToClipboard()
        }
        
        btnShowQR.setOnClickListener {
            showQRCode()
        }
        
        btnResetPairing.setOnClickListener {
            confirmResetPairing()
        }

        switchDisplayMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startDisplayService()
            } else {
                stopDisplayService()
            }
        }

        btnBatterySettings.setOnClickListener {
            requestBatteryOptimization()
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startHttpServer() {
        Log.d(TAG, "🚀 Starting DisplayServerService")
        try {
            val intent = Intent(this, DisplayServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tvStatus.text = "🟢 Server Running"
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to start server", e)
            tvStatus.text = "🔴 Server Error"
            Toast.makeText(this, "Failed to start server: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUI() {
        val tempServer = HttpServer(this, prefsManager)
        val ip = tempServer.getLocalIpAddress() ?: "0.0.0.0"
        val port = prefsManager.getServerPort()
        val token = prefsManager.getServerToken()
        val tokenHint = prefsManager.getTokenHint(token)
        val isPaired = prefsManager.isDisplayPaired()

        tvIpPort.text = "$ip:$port"
        tvTokenHint.text = tokenHint
        
        // Update Display Mode UI
        val isServiceRunning = isServiceRunning(DisplayServerService::class.java)
        switchDisplayMode.isChecked = isServiceRunning
        tvDisplayModeStatus.text = if (isServiceRunning) "ON" else "OFF"
        tvDisplayModeStatus.setTextColor(if (isServiceRunning) getColor(R.color.success) else getColor(R.color.gray_600))
        
        if (isServiceRunning) {
            tvServerInfo.text = "Server: $ip:$port"
            tvStatus.text = "🟢 Server Running"
        } else {
            tvServerInfo.text = "Server not running"
            tvStatus.text = "🔴 Server Stopped"
        }

        // Update battery optimization status
        updateBatteryStatus()

        if (isPaired) {
            val pairedName = prefsManager.getDisplayPairedMainName() ?: "Unknown"
            val pairedAt = prefsManager.getDisplayPairedAt()
            val timeAgo = if (pairedAt > 0) {
                val minutes = (System.currentTimeMillis() - pairedAt) / 60000
                if (minutes < 60) "${minutes}m ago" else "${minutes/60}h ago"
            } else "Unknown"
            
            tvPairedInfo.text = "Paired with $pairedName ($timeAgo)"
            layoutQR.visibility = View.GONE
            layoutConnected.visibility = View.VISIBLE
        } else {
            tvPairedInfo.text = "Waiting for pairing..."
            layoutQR.visibility = View.VISIBLE
            layoutConnected.visibility = View.GONE
        }

        // Generate QR code
        generateQRCode("$ip:$port:$token")
    }
    
    private fun startDisplayService() {
        Log.d(TAG, "🚀 Starting DisplayServerService")
        try {
            val intent = Intent(this, DisplayServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to start server", e)
            switchDisplayMode.isChecked = false
            Toast.makeText(this, "Failed to start server: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopDisplayService() {
        Log.d(TAG, "🛑 Stopping DisplayServerService")
        try {
            val intent = Intent(this, DisplayServerService::class.java)
            stopService(intent)
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to stop server", e)
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            for (service in services) {
                if (serviceClass.name == service.service.className) {
                    return service.foreground
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Could not check service status", e)
            false
        }
    }
    
    private fun updateBatteryStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true // Below Android 6, battery optimizations are less aggressive
        }
        
        if (isIgnoringBatteryOptimizations) {
            tvBatteryStatus.text = "Battery optimization: OFF"
            tvBatteryStatus.setTextColor(getColor(R.color.success))
            btnBatterySettings.visibility = View.GONE
        } else {
            tvBatteryStatus.text = "Battery optimization: ON"
            tvBatteryStatus.setTextColor(getColor(R.color.warning))
            btnBatterySettings.visibility = View.VISIBLE
        }
    }
    
    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("To ensure Display Mode works reliably in the background, please disable battery optimization for PHANToM GO.")
                .setPositiveButton("Settings") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivityForResult(intent, 1002)
                    } catch (e: Exception) {
                        // Fallback to general settings
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002) {
            updateUI()
        }
    }
    
    private fun generateQRCode(data: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            ivQrCode.setImageBitmap(bitmap)
            Log.d(TAG, "✅ QR code generated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to generate QR code", e)
        }
    }
    
    private fun refreshToken() {
        Log.d(TAG, "🔄 Refreshing token...")
        prefsManager.refreshServerToken()
        updateUI()
        Toast.makeText(this, "Token refreshed", Toast.LENGTH_SHORT).show()
    }
    
    private fun copyTokenToClipboard() {
        val token = prefsManager.getServerToken()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("PHANToM GO Token", token)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "📋 Token copied to clipboard")
    }
    
    private fun showQRCode() {
        if (layoutQR.visibility == View.VISIBLE) {
            layoutQR.visibility = View.GONE
        } else {
            layoutQR.visibility = View.VISIBLE
            generateQRCode("${tvIpPort.text}:${prefsManager.getServerToken()}")
        }
    }
    
    private fun confirmResetPairing() {
        AlertDialog.Builder(this)
            .setTitle("Reset Pairing?")
            .setMessage("This will remove the current pairing and generate a new token. The Main device will need to scan QR again.")
            .setPositiveButton("Reset") { _, _ ->
                Log.d(TAG, "�️ Resetting pairing")
                prefsManager.clearDisplayPairing()
                updateUI()
                Toast.makeText(this, "Pairing reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
