package com.phantom.carnavrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    }

    private lateinit var prefsManager: PrefsManager
    private lateinit var mainSender: MainSender
    
    private lateinit var tvPairStatus: TextView
    private lateinit var tvPairInfo: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var btnScanQR: Button
    private lateinit var btnSendTest: Button
    private lateinit var btnRetryPending: Button
    private lateinit var btnClearPairing: Button
    private lateinit var cardDisplay: MaterialCardView
    private lateinit var cardMain: MaterialCardView

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "✅ QR scan successful")
            updateUI()
        } else {
            Log.d(TAG, "❌ QR scan cancelled or failed")
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
    }

    private fun initViews() {
        tvPairStatus = findViewById(R.id.tvPairStatus)
        tvPairInfo = findViewById(R.id.tvPairInfo)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        btnScanQR = findViewById(R.id.btnScanQR)
        btnSendTest = findViewById(R.id.btnSendTest)
        btnRetryPending = findViewById(R.id.btnRetryPending)
        btnClearPairing = findViewById(R.id.btnClearPairing)
        cardDisplay = findViewById(R.id.cardDisplay)
        cardMain = findViewById(R.id.cardMain)

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

        cardDisplay.setOnClickListener {
            startActivity(Intent(this, DisplayActivity::class.java))
        }

        cardMain.setOnClickListener {
            if (!mainSender.isPaired()) {
                Toast.makeText(this, "Please pair with a Display device first", Toast.LENGTH_SHORT).show()
                startQRScan()
            } else {
                Toast.makeText(this, "Already paired! Use Send Test or share navigation to Display.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val isPaired = mainSender.isPaired()
        val pendingCount = mainSender.getPendingCount()

        if (isPaired) {
            tvPairStatus.text = "✅ Paired"
            tvPairInfo.text = mainSender.getPairedInfo()
            btnSendTest.isEnabled = true
            btnClearPairing.isEnabled = true
        } else {
            tvPairStatus.text = "❌ Not Paired"
            tvPairInfo.text = "Scan QR code from Display device"
            btnSendTest.isEnabled = false
            btnClearPairing.isEnabled = false
        }

        tvPendingCount.text = "Pending: $pendingCount"
        btnRetryPending.isEnabled = pendingCount > 0
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
        mainSender.sendTestLocation(object : MainSender.SendCallback {
            override fun onSuccess() {
                updateUI()
            }

            override fun onFailure(error: String, queued: Boolean) {
                updateUI()
            }
        })
    }

    private fun retryPending() {
        Log.d(TAG, "� Retrying pending commands")
        mainSender.retryPending(object : MainSender.SendCallback {
            override fun onSuccess() {
                updateUI()
            }

            override fun onFailure(error: String, queued: Boolean) {
                updateUI()
            }
        })
    }

    private fun confirmClearPairing() {
        AlertDialog.Builder(this)
            .setTitle("Clear Pairing?")
            .setMessage("This will remove the pairing with the Display device. You'll need to scan QR again to reconnect.")
            .setPositiveButton("Clear") { _, _ ->
                prefsManager.clearPairing()
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
    }
}
