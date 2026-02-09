package com.phantom.carnavrelay

import android.app.Activity
import android.app.Application
import android.util.Log

/**
 * PhantomGoApplication - Application class สำหรับ initialize CrashReporter
 * ต้องประกาศใน AndroidManifest.xml แอตทริบิวต์ android:name
 */
class PhantomGoApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val TAG = "PHANTOM_GO"
        @Volatile private var startedCount = 0
        @Volatile private var inForeground = false

        fun isInForeground(): Boolean = inForeground
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 PhantomGoApplication onCreate")
        
        // Initialize crash reporter
        CrashReporter.init(this)

        registerActivityLifecycleCallbacks(this)
        
        Log.d(TAG, "✅ Application initialization complete")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        startedCount += 1
        inForeground = startedCount > 0
        if (startedCount == 1) {
            try {
                val intent = android.content.Intent(DisplayServerService.ACTION_APP_FOREGROUND).apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to broadcast APP_FOREGROUND", e)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        inForeground = startedCount > 0
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
