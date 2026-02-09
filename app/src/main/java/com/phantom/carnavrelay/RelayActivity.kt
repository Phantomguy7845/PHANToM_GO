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

class RelayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
    }

    private lateinit var mainSender: MainSender
    private lateinit var sendingText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ RelayActivity.onCreate()")
        
        // Set up sending screen
        setContentView(R.layout.activity_relay_sending)
        sendingText = findViewById(R.id.sendingText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        
        mainSender = MainSender(this)

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

        // Normalize raw -> mapsUrl
        val normalizedUrl = normalizeRawInput(raw)
        
        if (normalizedUrl == null) {
            Log.w(TAG, "❌ Could not normalize input: $raw")
            Toast.makeText(this, "Invalid navigation URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "🔗 Normalized URL: $normalizedUrl")

        // Send to Display device
        try {
            sendToDisplay(normalizedUrl)
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception in sendToDisplay", e)
            runOnUiThread {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            finish()
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
                    }, 1500)
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
                        finish()
                    }, 3000)
                }
            }
        })
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
