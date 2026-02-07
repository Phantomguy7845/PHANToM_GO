package com.phantom.carnavrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.phantom.carnavrelay.bt.DisplayServerService

class DisplayActivity : AppCompatActivity() {

  private lateinit var code: String

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action == BtConst.ACTION_OPEN_URL) {
        val url = intent.getStringExtra(BtConst.EXTRA_URL) ?: return
        openMaps(url)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Keep screen on and full screen
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.insetsController?.let {
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        it.hide(WindowInsets.Type.systemBars())
      }
    } else {
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        or View.SYSTEM_UI_FLAG_FULLSCREEN
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
      )
    }

    code = intent.getStringExtra("code") ?: "000000"

    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(64, 64, 64, 64)
      gravity = android.view.Gravity.CENTER
      setBackgroundColor(ContextCompat.getColor(this@DisplayActivity, R.color.purple_700))
    }

    // App title
    val titleView = TextView(this).apply {
      text = getString(R.string.app_name_display)
      textSize = 24f
      setTextColor(ContextCompat.getColor(this@DisplayActivity, R.color.white))
      setPadding(0, 0, 0, 48)
      gravity = android.view.Gravity.CENTER
    }

    // Code card
    val codeCard = MaterialCardView(this).apply {
      radius = 32f
      cardElevation = 16f
      setContentPadding(64, 64, 64, 64)
      setCardBackgroundColor(ContextCompat.getColor(this@DisplayActivity, R.color.white))
    }

    val codeLayout = LinearLayout(this@DisplayActivity).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
    }

    val codeLabel = TextView(this@DisplayActivity).apply {
      text = getString(R.string.display_code_label)
      textSize = 18f
      setTextColor(ContextCompat.getColor(this@DisplayActivity, R.color.purple_700))
      setPadding(0, 0, 0, 24)
      gravity = android.view.Gravity.CENTER
    }

    val codeView = TextView(this@DisplayActivity).apply {
      text = code.chunked(2).joinToString(" ")
      textSize = 64f
      setTextColor(ContextCompat.getColor(this@DisplayActivity, R.color.black))
      setPadding(0, 0, 0, 24)
      gravity = android.view.Gravity.CENTER
    }

    val codeHint = TextView(this@DisplayActivity).apply {
      text = getString(R.string.display_code_hint)
      textSize = 14f
      setTextColor(ContextCompat.getColor(this@DisplayActivity, R.color.purple_500))
      alpha = 0.8f
      gravity = android.view.Gravity.CENTER
    }

    codeLayout.addView(codeLabel)
    codeLayout.addView(codeView)
    codeLayout.addView(codeHint)
    codeCard.addView(codeLayout)

    // Status text
    val statusView = TextView(this).apply {
      text = getString(R.string.waiting_connection)
      textSize = 16f
      setTextColor(ContextCompat.getColor(this@DisplayActivity, R.color.white))
      setPadding(0, 48, 0, 0)
      gravity = android.view.Gravity.CENTER
      alpha = 0.7f
    }

    // Keep screen on hint
    val keepScreenOnView = TextView(this).apply {
      text = getString(R.string.keep_screen_on)
      textSize = 14f
      setTextColor(ContextCompat.getColor(this@DisplayActivity, R.color.teal_200))
      setPadding(0, 24, 0, 0)
      gravity = android.view.Gravity.CENTER
    }

    layout.addView(titleView)
    layout.addView(codeCard)
    layout.addView(statusView)
    layout.addView(keepScreenOnView)

    setContentView(layout)

    // Start service
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(Intent(this, DisplayServerService::class.java).apply {
        putExtra("code", code)
      })
    } else {
      startService(Intent(this, DisplayServerService::class.java).apply {
        putExtra("code", code)
      })
    }
  }

  override fun onStart() {
    super.onStart()
    registerReceiver(receiver, IntentFilter(BtConst.ACTION_OPEN_URL))
  }

  override fun onStop() {
    unregisterReceiver(receiver)
    super.onStop()
  }

  private fun openMaps(url: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      })
    } catch (_: Throwable) {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      })
    }
  }
}
