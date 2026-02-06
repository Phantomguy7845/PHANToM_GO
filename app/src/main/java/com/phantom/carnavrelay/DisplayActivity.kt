package com.phantom.carnavrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    code = intent.getStringExtra("code") ?: "000000"

    val tv = TextView(this).apply {
      textSize = 26f
      text = "โหมดจอติดรถ\n\nรหัสจับคู่: $code\n\n(เปิดหน้านี้ค้างไว้)"
      setPadding(40, 80, 40, 80)
    }
    setContentView(tv)

    startForegroundService(Intent(this, DisplayServerService::class.java).apply {
      putExtra("code", code)
    })

    AlertDialog.Builder(this)
      .setTitle("โหมดจอติดรถ")
      .setMessage("ให้เอารหัส $code ไปใส่ในเครื่องหลัก\n\n*ควรจับคู่ Bluetooth ใน Settings ให้เรียบร้อยก่อน*")
      .setPositiveButton("OK", null)
      .show()
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
