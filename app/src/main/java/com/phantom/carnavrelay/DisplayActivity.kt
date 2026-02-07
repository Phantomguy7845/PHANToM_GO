package com.phantom.carnavrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
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
    private lateinit var btnRefreshToken: Button
    private lateinit var btnCopyToken: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ DisplayActivity.onCreate()")
        setContentView(R.layout.activity_display)

        prefsManager = PrefsManager(this)

        initViews()
        checkPermissions()
        updateDisplay()
        startHttpServer()
    }

    private fun initViews() {
        tvIpPort = findViewById(R.id.tvIpPort)
        tvTokenHint = findViewById(R.id.tvTokenHint)
        ivQrCode = findViewById(R.id.ivQrCode)
        tvStatus = findViewById(R.id.tvStatus)
        btnRefreshToken = findViewById(R.id.btnRefreshToken)
        btnCopyToken = findViewById(R.id.btnCopyToken)

        btnRefreshToken.setOnClickListener {
            refreshToken()
        }

        btnCopyToken.setOnClickListener {
            copyTokenToClipboard()
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

    private fun updateDisplay() {
        val tempServer = HttpServer(this, prefsManager)
        val ip = tempServer.getLocalIpAddress() ?: "0.0.0.0"
        val port = prefsManager.getServerPort()
        val token = prefsManager.getServerToken()
        val tokenHint = prefsManager.getTokenHint(token)

        tvIpPort.text = "$ip:$port"
        tvTokenHint.text = "Token: $tokenHint"

        val pairingPayload = JsonUtils.createPairingPayload(ip, port, token)
        generateQrCode(pairingPayload)

        Log.d(TAG, "📋 Display updated - IP: $ip, Port: $port, Token: $tokenHint")
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
            .setMessage("This will generate a new token. The old pairing will no longer work. Make sure to re-scan the new QR code on the Main device.")
            .setPositiveButton("Refresh") { _, _ ->
                val newToken = prefsManager.refreshServerToken()
                val tokenHint = prefsManager.getTokenHint(newToken)
                Log.d(TAG, "🔄 Token refreshed: $tokenHint")
                updateDisplay()
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
        updateDisplay()
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
