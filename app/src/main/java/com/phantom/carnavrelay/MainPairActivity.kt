package com.phantom.carnavrelay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.phantom.carnavrelay.bt.MainClientService

class MainPairActivity : AppCompatActivity() {

    private val TAG = "PHANTOM_GO"
    
    private lateinit var codeInput: TextInputEditText
    private lateinit var codeLayout: TextInputLayout
    private lateinit var connectButton: MaterialButton
    private var deviceAddress: String = ""
    private var deviceName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "▶️ MainPairActivity.onCreate() called")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_pair)

        deviceAddress = intent.getStringExtra("address") ?: ""
        deviceName = intent.getStringExtra("device_name") ?: "Unknown Device"
        
        Log.d(TAG, "📱 MainPairActivity - Address: $deviceAddress, Name: $deviceName")

        if (deviceAddress.isEmpty()) {
            Log.e(TAG, "❌ MainPairActivity: Empty device address")
            Toast.makeText(this, "ไม่พบอุปกรณ์", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            initViews()
            setupCodeInput()
            setupConnectButton()
            Log.d(TAG, "✅ MainPairActivity views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "💥 MainPairActivity view initialization failed", e)
            CrashReporter.recordException(this, "MainPairActivity:onCreate", e)
            Toast.makeText(this, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        Log.d(TAG, "🔧 MainPairActivity.initViews() called")
        codeInput = findViewById(R.id.codeInput)
        codeLayout = findViewById(R.id.codeLayout)
        connectButton = findViewById(R.id.connectButton)

        // Set device name in subtitle
        findViewById<android.widget.TextView>(R.id.deviceNameText)?.text = deviceName
    }

    private fun setupCodeInput() {
        Log.d(TAG, "🔧 MainPairActivity.setupCodeInput() called")
        codeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val code = s?.toString()?.replace(" ", "") ?: ""
                // Format with spaces every 2 digits
                if (code.length <= 6) {
                    val formatted = formatCode(code)
                    if (formatted != s?.toString()) {
                        codeInput.setText(formatted)
                        codeInput.setSelection(formatted.length)
                    }
                }

                // Enable button when code is complete
                connectButton.isEnabled = code.length == 6
                codeLayout.error = if (code.length == 6) null else "กรุณากรอกรหัส 6 หลัก"
            }
        })
    }

    private fun formatCode(code: String): String {
        return code.chunked(2).joinToString(" ")
    }

    private fun setupConnectButton() {
        Log.d(TAG, "🔧 MainPairActivity.setupConnectButton() called")
        connectButton.setOnClickListener {
            val code = codeInput.text?.toString()?.replace(" ", "") ?: ""
            Log.d(TAG, "👆 Connect button clicked with code length: ${code.length}")
            if (code.length == 6) {
                startMainClientService(code)
            } else {
                codeLayout.error = "กรุณากรอกรหัส 6 หลัก"
            }
        }
    }

    private fun startMainClientService(code: String) {
        Log.d(TAG, "🚀 MainPairActivity.startMainClientService() - Starting service for $deviceAddress")
        val intent = Intent(this, MainClientService::class.java).apply {
            putExtra("address", deviceAddress)
            putExtra("code", code)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "▶️ Starting foreground service (Android O+)")
                startForegroundService(intent)
            } else {
                Log.d(TAG, "▶️ Starting service (pre-Android O)")
                startService(intent)
            }
            Log.d(TAG, "✅ MainClientService started successfully")
            Toast.makeText(this, "กำลังเชื่อมต่อ...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to start MainClientService", e)
            CrashReporter.recordException(this, "MainPairActivity:startMainClientService", e)
            Toast.makeText(this, "ไม่สามารถเริ่มบริการได้: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        finish()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "💀 MainPairActivity.onDestroy() called")
        super.onDestroy()
    }
}
