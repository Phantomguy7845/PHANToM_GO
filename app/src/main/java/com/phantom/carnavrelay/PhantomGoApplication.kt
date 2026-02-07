package com.phantom.carnavrelay

import android.app.Application
import android.util.Log

/**
 * PhantomGoApplication - Application class สำหรับ initialize CrashReporter
 * ต้องประกาศใน AndroidManifest.xml แอตทริบิวต์ android:name
 */
class PhantomGoApplication : Application() {

    companion object {
        const val TAG = "PHANTOM_GO"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 PhantomGoApplication onCreate")
        
        // Initialize crash reporter
        CrashReporter.init(this)
        
        Log.d(TAG, "✅ Application initialization complete")
    }
}
