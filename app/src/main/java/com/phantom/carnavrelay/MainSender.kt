package com.phantom.carnavrelay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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

class MainSender(private val context: Context) {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val TIMEOUT_SECONDS = 3L
        private const val MAX_RETRIES = 2
        
        // Connection states
        const val STATE_UNPAIRED = "UNPAIRED"
        const val STATE_PAIRING = "PAIRING"
        const val STATE_CONNECTING = "CONNECTING"
        const val STATE_CONNECTED = "CONNECTED"
        const val STATE_AUTH_FAILED = "AUTH_FAILED"
        const val STATE_OFFLINE = "OFFLINE"
        
        // Callback interface for send results
        interface SendCallback {
            fun onSuccess()
            fun onFailure(error: String, queued: Boolean, authFailed: Boolean = false)
        }
        
        // Static method for accessibility service
        fun sendOpenUrlAsync(context: Context, mapsUrl: String, source: String = "a11y") {
            val sender = MainSender(context)
            Log.d(TAG, "[$source] Sending URL: $mapsUrl")
            sender.sendOpenUrl(mapsUrl, null) // No callback needed for async capture
        }
    }

    private val prefsManager = PrefsManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var currentState = STATE_UNPAIRED

    fun isPaired(): Boolean {
        return prefsManager.isPaired()
    }
    
    fun isVerified(): Boolean {
        return prefsManager.isPairedVerified()
    }
    
    fun getCurrentState(): String {
        return currentState
    }
    
    fun setState(state: String) {
        Log.d(TAG, "🔄 State change: $currentState → $state")
        currentState = state
    }

    fun getPairedInfo(): String {
        if (!isPaired()) return "Not paired"
        val ip = prefsManager.getPairedIp() ?: "unknown"
        val port = prefsManager.getPairedPort()
        val tokenHint = prefsManager.getPairedToken()?.let { prefsManager.getTokenHint(it) } ?: "****"
        val verified = if (prefsManager.isPairedVerified()) "✅" else "⏳"
        return "$verified $ip:$port (Token: $tokenHint)"
    }

    fun sendOpenUrl(url: String, callback: SendCallback? = null) {
        Log.d(TAG, "📤 sendOpenUrl() called - state: $currentState")
        
        // Guard: Check if paired
        if (!isPaired()) {
            Log.w(TAG, "❌ Cannot send - not paired with any display device")
            setState(STATE_UNPAIRED)
            showToast("Please pair with a Display device first")
            callback?.onFailure("Not paired with any display device", false)
            return
        }

        val ip = prefsManager.getPairedIp() ?: ""
        val port = prefsManager.getPairedPort()
        val token = prefsManager.getPairedToken() ?: ""
        
        // Guard: Validate config
        if (ip.isEmpty() || token.isEmpty()) {
            Log.e(TAG, "❌ Invalid pairing config - IP or token is empty")
            setState(STATE_AUTH_FAILED)
            showToast("Invalid pairing configuration")
            callback?.onFailure("Invalid pairing configuration", false, true)
            return
        }

        val endpoint = "http://$ip:$port/open-url"
        
        val jsonBody = try {
            JSONObject().apply {
                put("token", token)
                put("url", url)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to create JSON body", e)
            showToast("Internal error: ${e.message}")
            callback?.onFailure("Internal error: ${e.message}", false)
            return
        }

        Log.d(TAG, "📤 Sending URL to $endpoint")
        Log.d(TAG, "📦 Payload: ${jsonBody.replace(token, "****TOKEN****")}")
        
        setState(STATE_CONNECTING)

        val request = try {
            Request.Builder()
                .url(endpoint)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to build request", e)
            setState(STATE_OFFLINE)
            showToast("Failed to create request: ${e.message}")
            callback?.onFailure("Failed to create request: ${e.message}", false)
            return
        }

        sendWithRetry(request, url, callback, 0)
    }

    private fun sendWithRetry(request: Request, url: String, callback: SendCallback?, retryCount: Int) {
        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "💥 Send failed (attempt ${retryCount + 1}): ${e.message}", e)
                    
                    val errorMsg = when (e) {
                        is UnknownHostException -> "Cannot reach device. Check network."
                        is ConnectException -> "Connection refused. Is Display running?"
                        is SocketTimeoutException -> "Connection timed out."
                        else -> "Network error: ${e.message}"
                    }
                    
                    if (retryCount < MAX_RETRIES) {
                        Log.d(TAG, "🔄 Retrying... (${retryCount + 1}/$MAX_RETRIES)")
                        mainHandler.postDelayed({
                            sendWithRetry(request, url, callback, retryCount + 1)
                        }, 500)
                    } else {
                        // All retries failed - queue for later
                        Log.w(TAG, "❌ All retries failed, queuing URL: $url")
                        setState(STATE_OFFLINE)
                        prefsManager.addToPendingQueue(url)
                        showToast("Failed to send. Queued for retry.")
                        callback?.onFailure(errorMsg, true)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = try {
                        response.body?.string() ?: ""
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 Failed to read response body", e)
                        ""
                    }
                    
                    Log.d(TAG, "📨 Response: ${response.code} - $body")
                    
                    when (response.code) {
                        200 -> {
                            Log.d(TAG, "✅ URL sent successfully")
                            setState(STATE_CONNECTED)
                            prefsManager.setPairedVerified(true)
                            showToast("Sent to display device!")
                            callback?.onSuccess()
                        }
                        401 -> {
                            // Unauthorized - token mismatch
                            Log.e(TAG, "💥 Authentication failed (401) - token mismatch")
                            setState(STATE_AUTH_FAILED)
                            prefsManager.setPairedVerified(false)
                            showToast("Authentication failed. Please pair again.")
                            callback?.onFailure("Authentication failed. Token mismatch.", false, true)
                        }
                        409 -> {
                            // Conflict - not paired
                            Log.e(TAG, "💥 Not paired (409) - device requires pairing")
                            setState(STATE_AUTH_FAILED)
                            prefsManager.setPairedVerified(false)
                            showToast("Device not paired. Please scan QR again.")
                            callback?.onFailure("Device not paired. Please scan QR again.", false, true)
                        }
                        else -> {
                            val errorMsg = "Server error: ${response.code}"
                            Log.e(TAG, "💥 $errorMsg")
                            
                            // Try to parse error reason
                            val reason = try {
                                JSONObject(body).optString("reason", "Unknown")
                            } catch (e: Exception) {
                                "Unknown"
                            }
                            
                            // Queue for retry unless it's an auth error
                            if (response.code == 403) {
                                setState(STATE_AUTH_FAILED)
                                showToast("Access forbidden: $reason")
                                callback?.onFailure("Access forbidden: $reason", false)
                            } else {
                                prefsManager.addToPendingQueue(url)
                                showToast("Failed ($reason). Queued for retry.")
                                callback?.onFailure("$errorMsg - $reason", true)
                            }
                        }
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception in sendWithRetry", e)
            setState(STATE_OFFLINE)
            showToast("Error: ${e.message}")
            callback?.onFailure("Error: ${e.message}", false)
        }
    }

    fun retryPending(callback: SendCallback? = null) {
        val pending = prefsManager.getPendingQueue()
        
        if (pending.isEmpty()) {
            showToast("No pending commands")
            callback?.onSuccess()
            return
        }

        Log.d(TAG, "🔄 Retrying ${pending.size} pending commands")
        showToast("Retrying ${pending.size} pending commands...")

        var successCount = 0
        var failCount = 0
        val total = pending.size

        pending.forEach { url ->
            sendOpenUrl(url, object : SendCallback {
                override fun onSuccess() {
                    prefsManager.removeFromPendingQueue(url)
                    successCount++
                    checkComplete()
                }

                override fun onFailure(error: String, queued: Boolean, authFailed: Boolean) {
                    if (!queued || authFailed) {
                        prefsManager.removeFromPendingQueue(url)
                    }
                    failCount++
                    checkComplete()
                }

                private fun checkComplete() {
                    if (successCount + failCount == total) {
                        val msg = "Retry complete: $successCount success, $failCount failed"
                        Log.d(TAG, msg)
                        showToast(msg)
                        if (failCount == 0) {
                            callback?.onSuccess()
                        } else {
                            callback?.onFailure("$failCount commands still failed", pending.size > 0)
                        }
                    }
                }
            })
        }
    }

    fun getPendingCount(): Int {
        return prefsManager.getPendingCount()
    }

    fun clearPending() {
        prefsManager.clearPendingQueue()
        showToast("Pending queue cleared")
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Test with Bangkok location
    fun sendTestLocation(callback: SendCallback? = null) {
        val testUrl = "https://www.google.com/maps/search/?api=1&query=13.7563,100.5018"
        Log.d(TAG, "🧪 Sending test location (Bangkok): $testUrl")
        sendOpenUrl(testUrl, callback)
    }
    
    // Check server status via /status endpoint
    fun checkServerStatus(callback: ((Boolean, String?) -> Unit)? = null) {
        if (!isPaired()) {
            callback?.invoke(false, "Not paired")
            return
        }
        
        val ip = prefsManager.getPairedIp() ?: ""
        val port = prefsManager.getPairedPort()
        val token = prefsManager.getPairedToken() ?: ""
        
        if (ip.isEmpty()) {
            callback?.invoke(false, "No IP configured")
            return
        }
        
        val endpoint = "http://$ip:$port/status"
        Log.d(TAG, "🔍 Checking server status at $endpoint")
        
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .build()
        
        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "💥 Status check failed: ${e.message}")
                    setState(STATE_OFFLINE)
                    mainHandler.post {
                        callback?.invoke(false, e.message)
                    }
                }
                
                override fun onResponse(call: Call, response: Response) {
                    val body = try {
                        response.body?.string() ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    
                    Log.d(TAG, "📨 Status response: ${response.code}")
                    
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(body)
                            val serverPaired = json.optBoolean("paired", false)
                            val serverTokenHint = json.optString("tokenHint", "")
                            val localTokenHint = prefsManager.getTokenHint(token)
                            
                            // Check if token hint matches
                            val tokenMatches = serverTokenHint == localTokenHint
                            
                            if (serverPaired && tokenMatches) {
                                Log.d(TAG, "✅ Server verified - paired and token matches")
                                setState(STATE_CONNECTED)
                                prefsManager.setPairedVerified(true)
                                mainHandler.post {
                                    callback?.invoke(true, null)
                                }
                            } else if (!tokenMatches) {
                                Log.w(TAG, "⚠️ Token mismatch detected")
                                setState(STATE_AUTH_FAILED)
                                prefsManager.setPairedVerified(false)
                                mainHandler.post {
                                    callback?.invoke(false, "Token mismatch - please re-pair")
                                }
                            } else {
                                Log.d(TAG, "ℹ️ Server not paired yet")
                                setState(STATE_PAIRING)
                                mainHandler.post {
                                    callback?.invoke(false, "Display not paired yet")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "💥 Failed to parse status response", e)
                            mainHandler.post {
                                callback?.invoke(false, "Invalid response")
                            }
                        }
                    } else {
                        Log.w(TAG, "❌ Status check returned ${response.code}")
                        setState(STATE_OFFLINE)
                        mainHandler.post {
                            callback?.invoke(false, "HTTP ${response.code}")
                        }
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception in checkServerStatus", e)
            setState(STATE_OFFLINE)
            callback?.invoke(false, e.message)
        }
    }
}
