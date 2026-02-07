package com.phantom.carnavrelay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RelayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
    }

    private lateinit var mainSender: MainSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ RelayActivity.onCreate()")

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

        Log.d(TAG, "📨 Received intent: action=$action, data=$data")

        if (action != Intent.ACTION_VIEW || data == null) {
            Log.w(TAG, "❌ Invalid intent received")
            finish()
            return
        }

        // Normalize the URL
        val normalizedUrl = normalizeUrl(data)
        
        if (normalizedUrl == null) {
            Log.w(TAG, "❌ Could not normalize URL: $data")
            Toast.makeText(this, "Invalid navigation URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "🔗 Normalized URL: $normalizedUrl")

        // Send to Display device
        if (!mainSender.isPaired()) {
            Log.w(TAG, "❌ Not paired with any display device")
            Toast.makeText(this, "Not paired! Please pair with a Display device first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        mainSender.sendOpenUrl(normalizedUrl, object : MainSender.Companion.SendCallback {
            override fun onSuccess() {
                showToast("Sent to display device!")
                finish()
            }

            override fun onFailure(error: String, queued: Boolean) {
                if (queued) {
                    showToast("Failed to send. Queued for retry.")
                } else {
                    showToast("Failed: $error")
                }
                finish()
            }
        })
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

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
