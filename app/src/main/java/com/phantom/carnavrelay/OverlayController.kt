package com.phantom.carnavrelay

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat

class OverlayController(private val context: Context) {
    
    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val PREF_OVERLAY_ENABLED = "overlay_enabled"
    }

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var currentUrl: String? = null
    private val prefs: SharedPreferences = context.getSharedPreferences("phantom_go_prefs", Context.MODE_PRIVATE)
    private val prefsManager = PrefsManager(context)
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    fun isOverlayEnabled(): Boolean {
        return prefs.getBoolean(PREF_OVERLAY_ENABLED, false)
    }
    
    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_OVERLAY_ENABLED, enabled).apply()
        Log.d(TAG, "Overlay enabled: $enabled")
    }
    
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    fun showNavigationOverlay(url: String) {
        if (!isOverlayEnabled() || !hasOverlayPermission()) {
            Log.d(TAG, "Overlay not enabled or no permission")
            return
        }
        
        if (overlayView != null) {
            Log.d(TAG, "Overlay already showing, updating URL")
            currentUrl = url
            updateOverlayUrl(url)
            return
        }
        
        currentUrl = url
        Log.d(TAG, "🎯 Showing navigation overlay: $url")
        
        try {
            overlayView = LayoutInflater.from(context).inflate(R.layout.view_nav_overlay, null)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.CENTER
            params.x = 0
            params.y = 0
            
            windowManager?.addView(overlayView, params)
            
            setupOverlayControls()
            updateOverlayUrl(url)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show overlay", e)
        }
    }
    
    fun hideOverlay() {
        try {
            overlayView?.let { view ->
                val attached = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    view.isAttachedToWindow
                } else {
                    true
                }
                if (attached) {
                    windowManager?.removeView(view)
                    Log.d(TAG, "🚫 Navigation overlay hidden")
                } else {
                    Log.d(TAG, "ℹ️ Overlay view not attached; skipping remove")
                }
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to hide overlay", e)
        }
    }
    
    private fun setupOverlayControls() {
        overlayView?.let { view ->
            val openButton = view.findViewById<Button>(R.id.btnOpenNavigation)
            val dismissButton = view.findViewById<Button>(R.id.btnDismissOverlay)
            val urlText = view.findViewById<TextView>(R.id.txtOverlayUrl)
            
            openButton?.setOnClickListener {
                currentUrl?.let { url ->
                    openNavigationUrl(url)
                    hideOverlay()
                }
            }
            
            dismissButton?.setOnClickListener {
                hideOverlay()
            }
        }
    }
    
    private fun updateOverlayUrl(url: String) {
        overlayView?.let { view ->
            val urlText = view.findViewById<TextView>(R.id.txtOverlayUrl)
            urlText?.text = url
        }
    }
    
    private fun openNavigationUrl(url: String) {
        Log.d(TAG, "🧭 Opening navigation URL: $url")

        val openBehavior = prefsManager.getDisplayOpenBehavior()
        val previewUrl = NavLinkUtils.toDisplayOpenUrl(
            url,
            prefsManager.getDisplayNavMode(),
            openBehavior
        )
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl)).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
            Log.d(TAG, "✅ Maps opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open Maps", e)
            // Fallback to any maps app
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }
    
    fun cleanup() {
        hideOverlay()
        windowManager = null
    }
}
