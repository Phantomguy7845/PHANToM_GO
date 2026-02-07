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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.phantom.carnavrelay.BtConst
import com.phantom.carnavrelay.CrashReporter
import com.phantom.carnavrelay.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class DisplayServerService : Service() {

  companion object {
    private const val TAG = "PHANTOM_GO"
    private const val NOTIFICATION_ID = 2001
  }

  private var code: String = "000000"
  private var server: BluetoothServerSocket? = null
  private var worker: Thread? = null

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "🔧 DisplayServerService onCreate")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "▶️ DisplayServerService onStartCommand, startId=$startId")
    code = intent?.getStringExtra("code") ?: code
    Log.d(TAG, "🔢 Pairing code: $code")
    
    try {
      startForeground(NOTIFICATION_ID, notif(getString(R.string.notification_listening) + " (code $code)"))
      Log.d(TAG, "✅ Foreground service started")
    } catch (e: Exception) {
      Log.e(TAG, "💥 Failed to start foreground service", e)
    CrashReporter.recordException(applicationContext, "DisplayServerService:startForeground", e)
      stopSelf()
      return START_NOT_STICKY
    }

    if (worker == null) {
      Log.d(TAG, "🏃 Starting server worker thread")
      worker = Thread { runServerLoop() }.also { it.start() }
    }
    return START_STICKY
  }

  private fun runServerLoop() {
    Log.d(TAG, "🔄 Server loop started")
    try {
      val adapter = BluetoothAdapter.getDefaultAdapter()
      if (adapter == null) {
        Log.e(TAG, "❌ No Bluetooth adapter available")
        return
      }
      
      Log.d(TAG, "📡 Creating RFCOMM server socket...")
      server = adapter.listenUsingRfcommWithServiceRecord(BtConst.SERVICE_NAME, BtConst.APP_UUID)
      Log.d(TAG, "✅ Server socket created, waiting for connections...")

      while (true) {
        val socket = server?.accept()
        if (socket == null) {
          Log.w(TAG, "⚠️ Server socket closed")
          break
        }
        Log.d(TAG, "📱 Client connected: ${socket.remoteDevice.address}")
        handleClient(socket)
      }
    } catch (e: Throwable) {
      Log.e(TAG, "💥 Server loop error", e)
      CrashReporter.recordException(applicationContext, "DisplayServerService:runServerLoop", e)
    }
    Log.d(TAG, "🛑 Server loop ended")
  }

  private fun handleClient(socket: BluetoothSocket) {
    val clientAddr = socket.remoteDevice.address
    Log.d(TAG, "👤 Handling client: $clientAddr")
    
    try {
      val reader = BufferedReader(InputStreamReader(socket.inputStream))
      var paired = false

      while (true) {
        val line = reader.readLine()
        if (line == null) {
          Log.d(TAG, "📴 Client $clientAddr disconnected")
          break
        }
        
        Log.d(TAG, "📨 Received from $clientAddr: $line")
        val obj = try {
          JSONObject(line)
        } catch (e: Exception) {
          Log.e(TAG, "💥 Invalid JSON from $clientAddr: $line", e)
          CrashReporter.recordException(applicationContext, "DisplayServerService:parseJSON", e)
          continue
        }

        when (obj.optString("type")) {
          "HELLO" -> {
            val got = obj.optString("code")
            paired = (got == code)
            Log.d(TAG, "🔑 Pairing attempt: received=$got, expected=$code, matched=$paired")
            // ถ้าไม่ตรง ตัดการเชื่อมต่อทันที
            if (!paired) {
              Log.w(TAG, "❌ Pairing failed, disconnecting client")
              break
            }
            try {
              sendBroadcast(Intent(BtConst.ACTION_CONNECTED))
            } catch (e: Exception) {
              Log.e(TAG, "💥 Failed to broadcast CONNECTED", e)
              CrashReporter.recordException(applicationContext, "DisplayServerService:broadcastConnected", e)
            }
          }

          "OPEN_URL" -> {
            if (!paired) {
              Log.w(TAG, "⚠️ OPEN_URL from unpaired client, ignoring")
              continue
            }
            val url = obj.optString("url")
            Log.d(TAG, "🗺️ OPEN_URL received: $url")
            if (url.isNotBlank()) {
              try {
                sendBroadcast(Intent(BtConst.ACTION_OPEN_URL).putExtra(BtConst.EXTRA_URL, url))
              } catch (e: Exception) {
                Log.e(TAG, "💥 Failed to broadcast OPEN_URL", e)
                CrashReporter.recordException(applicationContext, "DisplayServerService:broadcastOpenUrl", e)
              }
            }
          }
        }
      }
    } catch (e: Throwable) {
      Log.e(TAG, "💥 Client handler error for $clientAddr", e)
      CrashReporter.recordException(applicationContext, "DisplayServerService:handleClient", e)
    } finally {
      try {
        socket.close()
        Log.d(TAG, "🔌 Client socket closed: $clientAddr")
      } catch (e: Throwable) {
        Log.e(TAG, "💥 Error closing client socket", e)
        CrashReporter.recordException(applicationContext, "DisplayServerService:closeSocket", e)
      }
      try {
        sendBroadcast(Intent(BtConst.ACTION_DISCONNECTED))
      } catch (e: Exception) {
        Log.e(TAG, "💥 Failed to broadcast DISCONNECTED", e)
      }
    }
  }

  private fun notif(text: String): Notification {
    val chId = "car_nav_display"
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(
        NotificationChannel(chId, getString(R.string.channel_display_name), NotificationManager.IMPORTANCE_LOW)
      )
    }
    return NotificationCompat.Builder(this, chId)
      .setContentTitle(getString(R.string.app_name_display))
      .setContentText(text)
      .setSmallIcon(R.drawable.ic_display_mode)
      .setOngoing(true)
      .build()
  }

  override fun onDestroy() {
    Log.d(TAG, "💀 DisplayServerService onDestroy")
    try {
      server?.close()
      Log.d(TAG, "🔌 Server socket closed")
    } catch (e: Throwable) {
      Log.e(TAG, "Error closing server socket", e)
      CrashReporter.recordException(applicationContext, "DisplayServerService:onDestroy", e)
    }
    worker = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
