package com.phantom.carnavrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phantom.carnavrelay.receiver.OpenMapsReceiver

class DisplayServerService : Service() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "phantom_go_display"
        private const val ACTION_OPEN_MAPS = "com.phantom.carnavrelay.OPEN_MAPS"
        private const val ACTION_STOP_SERVER = "com.phantom.carnavrelay.STOP_SERVER"
        
        // Wake lock
        private const val WAKE_LOCK_TAG = "PHANTOM_GO:DisplayServer"
        private var wakeLock: PowerManager.WakeLock? = null
    }

    private var httpServer: HttpServer? = null
    private lateinit var prefsManager: PrefsManager
    private var overlayController: OverlayController? = null
    private var lastUrl: String? = null
    private var lastCmdAt: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "▶️ DisplayServerService.onCreate()")
        prefsManager = PrefsManager(this)
        overlayController = OverlayController(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ DisplayServerService.onStartCommand()")

        // Handle stop action
        if (intent?.action == ACTION_STOP_SERVER) {
            Log.d(TAG, "🛑 Stop server action received")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Handle navigation received
        if (intent?.action == "com.phantom.carnavrelay.NAVIGATION_RECEIVED") {
            val url = intent.getStringExtra("url")
            if (!url.isNullOrEmpty()) {
                onNavigationReceived(url)
            }
            return START_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d(TAG, "✅ Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Start HTTP server
        try {
            if (httpServer == null || !httpServer!!.isServerRunning()) {
                httpServer = HttpServer(this, prefsManager)
                httpServer?.start()
                Log.d(TAG, "✅ HTTP Server started successfully")
            } else {
                Log.d(TAG, "ℹ️ HTTP Server already running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to start HTTP Server", e)
            CrashReporter.recordException(this, "DisplayServerService:startHttpServer", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "💀 DisplayServerService.onDestroy()")
        
        // Cleanup overlay
        overlayController?.cleanup()
        
        // Cleanup HTTP server
        try {
            httpServer?.stop()
            httpServer = null
            Log.d(TAG, "🛑 HTTP Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error stopping HTTP Server", e)
        }
        
        // Release wake lock
        releaseWakeLock()
        
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Display Mode"
            val descriptionText = "HTTP Server running for Display mode"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val ip = httpServer?.getLocalIpAddress() ?: "0.0.0.0"
        val port = prefsManager.getServerPort()
        val tokenHint = prefsManager.getTokenHint(prefsManager.getServerToken())

        // Main notification intent
        val mainIntent = Intent(this, DisplayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Open Maps action
        val openMapsIntent = Intent(this, OpenMapsReceiver::class.java).apply {
            action = ACTION_OPEN_MAPS
            lastUrl?.let { putExtra(OpenMapsReceiver.EXTRA_URL, it) }
        }
        val openMapsPendingIntent = PendingIntent.getBroadcast(
            this, 1, openMapsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop server action
        val stopIntent = Intent(this, DisplayServerService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PHANToM GO - Display Mode")
            .setContentText("Server: $ip:$port | Token: $tokenHint")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_compass,
                "Open PHANToM GO",
                mainPendingIntent
            )

        // Add Open Maps action if there's a recent URL
        if (!lastUrl.isNullOrEmpty() && (System.currentTimeMillis() - lastCmdAt) < 300000) { // 5 minutes
            builder.addAction(
                android.R.drawable.ic_menu_directions,
                "Open Maps",
                openMapsPendingIntent
            )
        }

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop Server",
            stopPendingIntent
        )

        return builder.build()
    }

    fun updateNotification() {
        try {
            val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error updating notification", e)
        }
    }

    fun onNavigationReceived(url: String) {
        Log.d(TAG, "📡 Navigation received: $url")
        lastUrl = url
        lastCmdAt = System.currentTimeMillis()
        
        // Save to prefs for diagnostics
        val prefs = getSharedPreferences("phantom_go_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("last_cmd_at", lastCmdAt)
            putString("last_url", url)
            apply()
        }
        
        // Show navigation notification with Open Navigation action
        showNavigationNotification(url)
        
        // Check if app is in foreground
        if (isAppInForeground()) {
            Log.d(TAG, "📱 App in foreground, opening Maps directly")
            openMapsDirectly(url)
        } else {
            Log.d(TAG, "📱 App in background, showing notification")
            
            // Try to show overlay if enabled and permission granted
            overlayController?.let { controller ->
                if (controller.isOverlayEnabled() && controller.hasOverlayPermission()) {
                    Log.d(TAG, "🎯 Showing overlay widget")
                    controller.showNavigationOverlay(url)
                } else {
                    Log.d(TAG, "🚫 Overlay disabled or no permission, using notification only")
                    if (!controller.hasOverlayPermission()) {
                        Log.w(TAG, "⚠️ Overlay permission not granted")
                    }
                    if (!controller.isOverlayEnabled()) {
                        Log.d(TAG, "ℹ️ Overlay feature disabled in settings")
                    }
                }
            } ?: run {
                Log.w(TAG, "⚠️ Overlay controller not initialized")
            }
        }
    }
    
    private fun isAppInForeground(): Boolean {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningTasks = activityManager.getRunningTasks(1)
            if (runningTasks.isNotEmpty()) {
                val topActivity = runningTasks[0].topActivity
                return topActivity?.packageName == packageName
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not check foreground state", e)
        }
        return false
    }
    
    private fun openMapsDirectly(url: String) {
        try {
            val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (mapsIntent.resolveActivity(packageManager) != null) {
                startActivity(mapsIntent)
                Log.d(TAG, "✅ Opened Google Maps directly")
            } else {
                // Fallback
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallbackIntent)
                Log.d(TAG, "✅ Opened with fallback map app")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to open Maps directly", e)
            // Fallback to notification
            showNavigationNotification(url)
        }
    }
    
    private fun showNavigationNotification(url: String) {
        try {
            val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            
            // Create a high-priority notification for navigation
            val openNavIntent = Intent(this, OpenNavigationActivity::class.java).apply {
                putExtra("url", url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openNavPendingIntent = PendingIntent.getActivity(
                this, 2001, openNavIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🗺️ Navigation Received")
                .setContentText("Tap to open Maps")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.ic_menu_directions,
                    "Open Navigation",
                    openNavPendingIntent
                )
                .setFullScreenIntent(openNavPendingIntent, true)
                .build()
            
            notificationManager?.notify(NOTIFICATION_ID + 1, notification)
            Log.d(TAG, "✅ Navigation notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to show navigation notification", e)
        }
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(10*60*1000L) // 10 minutes
            }
            Log.d(TAG, "✅ Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to acquire wake lock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "✅ Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error releasing wake lock", e)
        }
    }
}
