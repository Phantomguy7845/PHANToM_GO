package com.phantom.carnavrelay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class RelayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
    }

    private lateinit var mainSender: MainSender
    private lateinit var prefsManager: PrefsManager
    private lateinit var sendingText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private val resolverClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ RelayActivity.onCreate()")
        
        // Set up sending screen
        setContentView(R.layout.activity_relay_sending)
        sendingText = findViewById(R.id.sendingText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        
        mainSender = MainSender(this)
        prefsManager = PrefsManager(this)

        // Handle the intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "▶️ RelayActivity.onNewIntent()")
        if (intent != null) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data
        val type = intent.type
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

        Log.d(TAG, "📨 Received intent: action=$action, data=$data, type=$type")
        Log.d(TAG, "📨 Extras: EXTRA_TEXT=$extraText, EXTRA_PROCESS_TEXT=$processText")

        // Extract raw URL/text from different intent types
        val raw = when (action) {
            Intent.ACTION_VIEW -> {
                // ACTION_VIEW: raw = intent.data?.toString()
                data?.toString()
            }
            Intent.ACTION_SEND -> {
                // ACTION_SEND (text/plain): raw = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (type == "text/plain") extraText else null
            }
            Intent.ACTION_PROCESS_TEXT -> {
                // ACTION_PROCESS_TEXT: raw = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                processText
            }
            else -> {
                Log.w(TAG, "❌ Unsupported action: $action")
                null
            }
        }

        // If raw is empty, log and finish
        if (raw.isNullOrEmpty()) {
            Log.e(TAG, "❌ NO_RAW: No data received in intent")
            Log.e(TAG, "❌ NO_RAW: Intent details: action=$action, data=$data, type=$type, extraText=$extraText, processText=$processText")
            runOnUiThread {
                Toast.makeText(this, "No URL or text received", Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        Log.d(TAG, "📋 Raw input: $raw")
        PhantomLog.i("NAV rawUrl=$raw")

        // Normalize raw -> mapsUrl
        val normalizedUrl = normalizeRawInput(raw)
        
        if (normalizedUrl == null) {
            Log.w(TAG, "❌ Could not normalize input: $raw")
            Toast.makeText(this, "Invalid navigation URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "🔗 Normalized URL: $normalizedUrl")
        PhantomLog.i("NAV normalizedUrl=$normalizedUrl")

        resolveShortLinkIfNeeded(normalizedUrl) { resolvedUrl, resolved ->
            PhantomLog.i("NAV resolvedUrl=$resolvedUrl (resolved=$resolved)")
            try {
                sendToDisplay(resolvedUrl)
            } catch (e: Exception) {
                Log.e(TAG, "💥 Exception in sendToDisplay", e)
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }
    
    private fun sendToDisplay(mapsUrl: String) {
        if (!mainSender.isPaired()) {
            Log.w(TAG, "❌ Not paired with any display device")
            runOnUiThread {
                statusText.text = "Not paired!"
                sendingText.text = "Pairing required"
                Toast.makeText(this@RelayActivity, "Not paired! Please pair with a Display device first.", Toast.LENGTH_LONG).show()
                handler.postDelayed({ finish() }, 2000)
            }
            return
        }

        Log.d(TAG, "📤 Sending URL to display: $mapsUrl")
        PhantomLog.i("NAV sendUrl=$mapsUrl")
        
        // Update UI
        runOnUiThread {
            statusText.text = "Sending navigation..."
            sendingText.text = mapsUrl
            progressBar.visibility = View.VISIBLE
        }
        
        mainSender.sendOpenUrl(mapsUrl, object : MainSender.Companion.SendCallback {
            override fun onSuccess() {
                Log.d(TAG, "✅ URL sent successfully to display")
                runOnUiThread {
                    statusText.text = "Sent successfully!"
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@RelayActivity, "Sent to display device!", Toast.LENGTH_SHORT).show()
                    
                    // Auto-finish after showing success
                    handler.postDelayed({
                        finish()
                        overridePendingTransition(0, 0)
                    }, 500)
                }
            }

            override fun onFailure(error: String, queued: Boolean, authFailed: Boolean) {
                Log.e(TAG, "❌ Failed to send URL: $error, queued=$queued, authFailed=$authFailed")
                runOnUiThread {
                    statusText.text = "Failed: $error"
                    sendingText.text = "Error occurred"
                    progressBar.visibility = View.GONE
                    
                    if (queued) {
                        Toast.makeText(this@RelayActivity, "Failed to send. Queued for retry.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@RelayActivity, "Failed: $error", Toast.LENGTH_LONG).show()
                    }
                    
                    // Auto-finish after showing error
                    handler.postDelayed({
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            finishAndRemoveTask()
                        } else {
                            finish()
                        }
                        overridePendingTransition(0, 0)
                    }, 2000)
                }
            }
        })
    }

    private fun resolveShortLinkIfNeeded(url: String, callback: (String, Boolean) -> Unit) {
        if (!isShortMapsLink(url)) {
            callback(url, false)
            return
        }

        val cached = prefsManager.getResolvedUrlFor(url)
        if (!cached.isNullOrEmpty()) {
            Log.d(TAG, "🔁 Resolved URL from cache: $cached")
            PhantomLog.i("NAV resolvedCache hit: $url -> $cached")
            callback(cached, true)
            return
        }

        Log.d(TAG, "🔎 Resolving short URL: $url")
        PhantomLog.i("NAV resolve start: $url")
        resolveWithHead(url, 0, callback)
    }

    private fun resolveWithHead(url: String, attempt: Int, callback: (String, Boolean) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        resolverClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "⚠️ HEAD resolve failed (attempt ${attempt + 1}): ${e.message}")
                if (attempt < 1) {
                    resolveWithHead(url, attempt + 1, callback)
                } else {
                    PhantomLog.w("NAV resolve HEAD failed, fallback to original: $url")
                    callback(url, false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val finalUrl = response.request.url.toString()
                    val hasRedirect = response.priorResponse != null
                    val location = response.header("Location")

                    if (hasRedirect || !location.isNullOrEmpty()) {
                        Log.d(TAG, "✅ HEAD resolved: $finalUrl")
                        prefsManager.putResolvedUrl(url, finalUrl)
                        callback(finalUrl, true)
                        return
                    }

                    if (response.code == 405) {
                        resolveWithGet(url, attempt, callback)
                        return
                    }

                    // No redirect info; try GET to resolve
                    resolveWithGet(url, attempt, callback)
                }
            }
        })
    }

    private fun resolveWithGet(url: String, attempt: Int, callback: (String, Boolean) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        resolverClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "⚠️ GET resolve failed (attempt ${attempt + 1}): ${e.message}")
                if (attempt < 1) {
                    resolveWithGet(url, attempt + 1, callback)
                } else {
                    PhantomLog.w("NAV resolve GET failed, fallback to original: $url")
                    callback(url, false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val finalUrl = response.request.url.toString()
                    if (finalUrl != url) {
                        Log.d(TAG, "✅ GET resolved: $finalUrl")
                        prefsManager.putResolvedUrl(url, finalUrl)
                        callback(finalUrl, true)
                    } else {
                        Log.w(TAG, "⚠️ GET resolve returned same URL, using original")
                        PhantomLog.w("NAV resolve GET same URL, fallback: $url")
                        callback(url, false)
                    }
                }
            }
        })
    }

    private fun isShortMapsLink(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            when {
                host == "maps.app.goo.gl" -> true
                host == "goo.gl" && uri.path?.contains("/app/maps") == true -> true
                host == "goo.gl" && uri.path?.contains("/maps") == true -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun normalizeRawInput(raw: String): String? {
        // Try to parse as URI first
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            null
        }
        
        return when {
            // If it's a valid URI with scheme, use normalizeUrl
            uri?.scheme != null -> normalizeUrl(uri)
            // If it looks like coordinates (lat,lng), create geo URI
            raw.matches(Regex("""^-?\d+\.?\d*,-?\d+\.?\d*\$""")) -> {
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(raw)}"
            }
            // If it's a search query, create search URL
            else -> {
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(raw)}"
            }
        }
    }

    private fun normalizeUrl(uri: Uri): String? {
        return when (uri.scheme) {
            "geo" -> {
                // geo:lat,lng or geo:0,0?q=...
                val geoData = uri.schemeSpecificPart
                val query = uri.query
                
                if (query != null && query.startsWith("q=")) {
                    // geo:0,0?q=search+query
                    val searchQuery = query.substring(2).replace("+", " ")
                    "https://www.google.com/maps/search/?api=1&query=${Uri.encode(searchQuery)}"
                } else if (geoData.contains(",")) {
                    // geo:lat,lng
                    val parts = geoData.split(",")
                    if (parts.size >= 2) {
                        val lat = parts[0]
                        val lng = parts[1].substringBefore(";") // Remove any additional params
                        "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
                    } else null
                } else null
            }
            
            "google.navigation" -> {
                // google.navigation:q=...
                val query = uri.getQueryParameter("q")
                if (query != null) {
                    "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(query)}"
                } else null
            }
            
            "https" -> {
                when {
                    uri.host?.contains("google.com") == true && uri.path?.contains("/maps") == true -> {
                        // Google Maps URL - use as-is
                        uri.toString()
                    }
                    uri.host?.contains("maps.app.goo.gl") == true -> {
                        // Shortened Google Maps URL - use as-is
                        uri.toString()
                    }
                    else -> uri.toString()
                }
            }
            
            else -> uri.toString()
        }
    }
}
