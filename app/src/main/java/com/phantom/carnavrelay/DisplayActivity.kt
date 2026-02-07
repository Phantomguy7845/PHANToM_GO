package com.phantom.carnavrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

    // Keep screen on
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // IMPORTANT: ต้อง setContentView ก่อนค่อยทำ insets/immersive เพื่อให้ decorView ถูกสร้างแล้ว
    setContentView(R.layout.activity_display)

    // หลัง setContentView แล้ว decorView จะพร้อม
    applyImmersiveSafe()

    code = intent.getStringExtra("code") ?: "000000"

    setupCodeDisplay()
    setupCopyButton()

    // Start service
    val svc = Intent(this, DisplayServerService::class.java).apply {
      putExtra("code", code)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
  }

  // กัน OEM/Android 15 บางรุ่นที่ insets controller ยังไม่พร้อมในจังหวะต้น ๆ
  private fun applyImmersiveSafe() {
    // Edge-to-edge (optional แต่ช่วยให้ compat ทำงานสม่ำเสมอ)
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // รันหลัง view ถูก attach จริง ๆ
    window.decorView.post {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val controller = WindowCompat.getInsetsController(window, window.decorView)
          controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
          @Suppress("DEPRECATION")
          window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              or View.SYSTEM_UI_FLAG_FULLSCREEN
              or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
      } catch (t: Throwable) {
        // ถ้า immersive ทำงานไม่ได้ ก็ไม่ควรทำให้แอป crash
        // (จะยังใช้งานต่อได้ แค่ไม่ fullscreen)
      }
    }
  }

  private fun setupCodeDisplay() {
    // Display code in 3 blocks: [12] [34] [56]
    val blocks = listOf(
      findViewById<android.widget.TextView>(R.id.codeBlock1),
      findViewById<android.widget.TextView>(R.id.codeBlock2),
      findViewById<android.widget.TextView>(R.id.codeBlock3)
    )
    val chunkedCode = code.chunked(2)
    for (i in blocks.indices) {
      blocks[i].text = chunkedCode.getOrElse(i) { "--" }
    }
  }

  private fun setupCopyButton() {
    findViewById<View>(R.id.copyButton)?.setOnClickListener {
      val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      val clip = android.content.ClipData.newPlainText("Pairing Code", code)
      clipboard.setPrimaryClip(clip)
      Toast.makeText(this, "คัดลอกรหัส $code", Toast.LENGTH_SHORT).show()
    }
  }

  private fun updateConnectionStatus(connected: Boolean) {
    val statusDot = findViewById<View>(R.id.statusDot)
    val statusText = findViewById<android.widget.TextView>(R.id.statusText)
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
