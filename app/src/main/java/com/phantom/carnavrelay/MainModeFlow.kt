package com.phantom.carnavrelay

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.phantom.carnavrelay.bt.MainClientService

object MainModeFlow {
  fun start(ctx: Context) {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    val bonded = adapter?.bondedDevices?.toList().orEmpty()

    if (bonded.isEmpty()) {
      AlertDialog.Builder(ctx)
        .setTitle("ยังไม่มีอุปกรณ์ที่จับคู่")
        .setMessage("ไปจับคู่ Bluetooth กับมือถือจอติดรถใน Settings ก่อน แล้วกลับมาใหม่")
        .setPositiveButton("OK", null)
        .show()
      return
    }

    val names = bonded.map { "${it.name} (${it.address})" }.toTypedArray()

    AlertDialog.Builder(ctx)
      .setTitle("เลือกจอติดรถ (Paired devices)")
      .setItems(names) { _, which ->
        val device = bonded[which]
        MainPairDialog.show(ctx, device.address)
      }
      .show()
  }
}

object MainPairDialog {
  fun show(ctx: Context, address: String) {
    val input = android.widget.EditText(ctx).apply { hint = "ใส่รหัส 6 หลักจากจอติดรถ" }

    AlertDialog.Builder(ctx)
      .setTitle("ยืนยันรหัสจับคู่")
      .setView(input)
      .setPositiveButton("เชื่อมต่อ") { _, _ ->
        val code = input.text.toString().trim()
        val i = Intent(ctx, MainClientService::class.java).apply {
          putExtra("address", address)
          putExtra("code", code)
        }
        ctx.startForegroundService(i)
      }
      .setNegativeButton("ยกเลิก", null)
      .show()
  }
}
