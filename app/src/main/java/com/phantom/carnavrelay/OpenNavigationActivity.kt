package com.phantom.carnavrelay

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OpenNavigationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val EXTRA_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ OpenNavigationActivity.onCreate()")
        
        // Get URL from intent
        val url = intent.getStringExtra(EXTRA_URL)
        
        if (url.isNullOrEmpty()) {
            Log.e(TAG, "❌ No URL provided in intent")
            Toast.makeText(this, "No navigation URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        Log.d(TAG, "📍 Opening navigation URL: $url")
        
        try {
            // Create intent to open Google Maps
            val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Try to open Google Maps specifically
                setPackage("com.google.android.apps.maps")
            }
            
            startActivity(mapsIntent)
            Log.d(TAG, "✅ Successfully launched Google Maps")
            finish()
            
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "⚠️ Google Maps not found, opening in browser")
            try {
                // Fallback to any app that can handle the URL
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
                Log.d(TAG, "✅ Successfully launched browser")
                finish()
            } catch (e2: Exception) {
                Log.e(TAG, "💥 Failed to open any app for URL: $url", e2)
                Toast.makeText(this, "Cannot open navigation URL", Toast.LENGTH_LONG).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error opening navigation URL: $url", e)
            Toast.makeText(this, "Error opening navigation", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
