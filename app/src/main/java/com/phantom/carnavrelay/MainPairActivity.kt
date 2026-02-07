package com.phantom.carnavrelay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.phantom.carnavrelay.bt.MainClientService

class MainPairActivity : AppCompatActivity() {

    private lateinit var codeInput: TextInputEditText
    private lateinit var codeLayout: TextInputLayout
    private lateinit var connectButton: MaterialButton
    private var deviceAddress: String = ""
    private var deviceName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_pair)

        deviceAddress = intent.getStringExtra("address") ?: ""
        deviceName = intent.getStringExtra("device_name") ?: "Unknown Device"

        if (deviceAddress.isEmpty()) {
            Toast.makeText(this, "ไม่พบอุปกรณ์", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupCodeInput()
        setupConnectButton()
    }

    private fun initViews() {
        codeInput = findViewById(R.id.codeInput)
        codeLayout = findViewById(R.id.codeLayout)
        connectButton = findViewById(R.id.connectButton)

        // Set device name in subtitle
        findViewById<android.widget.TextView>(R.id.deviceNameText)?.text = deviceName
    }

    private fun setupCodeInput() {
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
        connectButton.setOnClickListener {
            val code = codeInput.text?.toString()?.replace(" ", "") ?: ""
            if (code.length == 6) {
                startMainClientService(code)
            } else {
                codeLayout.error = "กรุณากรอกรหัส 6 หลัก"
            }
        }
    }

    private fun startMainClientService(code: String) {
        val intent = Intent(this, MainClientService::class.java).apply {
            putExtra("address", deviceAddress)
            putExtra("code", code)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "กำลังเชื่อมต่อ...", Toast.LENGTH_SHORT).show()
        finish()
    }
}
