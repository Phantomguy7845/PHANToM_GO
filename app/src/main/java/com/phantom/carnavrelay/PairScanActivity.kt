package com.phantom.carnavrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class PairScanActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    private lateinit var prefsManager: PrefsManager

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            Log.d(TAG, "📱 QR Code scanned: ${result.contents}")
            handleScannedQR(result.contents)
        } else {
            Log.d(TAG, "❌ QR Scan cancelled")
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ PairScanActivity.onCreate()")
        
        prefsManager = PrefsManager(this)

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        } else {
            startScan()
        }
    }

    private fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan QR code from Display device")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }

    private fun handleScannedQR(content: String) {
        Log.d(TAG, "📦 Processing QR content: $content")

        // Try to parse as JSON
        val pairingData = JsonUtils.parsePairingPayload(content)
        
        if (pairingData != null) {
            val (ip, port, token) = pairingData
            Log.d(TAG, "✅ Parsed pairing data - IP: $ip, Port: $port, Token: ${prefsManager.getTokenHint(token)}")
            
            // Save pairing
            prefsManager.savePairing(ip, port, token)
            
            Toast.makeText(this, "Paired with $ip:$port!", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        } else {
            Log.e(TAG, "❌ Invalid QR code format")
            Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "✅ Camera permission granted")
                    startScan()
                } else {
                    Log.w(TAG, "❌ Camera permission denied")
                    Toast.makeText(this, "Camera permission required to scan QR codes", Toast.LENGTH_LONG).show()
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        }
    }
}
