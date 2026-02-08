package com.phantom.carnavrelay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class OpenMapsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        const val EXTRA_URL = "url"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL)
        
        if (url.isNullOrEmpty()) {
            Log.e(TAG, "❌ OpenMapsReceiver: No URL provided")
            return
        }

        Log.d(TAG, "🗺️ OpenMapsReceiver: Opening URL: $url")
        
        try {
            // Try to open with Google Maps first
            val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if Google Maps is installed
            if (mapsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsIntent)
                Log.d(TAG, "✅ Opened Google Maps")
            } else {
                // Fallback to any map app
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Log.d(TAG, "✅ Opened with fallback map app")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to open Maps", e)
        }
    }
}
