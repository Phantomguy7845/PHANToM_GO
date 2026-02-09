package com.phantom.carnavrelay

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SendNavigationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "PHANTOM_GO"
        const val KEY_URL = "url"
        const val KEY_TIMESTAMP = "timestamp"
        private const val MAX_WAIT_MS = 10000L
        private const val MAX_ATTEMPTS = 2
    }

    override fun doWork(): Result {
        val rawUrl = inputData.getString(KEY_URL)
        if (rawUrl.isNullOrEmpty()) {
            Log.e(TAG, "❌ Worker missing URL input")
            PhantomLog.w("NAV worker missing URL")
            return Result.failure()
        }

        val prefsManager = PrefsManager(applicationContext)
        if (!prefsManager.isPaired()) {
            Log.w(TAG, "❌ Worker: not paired with display device")
            PhantomLog.w("NAV worker: not paired")
            NotificationHelper.showResult(applicationContext, "Failed to send ❌", allowToastFallback = true)
            return Result.failure()
        }

        val resolvedUrl = NavLinkUtils.resolveShortLinkIfNeeded(prefsManager, rawUrl)
        PhantomLog.i("NAV resolvedUrl=$resolvedUrl")

        val latch = CountDownLatch(1)
        val resultHolder = SendResultHolder()
        val sender = MainSender(applicationContext, toastEnabled = false, queueOnFailure = false)

        sender.sendOpenUrl(resolvedUrl, object : MainSender.Companion.SendCallback {
            override fun onSuccess() {
                resultHolder.success = true
                latch.countDown()
            }

            override fun onFailure(error: String, queued: Boolean, authFailed: Boolean) {
                resultHolder.success = false
                resultHolder.error = error
                resultHolder.authFailed = authFailed
                latch.countDown()
            }
        })

        val completed = latch.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS)
        if (!completed) {
            Log.w(TAG, "⚠️ Worker timeout waiting for send result")
            PhantomLog.w("NAV worker timeout")
            return Result.retry()
        }

        if (resultHolder.success) {
            Log.d(TAG, "✅ Worker: URL sent successfully")
            NotificationHelper.showResult(applicationContext, "Sent to car display ✅", allowToastFallback = true)
            return Result.success()
        }

        val error = resultHolder.error ?: "Unknown error"
        val permanentFailure = isPermanentFailure(error, resultHolder.authFailed)

        if (permanentFailure || runAttemptCount >= MAX_ATTEMPTS) {
            Log.e(TAG, "❌ Worker: permanent failure - $error")
            PhantomLog.w("NAV worker failure: $error")
            NotificationHelper.showResult(applicationContext, "Failed to send ❌", allowToastFallback = true)
            return Result.failure()
        }

        Log.w(TAG, "🔄 Worker: transient failure - $error")
        PhantomLog.w("NAV worker retry: $error")
        return Result.retry()
    }

    private fun isPermanentFailure(error: String, authFailed: Boolean): Boolean {
        if (authFailed) return true
        val lower = error.lowercase()
        return lower.contains("not paired") ||
            lower.contains("invalid pairing") ||
            lower.contains("authentication") ||
            lower.contains("forbidden")
    }

    private class SendResultHolder {
        var success: Boolean = false
        var error: String? = null
        var authFailed: Boolean = false
    }
}
