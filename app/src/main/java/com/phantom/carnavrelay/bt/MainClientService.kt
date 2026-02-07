package com.phantom.carnavrelay.bt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phantom.carnavrelay.BtConst
import com.phantom.carnavrelay.R
import org.json.JSONObject
import java.io.PrintWriter

class MainClientService : Service() {

  private var address: String? = null
  private var code: String? = null

  @Volatile private var socket: BluetoothSocket? = null
  @Volatile private var writer: PrintWriter? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val mode = intent?.getStringExtra("mode")

    // ส่งอย่างเดียวจาก RelayActivity
    val sendUrl = intent?.getStringExtra("send_url")
    if (mode == "SEND_ONLY" && sendUrl != null) {
      sendOpenUrl(sendUrl)
      return START_NOT_STICKY
    }

    // เริ่มเชื่อมต่อค้างไว้
    address = intent?.getStringExtra("address") ?: address
    code = intent?.getStringExtra("code") ?: code

    startForeground(2002, notif("เครื่องหลักกำลังเชื่อมต่อจอติดรถ…"))
    connectIfNeeded()

    return START_STICKY
  }

  private fun connectIfNeeded() {
    if (socket != null && socket!!.isConnected) return

    Thread {
      try {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@Thread
        val dev = adapter.getRemoteDevice(address) ?: return@Thread

        val s = dev.createRfcommSocketToServiceRecord(BtConst.APP_UUID)
        adapter.cancelDiscovery()
        s.connect()

        socket = s
        writer = PrintWriter(s.outputStream, true)

        // ส่ง HELLO + code
        val hello = JSONObject().apply {
          put("type", "HELLO")
          put("code", code ?: "")
        }.toString()
        writer?.println(hello)

      } catch (_: Throwable) {
        closeNow()
      }
    }.start()
  }

  private fun sendOpenUrl(url: String) {
    connectIfNeeded()
    Thread {
      try {
        val w = writer
        if (w != null) {
          val msg = JSONObject().apply {
            put("type", "OPEN_URL")
            put("url", url)
          }.toString()
          w.println(msg)
        }
      } catch (_: Throwable) {
        closeNow()
      }
    }.start()
  }

  private fun closeNow() {
    try { socket?.close() } catch (_: Throwable) {}
    socket = null
    writer = null
  }

  private fun notif(text: String): Notification {
    val chId = "car_nav_main"
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(
        NotificationChannel(chId, getString(R.string.channel_main_name), NotificationManager.IMPORTANCE_LOW)
      )
    }
    return NotificationCompat.Builder(this, chId)
      .setContentTitle(getString(R.string.app_name_main))
      .setContentText(text)
      .setSmallIcon(R.drawable.ic_main_mode)
      .setOngoing(true)
      .build()
  }

  override fun onDestroy() {
    closeNow()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
