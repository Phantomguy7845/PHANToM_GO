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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.phantom.carnavrelay.bt.DisplayServerService

class DisplayActivity : AppCompatActivity() {

  private lateinit var code: String
  private var isConnected = false

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        BtConst.ACTION_OPEN_URL -> {
          val url = intent.getStringExtra(BtConst.EXTRA_URL) ?: return
          openMaps(url)
        }
        BtConst.ACTION_CONNECTED -> {
          isConnected = true
          updateConnectionStatus(true)
        }
        BtConst.ACTION_DISCONNECTED -> {
          isConnected = false
          updateConnectionStatus(false)
        }
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

    setContentView(R.layout.activity_display)

    code = intent.getStringExtra("code") ?: "000000"
    setupCodeDisplay()
    setupCopyButton()

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

  private fun setupCodeDisplay() {
    // Display code in 3 blocks: [12] [34] [56]
    val blocks = listOf(
      findViewById<TextView>(R.id.codeBlock1),
      findViewById<TextView>(R.id.codeBlock2),
      findViewById<TextView>(R.id.codeBlock3)
    )

    val chunkedCode = code.chunked(2)
    for (i in blocks.indices) {
      blocks[i].text = chunkedCode.getOrElse(i) { "--" }
    }
  }

  private fun setupCopyButton() {
    findViewById<MaterialButton>(R.id.copyButton)?.setOnClickListener {
      val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      val clip = android.content.ClipData.newPlainText("Pairing Code", code)
      clipboard.setPrimaryClip(clip)
      Toast.makeText(this, "คัดลอกรหัส $code", Toast.LENGTH_SHORT).show()
    }
  }

  private fun updateConnectionStatus(connected: Boolean) {
    val statusDot = findViewById<View>(R.id.statusDot)
    val statusText = findViewById<TextView>(R.id.statusText)

    if (connected) {
      statusDot?.background = ContextCompat.getDrawable(this, R.drawable.circle_status_connected)
      statusText?.text = "เชื่อมต่อแล้ว"
      statusText?.setTextColor(ContextCompat.getColor(this, R.color.success))
    } else {
      statusDot?.background = ContextCompat.getDrawable(this, R.drawable.circle_status_waiting)
      statusText?.text = getString(R.string.waiting_connection)
      statusText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
    }
  }

  override fun onStart() {
    super.onStart()
    val filter = IntentFilter().apply {
      addAction(BtConst.ACTION_OPEN_URL)
      addAction(BtConst.ACTION_CONNECTED)
      addAction(BtConst.ACTION_DISCONNECTED)
    }
    registerReceiver(receiver, filter)
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
