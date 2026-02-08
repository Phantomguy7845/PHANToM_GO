package com.phantom.carnavrelay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
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
        tvTokenHint.text = "Token: $tokenHint"

        if (isPaired) {
            // Paired state - show connected UI
            val mainName = prefsManager.getDisplayPairedMainName() ?: "Unknown Device"
            val pairedAt = prefsManager.getDisplayPairedAt()
            val timeStr = if (pairedAt > 0) {
                val minutes = (System.currentTimeMillis() - pairedAt) / 60000
                if (minutes < 60) "$minutes min ago" else "${minutes / 60} hr ago"
            } else ""
            
            tvPairedInfo.text = "Connected to: $mainName\n$timeStr"
            tvStatus.text = "🟢 CONNECTED"
            
            layoutQR.visibility = View.GONE
            layoutConnected.visibility = View.VISIBLE
            
            Log.d(TAG, "📋 Display updated - PAIRED with $mainName")
        } else {
            // Not paired - show QR code
            tvStatus.text = "🟡 Waiting for pairing..."
            tvPairedInfo.text = "Scan this QR code with Main device"
            
            layoutQR.visibility = View.VISIBLE
            layoutConnected.visibility = View.GONE
            
            val pairingPayload = JsonUtils.createPairingPayload(ip, port, token)
            generateQrCode(pairingPayload)
            
            Log.d(TAG, "📋 Display updated - Waiting for pairing, tokenHint=$tokenHint")
        }
    }
    
    private fun showQRCode() {
        // Show QR code temporarily
        val tempServer = HttpServer(this, prefsManager)
        val ip = tempServer.getLocalIpAddress() ?: "0.0.0.0"
        val port = prefsManager.getServerPort()
        val token = prefsManager.getServerToken()
        
        layoutQR.visibility = View.VISIBLE
        layoutConnected.visibility = View.GONE
        
        val pairingPayload = JsonUtils.createPairingPayload(ip, port, token)
        generateQrCode(pairingPayload)
        
        // Auto-hide after 30 seconds
        ivQrCode.postDelayed({
            if (!isFinishing && !isDestroyed) {
                updateUI()
            }
        }, 30000)
        
        Toast.makeText(this, "QR code shown for 30 seconds", Toast.LENGTH_SHORT).show()
    }
    
    private fun confirmResetPairing() {
        AlertDialog.Builder(this)
            .setTitle("Reset Pairing?")
            .setMessage("This will disconnect the paired Main device and generate a new token. You'll need to re-scan the QR code.")
            .setPositiveButton("Reset") { _, _ ->
                Log.d(TAG, "🔄 Resetting pairing - clearing paired state and refreshing token")
                prefsManager.setDisplayPaired(false)
                prefsManager.refreshServerToken()
                updateUI()
                Toast.makeText(this, "Pairing reset. New QR code generated.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateQrCode(content: String) {
        try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"

            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 400, 400, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }

            ivQrCode.setImageBitmap(bitmap)
            Log.d(TAG, "✅ QR Code generated")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to generate QR code", e)
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshToken() {
        AlertDialog.Builder(this)
            .setTitle("Refresh Token?")
            .setMessage("This will generate a new token and reset any existing pairing. The old pairing will no longer work. Make sure to re-scan the new QR code on the Main device.")
            .setPositiveButton("Refresh") { _, _ ->
                prefsManager.setDisplayPaired(false)
                val newToken = prefsManager.refreshServerToken()
                val tokenHint = prefsManager.getTokenHint(newToken)
                Log.d(TAG, "🔄 Token refreshed: $tokenHint")
                updateUI()
                Toast.makeText(this, "Token refreshed! Re-scan QR code on Main device.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyTokenToClipboard() {
        val token = prefsManager.getServerToken()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("PHANToM GO Token", token)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ DisplayActivity.onResume()")
        updateUI()
        
        // Register for pairing state changes
        val filter = IntentFilter(HttpServer.ACTION_PAIRING_CHANGED)
        registerReceiver(pairingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ DisplayActivity.onPause()")
        
        // Unregister receiver
        try {
            unregisterReceiver(pairingReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to unregister receiver", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "💀 DisplayActivity.onDestroy()")
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "✅ All permissions granted")
                startHttpServer()
            } else {
                Log.w(TAG, "⚠️ Some permissions denied")
                Toast.makeText(this, "Some permissions denied. Server may not work properly.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
