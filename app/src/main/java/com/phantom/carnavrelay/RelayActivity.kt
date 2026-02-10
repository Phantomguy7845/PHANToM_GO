package com.phantom.carnavrelay

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class RelayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PHANTOM_GO"
        private const val EXTRA_TEXT = "android.intent.extra.TEXT"
    }

    private lateinit var mainSender: MainSender
    private lateinit var prefsManager: PrefsManager
    private lateinit var sendingText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var actionButton: Button

    private var lastRaw: String? = null
    private var requestId: Int = 0
    private var hasOpenedMaps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relay_sending)

        sendingText = findViewById(R.id.sendingText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        actionButton = findViewById(R.id.actionButton)

        mainSender = MainSender(applicationContext, toastEnabled = false)
        prefsManager = PrefsManager(applicationContext)

        actionButton.visibility = View.GONE
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        requestId += 1
        val currentRequestId = requestId
        resetUiForNewRequest()

        if (isDisplayModeActive()) {
            Log.w(TAG, "🚫 Display mode active - ignoring external intent")
            PhantomLog.w("NAV ignored: device in Display mode")
            showError("This device is Display mode", retryable = false)
            return
        }

        val raw = extractRawInput(intent)
        if (raw.isNullOrEmpty()) {
            Log.e(TAG, "❌ NO_RAW: No data received in intent")
            PhantomLog.w("NAV no raw input")
            showError("No data received", retryable = false)
            return
        }

        lastRaw = raw
        Log.d(TAG, "📋 Raw input: $raw")
        PhantomLog.i("NAV rawUrl=$raw")

        startSendFlow(raw, currentRequestId)
    }

    private fun startSendFlow(raw: String, currentRequestId: Int) {
        val normalizedUrl = NavLinkUtils.normalizeRawInput(raw)
        if (normalizedUrl.isNullOrEmpty()) {
            Log.w(TAG, "❌ Could not normalize input: $raw")
            PhantomLog.w("NAV normalize failed: $raw")
            showError("Invalid link", retryable = false)
            return
        }

        updateStatus("Resolving link…", R.color.aurora_mint)

        Thread {
            val resolvedUrl = NavLinkUtils.resolveShortLinkIfNeeded(prefsManager, normalizedUrl)
            runOnUiThread {
                if (currentRequestId != requestId) return@runOnUiThread
                updateStatus("Sending to Display…", R.color.aurora_mint)
                sendToDisplay(resolvedUrl, currentRequestId)
            }
        }.start()
    }

    private fun sendToDisplay(resolvedUrl: String, currentRequestId: Int) {
        if (!mainSender.isPaired()) {
            Log.w(TAG, "❌ Not paired with any display device")
            showError("Not paired with Display device", retryable = false)
            return
        }

        Log.d(TAG, "📤 Sending URL to display: $resolvedUrl")
        PhantomLog.i("NAV sendUrl=$resolvedUrl")

        mainSender.sendOpenUrl(resolvedUrl, object : MainSender.Companion.SendCallback {
            override fun onSuccess() {
                runOnUiThread {
                    if (currentRequestId != requestId) return@runOnUiThread
                    Log.d(TAG, "✅ URL sent successfully to display")
                    updateStatus("Sent! Opening Google Maps…", R.color.aurora_success)
                    openMapsAndExit(resolvedUrl)
                }
            }

            override fun onFailure(error: String, queued: Boolean, authFailed: Boolean) {
                runOnUiThread {
                    if (currentRequestId != requestId) return@runOnUiThread
                    Log.e(TAG, "❌ Failed to send URL: $error, queued=$queued, authFailed=$authFailed")
                    showError("Send failed: $error", retryable = true)
                }
            }
        })
    }

    private fun openMapsAndExit(url: String) {
        if (hasOpenedMaps) return
        hasOpenedMaps = true

        val uri = Uri.parse(url)
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (mapsIntent.resolveActivity(packageManager) != null) {
                startActivity(mapsIntent)
                closeAndRemoveFromRecents()
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Google Maps intent resolve failed", e)
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val hasExternalHandler = try {
            val handlers = packageManager.queryIntentActivities(
                fallbackIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            handlers.any { it.activityInfo.packageName != packageName }
        } catch (e: Exception) {
            false
        }

        if (hasExternalHandler) {
            try {
                startActivity(fallbackIntent)
                closeAndRemoveFromRecents()
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "⚠️ Fallback maps intent failed", e)
            }
        }

        hasOpenedMaps = false
        Toast.makeText(this, "ไม่พบ Google Maps", Toast.LENGTH_SHORT).show()
        showError("ไม่พบ Google Maps", retryable = false)
    }

    private fun closeAndRemoveFromRecents() {
        try {
            if (!isTaskRoot) {
                finishAffinity()
            }
            finishAndRemoveTask()
        } catch (e: Exception) {
            finish()
        }
        overridePendingTransition(0, 0)
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

    private fun resetUiForNewRequest() {
        sendingText.text = "Sending to Display..."
        statusText.text = "Preparing..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.aurora_mint))
        progressBar.visibility = View.VISIBLE
        actionButton.visibility = View.GONE
        hasOpenedMaps = false
    }

    private fun updateStatus(message: String, colorRes: Int) {
        statusText.text = message
        statusText.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showError(message: String, retryable: Boolean) {
        progressBar.visibility = View.GONE
        updateStatus(message, R.color.aurora_error)

        if (retryable && !lastRaw.isNullOrEmpty()) {
            actionButton.text = "Retry"
            actionButton.visibility = View.VISIBLE
            actionButton.setOnClickListener {
                requestId += 1
                resetUiForNewRequest()
                startSendFlow(lastRaw!!, requestId)
            }
        } else {
            actionButton.text = "Close"
            actionButton.visibility = View.VISIBLE
            actionButton.setOnClickListener { finish() }
        }
    }

    private fun isDisplayModeActive(): Boolean {
        val deviceMode = prefsManager.getDeviceMode()
        if (deviceMode == PrefsManager.MODE_DISPLAY) return true

        return try {
            val activityManager = getSystemService(android.app.ActivityManager::class.java)
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            services.any { it.service.className == DisplayServerService::class.java.name }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not check DisplayServerService state", e)
            false
        }
    }
}
