package com.phantom.carnavrelay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.phantom.carnavrelay.PhantomLog

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
        PhantomLog.i("NAV openAttempt rawUrl=$url (OpenMapsReceiver)")
        
        try {
            val prefsManager = com.phantom.carnavrelay.PrefsManager(context)
            val openBehavior = prefsManager.getDisplayOpenBehavior()
            val previewUrl = com.phantom.carnavrelay.NavLinkUtils.toDisplayOpenUrl(
                url,
                prefsManager.getDisplayNavMode(),
                openBehavior
            )
            val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl)).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(mapsIntent)
            Log.d(TAG, "✅ Opened Google Maps")
            PhantomLog.i("NAV openSuccess via Google Maps (OpenMapsReceiver)")
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "⚠️ Google Maps not found, opening fallback")
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Log.d(TAG, "✅ Opened with fallback map app")
                PhantomLog.w("NAV openFallback success (OpenMapsReceiver)")
            } catch (e2: Exception) {
                Log.e(TAG, "💥 Failed to open Maps fallback", e2)
                PhantomLog.e("NAV openFail (OpenMapsReceiver): ${e2.message}", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to open Maps", e)
            PhantomLog.e("NAV openError (OpenMapsReceiver): ${e.message}", e)
        }
    }
}
