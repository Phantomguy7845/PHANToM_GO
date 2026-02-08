package com.phantom.carnavrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class PairScanActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val TIMEOUT_SECONDS = 5L
    }

    private lateinit var prefsManager: PrefsManager
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressDialog: AlertDialog? = null

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
        
        if (pairingData == null) {
            Log.e(TAG, "❌ Invalid QR code format")
            Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        
        val (ip, port, token) = pairingData
        Log.d(TAG, "✅ Parsed pairing data - IP: $ip, Port: $port, Token: ${prefsManager.getTokenHint(token)}")
        
        // Show progress dialog
        showProgressDialog("Verifying pairing...")
        
        // Call /pair endpoint to verify
        callPairEndpoint(ip, port, token)
    }
    
    private fun callPairEndpoint(ip: String, port: Int, token: String) {
        val endpoint = "http://$ip:$port/pair"
        val mainId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        val mainName = android.os.Build.MODEL
        
        val jsonBody = JSONObject().apply {
            put("token", token)
            put("mainId", mainId)
            put("mainName", mainName)
        }.toString()
        
        Log.d(TAG, "📤 Calling /pair at $endpoint")
        Log.d(TAG, "📦 Payload: $jsonBody")
        
        val request = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "💥 /pair request failed: ${e.message}", e)
                hideProgressDialog()
                
                val errorMsg = when (e) {
                    is UnknownHostException -> "Cannot reach device. Check IP address."
                    is ConnectException -> "Connection refused. Is Display mode running?"
                    is SocketTimeoutException -> "Connection timed out. Check network."
                    else -> "Network error: ${e.message}"
                }
                
                mainHandler.post {
                    showErrorDialog(errorMsg)
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Log.d(TAG, "📨 /pair response: ${response.code} - $body")
                hideProgressDialog()
                
                mainHandler.post {
                    when (response.code) {
                        200 -> {
                            // Success - save pairing as verified
                            Log.d(TAG, "✅ /pair success - saving verified pairing")
                            prefsManager.savePairing(ip, port, token, verified = true)
                            Toast.makeText(this@PairScanActivity, "Paired with $ip:$port!", Toast.LENGTH_LONG).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        401 -> {
                            // Unauthorized - token mismatch
                            Log.w(TAG, "❌ /pair UNAUTHORIZED - token mismatch")
                            showErrorDialog("Authentication failed. Token mismatch.\n\nPlease refresh QR code on Display device and scan again.")
                        }
                        else -> {
                            // Other error
                            val json = try {
                                JSONObject(body)
                            } catch (e: Exception) {
                                null
                            }
                            val reason = json?.optString("reason", "Unknown error")
                            Log.e(TAG, "❌ /pair error: $reason")
                            showErrorDialog("Pairing failed: $reason\n\nPlease try again.")
                        }
                    }
                }
                response.close()
            }
        })
    }
    
    private fun showProgressDialog(message: String) {
        mainHandler.post {
            progressDialog = AlertDialog.Builder(this)
                .setTitle("Pairing")
                .setMessage(message)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        }
    }
    
    private fun hideProgressDialog() {
        mainHandler.post {
            progressDialog?.dismiss()
            progressDialog = null
        }
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Pairing Failed")
            .setMessage(message)
            .setPositiveButton("Scan Again") { _, _ ->
                startScan()
            }
            .setNegativeButton("Cancel") { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .setCancelable(false)
            .show()
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
