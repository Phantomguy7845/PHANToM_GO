package com.phantom.carnavrelay

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

class SilentShareReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val WORK_NAME = "send_nav"
        private const val EXTRA_TEXT = "android.intent.extra.TEXT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "▶️ SilentShareReceiverActivity.onCreate()")

        NotificationHelper.ensureChannel(this)

        val prefsManager = PrefsManager(applicationContext)
        val deviceMode = prefsManager.getDeviceMode()
        val isDisplayMode = deviceMode == PrefsManager.MODE_DISPLAY || isDisplayModeActive()

        if (isDisplayMode) {
            Log.w(TAG, "🚫 Display mode active - ignoring external intent")
            PhantomLog.w("NAV ignored: device in Display mode")
            NotificationHelper.showInfo(this, "This device is Display mode", allowToastFallback = true)
            finishNoAnim()
            return
        }

        val raw = extractRawInput(intent)
        if (raw.isNullOrEmpty()) {
            Log.e(TAG, "❌ NO_RAW: No data received in intent")
            PhantomLog.w("NAV no raw input")
            NotificationHelper.showResult(this, "Failed to send ❌", allowToastFallback = true)
            finishNoAnim()
            return
        }

        Log.d(TAG, "📋 Raw input: $raw")
        PhantomLog.i("NAV rawUrl=$raw")

        val normalizedUrl = NavLinkUtils.normalizeRawInput(raw)
        if (normalizedUrl.isNullOrEmpty()) {
            Log.w(TAG, "❌ Could not normalize input: $raw")
            PhantomLog.w("NAV normalize failed: $raw")
            NotificationHelper.showResult(this, "Failed to send ❌", allowToastFallback = true)
            finishNoAnim()
            return
        }

        Log.d(TAG, "🔗 Normalized URL: $normalizedUrl")
        PhantomLog.i("NAV normalizedUrl=$normalizedUrl")

        val data = workDataOf(
            SendNavigationWorker.KEY_URL to normalizedUrl,
            SendNavigationWorker.KEY_TIMESTAMP to System.currentTimeMillis()
        )

        val request = OneTimeWorkRequestBuilder<SendNavigationWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)

        NotificationHelper.showSending(this, allowToastFallback = true)
        finishNoAnim()
    }

    private fun extractRawInput(intent: Intent): String? {
        val action = intent.action
        val type = intent.type
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

        return when (action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> {
                if (type == "text/plain") {
                    extraText ?: readClipData(intent.clipData)
                } else {
                    null
                }
            }
            Intent.ACTION_PROCESS_TEXT -> processText
            else -> {
                Log.w(TAG, "❌ Unsupported action: $action")
                extraText ?: intent.getStringExtra(EXTRA_TEXT) ?: readClipData(intent.clipData)
            }
        }
    }

    private fun readClipData(clipData: ClipData?): String? {
        if (clipData == null || clipData.itemCount == 0) return null
        val item = clipData.getItemAt(0)
        return item.text?.toString() ?: item.uri?.toString()
    }

    private fun isDisplayModeActive(): Boolean {
        return try {
            val activityManager = getSystemService(android.app.ActivityManager::class.java)
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            services.any { it.service.className == DisplayServerService::class.java.name }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not check DisplayServerService state", e)
            false
        }
    }

    private fun finishNoAnim() {
        finish()
        overridePendingTransition(0, 0)
    }
}
