package com.phantom.carnavrelay

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MainSender(
    private val context: Context,
    private val toastEnabled: Boolean = true,
    private val queueOnFailure: Boolean = true
) {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val TIMEOUT_SECONDS = 3L
        private const val MAX_RETRIES = 2
        private const val DISCOVERY_COOLDOWN_MS = 30000L
        private const val DISCOVERY_TIMEOUT_MS = 300L
        private const val DISCOVERY_MAX_HOSTS = 254
        
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
    @Volatile private var discoveryInProgress = false
    @Volatile private var lastDiscoveryAt = 0L

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
                        Log.w(TAG, "❌ All retries failed${if (queueOnFailure) ", queuing URL" else ""}: $url")
                        setState(STATE_OFFLINE)
                        if (queueOnFailure) {
                            prefsManager.addToPendingQueue(url)
                            showToast("Failed to send. Queued for retry.")
                        }
                        maybeDiscoverDisplay("send-failure")
                        callback?.onFailure(errorMsg, queueOnFailure)
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
                                if (queueOnFailure) {
                                    prefsManager.addToPendingQueue(url)
                                    showToast("Failed ($reason). Queued for retry.")
                                }
                                callback?.onFailure("$errorMsg - $reason", queueOnFailure)
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
        if (!toastEnabled) return
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
                    maybeDiscoverDisplay("status-failure")
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

    private fun maybeDiscoverDisplay(reason: String) {
        if (!isPaired()) return
        val now = System.currentTimeMillis()
        if (discoveryInProgress || now - lastDiscoveryAt < DISCOVERY_COOLDOWN_MS) {
            return
        }
        discoveryInProgress = true
        lastDiscoveryAt = now

        Thread {
            try {
                discoverDisplayOnLocalNetwork(reason)
            } catch (e: Exception) {
                Log.e(TAG, "💥 Auto-reconnect discovery failed", e)
            } finally {
                discoveryInProgress = false
            }
        }.start()
    }

    private fun discoverDisplayOnLocalNetwork(reason: String) {
        val token = prefsManager.getPairedToken() ?: return
        val tokenHint = prefsManager.getTokenHint(token)
        val port = prefsManager.getPairedPort()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
        val maskInt = wifiManager?.dhcpInfo?.netmask ?: 0
        if (ipInt == 0 || maskInt == 0) {
            Log.w(TAG, "⚠️ Auto-reconnect skipped: no Wi-Fi IP")
            return
        }

        val ip = ipInt.toUInt()
        val mask = maskInt.toUInt()
        val network = ip and mask
        val broadcast = network or mask.inv()
        var start = network + 1u
        var end = if (broadcast > 0u) broadcast - 1u else 0u
        var hostCount = if (end >= start) (end - start + 1u) else 0u

        if (hostCount == 0u) {
            Log.w(TAG, "⚠️ Auto-reconnect skipped: invalid subnet range")
            return
        }

        if (hostCount > DISCOVERY_MAX_HOSTS.toUInt()) {
            val subnetBase = ip and 0xFFFFFF00u
            start = subnetBase + 1u
            end = subnetBase + 254u
            hostCount = 254u
        }

        val localIpString = uintToIp(ip)
        val localTokenHint = tokenHint
        val localMainId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

        val discoveryClient = OkHttpClient.Builder()
            .connectTimeout(DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        val found = AtomicBoolean(false)
        val foundIp = AtomicReference<String?>(null)
        val foundPort = AtomicInteger(port)

        val executor = Executors.newFixedThreadPool(12)

        var host = start
        while (host <= end) {
            val targetIp = uintToIp(host)
            host++
            if (targetIp == localIpString) {
                continue
            }
            executor.execute {
                if (found.get()) return@execute
                val request = Request.Builder()
                    .url("http://$targetIp:$port/status")
                    .get()
                    .build()
                try {
                    discoveryClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val serverTokenHint = json.optString("tokenHint", "")
                        val paired = json.optBoolean("paired", false)
                        val pairedMainId = json.optString("pairedMainId", "")

                        if (paired &&
                            serverTokenHint == localTokenHint &&
                            (pairedMainId.isEmpty() || pairedMainId == localMainId)
                        ) {
                            if (found.compareAndSet(false, true)) {
                                foundIp.set(targetIp)
                                foundPort.set(json.optInt("port", port))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore individual host failures
                }
            }
        }

        executor.shutdown()
        val deadline = System.currentTimeMillis() + 4000L
        while (!executor.isTerminated && System.currentTimeMillis() < deadline && !found.get()) {
            executor.awaitTermination(200, TimeUnit.MILLISECONDS)
        }
        if (found.get()) {
            executor.shutdownNow()
        }

        val ipFound = foundIp.get()
        if (!ipFound.isNullOrEmpty()) {
            prefsManager.savePairing(ipFound, foundPort.get(), token, verified = true)
            setState(STATE_CONNECTED)
            val message = "🔁 Auto-reconnect: Display found at $ipFound:${foundPort.get()} ($reason)"
            Log.d(TAG, message)
            PhantomLog.i(message)
            showToast("Reconnected to Display")

            if (prefsManager.getPendingCount() > 0) {
                Log.d(TAG, "🔄 Auto-retry pending queue after reconnect")
                retryPending(null)
            }
        } else {
            Log.d(TAG, "ℹ️ Auto-reconnect: display not found ($reason)")
        }
    }

    private fun uintToIp(value: UInt): String {
        val b1 = (value and 0xFFu).toInt()
        val b2 = ((value shr 8) and 0xFFu).toInt()
        val b3 = ((value shr 16) and 0xFFu).toInt()
        val b4 = ((value shr 24) and 0xFFu).toInt()
        return "$b1.$b2.$b3.$b4"
    }
}
