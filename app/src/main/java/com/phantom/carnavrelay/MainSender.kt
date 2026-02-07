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
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainSender(private val context: Context) {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val TIMEOUT_SECONDS = 3L
        private const val MAX_RETRIES = 2
        
        // Callback interface for send results
        interface SendCallback {
            fun onSuccess()
            fun onFailure(error: String, queued: Boolean)
        }
    }

    private val prefsManager = PrefsManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isPaired(): Boolean {
        return prefsManager.isPaired()
    }

    fun getPairedInfo(): String {
        if (!isPaired()) return "Not paired"
        val ip = prefsManager.getPairedIp() ?: "unknown"
        val port = prefsManager.getPairedPort()
        val tokenHint = prefsManager.getPairedToken()?.let { prefsManager.getTokenHint(it) } ?: "****"
        return "$ip:$port (Token: $tokenHint)"
    }

    fun sendOpenUrl(url: String, callback: SendCallback? = null) {
        if (!isPaired()) {
            Log.w(TAG, "❌ Cannot send - not paired with any display device")
            callback?.onFailure("Not paired with any display device", false)
            return
        }

        val ip = prefsManager.getPairedIp() ?: ""
        val port = prefsManager.getPairedPort()
        val token = prefsManager.getPairedToken() ?: ""

        val endpoint = "http://$ip:$port/open-url"
        val jsonBody = JsonUtils.createPairingPayload(ip, port, token)
            .replace(""""ip":"$ip","port":$port,"token":"$token"""", 
                    """"token":"$token","url":"$url"""")

        Log.d(TAG, "📤 Sending URL to $endpoint")
        Log.d(TAG, "📦 Payload: $jsonBody")

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        sendWithRetry(request, url, callback, 0)
    }

    private fun sendWithRetry(request: Request, url: String, callback: SendCallback?, retryCount: Int) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "💥 Send failed (attempt ${retryCount + 1}): ${e.message}")
                
                if (retryCount < MAX_RETRIES) {
                    Log.d(TAG, "🔄 Retrying... (${retryCount + 1}/$MAX_RETRIES)")
                    // Retry after 500ms
                    mainHandler.postDelayed({
                        sendWithRetry(request, url, callback, retryCount + 1)
                    }, 500)
                } else {
                    // All retries failed - queue for later
                    Log.w(TAG, "❌ All retries failed, queuing URL: $url")
                    prefsManager.addToPendingQueue(url)
                    showToast("Failed to send. Queued for retry.")
                    callback?.onFailure("Failed after ${MAX_RETRIES + 1} attempts: ${e.message}", true)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Log.d(TAG, "📨 Response: ${response.code} - $body")
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ URL sent successfully")
                    showToast("Sent to display device!")
                    callback?.onSuccess()
                } else {
                    val errorMsg = "Server error: ${response.code}"
                    Log.e(TAG, "💥 $errorMsg")
                    
                    // Queue for retry unless it's an auth error
                    if (response.code == 401) {
                        showToast("Authentication failed. Check pairing.")
                        callback?.onFailure("Authentication failed", false)
                    } else {
                        prefsManager.addToPendingQueue(url)
                        showToast("Failed to send. Queued for retry.")
                        callback?.onFailure(errorMsg, true)
                    }
                }
                response.close()
            }
        })
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

                override fun onFailure(error: String, queued: Boolean) {
                    if (!queued) {
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
}
