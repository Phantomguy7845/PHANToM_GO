package com.phantom.carnavrelay.bt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phantom.carnavrelay.BtConst
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class DisplayServerService : Service() {

  private var code: String = "000000"
  private var server: BluetoothServerSocket? = null
  private var worker: Thread? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    code = intent?.getStringExtra("code") ?: code
    startForeground(2001, notif("จอติดรถกำลังรอการเชื่อมต่อ… (code $code)"))

    if (worker == null) {
      worker = Thread { runServerLoop() }.also { it.start() }
    }
    return START_STICKY
  }

  private fun runServerLoop() {
    try {
      val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
      server = adapter.listenUsingRfcommWithServiceRecord(BtConst.SERVICE_NAME, BtConst.APP_UUID)

      while (true) {
        val socket = server?.accept() ?: break
        handleClient(socket)
      }
    } catch (_: Throwable) {
      // ignore
    }
  }

  private fun handleClient(socket: BluetoothSocket) {
    try {
      val reader = BufferedReader(InputStreamReader(socket.inputStream))
      var paired = false

      while (true) {
        val line = reader.readLine() ?: break
        val obj = JSONObject(line)

        when (obj.optString("type")) {
          "HELLO" -> {
            val got = obj.optString("code")
            paired = (got == code)
            // ถ้าไม่ตรง ตัดการเชื่อมต่อทันที
            if (!paired) break
          }

          "OPEN_URL" -> {
            if (!paired) continue
            val url = obj.optString("url")
            if (url.isNotBlank()) {
              // ส่งไปให้ DisplayActivity เปิด Maps (ควรเปิด Activity ค้างไว้)
              sendBroadcast(Intent(BtConst.ACTION_OPEN_URL).putExtra(BtConst.EXTRA_URL, url))
            }
          }
        }
      }
    } catch (_: Throwable) {
      // ignore
    } finally {
      try { socket.close() } catch (_: Throwable) {}
    }
  }

  private fun notif(text: String): Notification {
    val chId = "car_nav_display"
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(
        NotificationChannel(chId, "Car Display", NotificationManager.IMPORTANCE_LOW)
      )
    }
    return NotificationCompat.Builder(this, chId)
      .setContentTitle("CarNav Relay (Display)")
      .setContentText(text)
      .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
      .setOngoing(true)
      .build()
  }

  override fun onDestroy() {
    try { server?.close() } catch (_: Throwable) {}
    worker = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
