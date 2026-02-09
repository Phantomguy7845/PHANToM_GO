package com.phantom.carnavrelay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object PhantomLog {
    private const val TAG = "PHANTOM_GO"
    private const val PREF_NAME = "phantom_logs"
    private const val MAX_LOGS = 1000
    private const val LOG_KEY_PREFIX = "log_"
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val memoryLogs = ConcurrentLinkedQueue<String>()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Load existing logs into memory
        loadExistingLogs()
    }

    private fun loadExistingLogs() {
        prefs?.let { sp ->
            for (i in 0 until MAX_LOGS) {
                val key = "$LOG_KEY_PREFIX$i"
                val log = sp.getString(key, null)
                if (log != null) {
                    memoryLogs.offer(log)
                }
            }
        }
    }

    fun d(message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLog = "[$timestamp] DEBUG: $message"
        
        Log.d(TAG, message)
        addLog(formattedLog)
    }

    fun e(message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val formattedLog = if (throwable != null) {
            "[$timestamp] ERROR: $message\n${Log.getStackTraceString(throwable)}"
        } else {
            "[$timestamp] ERROR: $message"
        }
        
        Log.e(TAG, message, throwable)
        addLog(formattedLog)
    }

    fun w(message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLog = "[$timestamp] WARN: $message"
        
        Log.w(TAG, message)
        addLog(formattedLog)
    }

    fun i(message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLog = "[$timestamp] INFO: $message"
        
        Log.i(TAG, message)
        addLog(formattedLog)
    }

    private fun addLog(log: String) {
        memoryLogs.offer(log)
        
        // Keep only last MAX_LOGS in memory
        while (memoryLogs.size > MAX_LOGS) {
            memoryLogs.poll()
        }
        
        // Save to preferences (async)
        saveToPrefs()
    }

    private fun saveToPrefs() {
        prefs?.let { sp ->
            val editor = sp.edit()
            val logs = memoryLogs.toList()
            
            // Clear old logs
            for (i in 0 until MAX_LOGS) {
                editor.remove("$LOG_KEY_PREFIX$i")
            }
            
            // Save new logs
            logs.forEachIndexed { index, log ->
                editor.putString("$LOG_KEY_PREFIX$index", log)
            }
            
            editor.apply()
        }
    }

    fun getLogs(limit: Int = MAX_LOGS): List<String> {
        return memoryLogs.toList().takeLast(limit)
    }

    fun clearLogs() {
        memoryLogs.clear()
        prefs?.let { sp ->
            sp.edit().clear().apply()
        }
    }

    fun getLogCount(): Int {
        return memoryLogs.size
    }
}
