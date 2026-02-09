package com.phantom.carnavrelay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val TAG = "PHANTOM_GO"
    private const val CHANNEL_ID = "phantom_go_main"
    private const val NOTIFICATION_ID = 3001
    private const val NOTIFICATION_TIMEOUT_MS = 4000L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PHANToM GO (Main)"
            val descriptionText = "Share status notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showSending(context: Context, allowToastFallback: Boolean) {
        showNotification(context, "Sending to car display…", allowToastFallback, forceAlert = false)
    }

    fun showResult(context: Context, message: String, allowToastFallback: Boolean) {
        showNotification(context, message, allowToastFallback, forceAlert = true)
    }

    fun showInfo(context: Context, message: String, allowToastFallback: Boolean) {
        showNotification(context, message, allowToastFallback, forceAlert = true)
    }

    private fun showNotification(
        context: Context,
        text: String,
        allowToastFallback: Boolean,
        forceAlert: Boolean
    ) {
        ensureChannel(context)
        if (!canPostNotifications(context)) {
            Log.w(TAG, "🔕 Notification permission missing or blocked")
            PhantomLog.w("NOTIF blocked: $text")
            if (allowToastFallback) {
                showToast(context, text)
            }
            return
        }

        if (forceAlert) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_main_mode)
            .setContentTitle("PHANToM GO")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_main_mode, "Open app", openPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun showToast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
