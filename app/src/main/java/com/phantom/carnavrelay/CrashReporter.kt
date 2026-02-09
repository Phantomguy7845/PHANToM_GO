package com.phantom.carnavrelay

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashReporter - บันทึก crash และ device info เมื่อแอป crash
 * ใช้ Thread.setDefaultUncaughtExceptionHandler ดักจับ exception ที่ไม่ได้จัดการ
 */
object CrashReporter {

    const val TAG = "PHANTOM_GO"
    const val CRASH_FILE_NAME = "crash_last.txt"

    private var application: Application? = null

    fun init(app: Application) {
        application = app
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "💥 CRASH detected on thread: ${thread.name}", throwable)
            
            // บันทึก crash info
            saveCrashReport(throwable)
            
            // เรียก handler เดิม (ให้ระบบ Android จัดการต่อ)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        Log.d(TAG, "✅ CrashReporter initialized")
    }

    private fun saveCrashReport(throwable: Throwable) {
        try {
            val app = application ?: return
            val crashFile = File(app.filesDir, CRASH_FILE_NAME)
            
            val report = buildCrashReport(throwable)
            crashFile.writeText(report)
            
            Log.d(TAG, "📝 Crash report saved to: ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save crash report", e)
        }
    }

    private fun buildCrashReport(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        
        // Header
        pw.println("================================")
        pw.println("PHANToM GO - CRASH REPORT")
        pw.println("================================")
        pw.println()
        
        // Timestamp
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        pw.println("Time: ${sdf.format(Date())}")
        pw.println()
        
        // Device Info
        pw.println("--- DEVICE INFO ---")
        pw.println("Manufacturer: ${Build.MANUFACTURER}")
        pw.println("Model: ${Build.MODEL}")
        pw.println("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println("ABIs: ${Build.SUPPORTED_ABIS.contentToString()}")
        pw.println()
        
        // App Info
        pw.println("--- APP INFO ---")
        try {
            val pm = application?.packageManager
            val pi = pm?.getPackageInfo(application?.packageName ?: "", 0)
            pw.println("Package: ${application?.packageName}")
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi?.longVersionCode?.toString() ?: "unknown"
            } else {
                @Suppress("DEPRECATION")
                pi?.versionCode?.toString() ?: "unknown"
            }
            pw.println("Version: ${pi?.versionName} ($versionCode)")
        } catch (e: Exception) {
            pw.println("Package: (unable to get)")
        }
        pw.println()
        
        // Stack Trace
        pw.println("--- STACK TRACE ---")
        throwable.printStackTrace(pw)
        pw.println()
        
        // Cause chain
        var cause = throwable.cause
        var level = 1
        while (cause != null) {
            pw.println()
            pw.println("--- CAUSE LEVEL $level ---")
            cause.printStackTrace(pw)
            cause = cause.cause
            level++
        }
        
        pw.println()
        pw.println("================================")
        pw.println("END OF REPORT")
        pw.println("================================")
        
        pw.flush()
        return sw.toString()
    }

    fun hasCrashReport(): Boolean {
        val app = application ?: return false
        return File(app.filesDir, CRASH_FILE_NAME).exists()
    }

    fun getCrashReport(): String? {
        val app = application ?: return null
        val crashFile = File(app.filesDir, CRASH_FILE_NAME)
        return if (crashFile.exists()) crashFile.readText() else null
    }

    fun clearCrashReport() {
        val app = application ?: return
        val crashFile = File(app.filesDir, CRASH_FILE_NAME)
        if (crashFile.exists()) {
            crashFile.delete()
            Log.d(TAG, "🗑️ Crash report cleared")
        }
    }

    /**
     * Record an exception manually (for caught exceptions in services)
     */
    fun recordException(context: Context, where: String, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)

            pw.println("--- MANUAL EXCEPTION RECORD ---")
            pw.println("Location: $where")
            pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            pw.println()
            pw.println("--- STACK TRACE ---")
            throwable.printStackTrace(pw)
            pw.println()
            pw.println("================================")

            pw.flush()

            val crashFile = File(context.filesDir, "crash_exceptions.txt")
            crashFile.appendText(sw.toString() + "\n\n")

            Log.d(TAG, "📝 Exception recorded at $where: ${throwable.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to record exception", e)
        }
    }

    fun lastCrashText(context: Context): String? {
        val crashFile = File(context.filesDir, CRASH_FILE_NAME)
        return if (crashFile.exists()) crashFile.readText() else null
    }

    /**
     * Check and show crash dialog if there's a pending crash report
     * Call this from MainActivity.onCreate()
     */
    fun checkAndShowCrashReport(activity: MainActivity) {
        if (hasCrashReport()) {
            Log.d(TAG, "🔍 Found pending crash report, showing diagnostics")
            val intent = Intent(activity, DiagnosticsActivity::class.java).apply {
                putExtra("show_crash", true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            activity.startActivity(intent)
        }
    }
}
