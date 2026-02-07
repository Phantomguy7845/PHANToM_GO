package com.phantom.carnavrelay

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phantom.carnavrelay.bt.MainClientService

object MainModeFlow {
  fun start(ctx: Context) {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    
    if (adapter == null) {
      MaterialAlertDialogBuilder(ctx)
        .setTitle("ไม่รองรับ Bluetooth")
        .setMessage("อุปกรณ์นี้ไม่รองรับ Bluetooth")
        .setPositiveButton(R.string.ok, null)
        .show()
      return
    }
    
    if (!adapter.isEnabled) {
      MaterialAlertDialogBuilder(ctx)
        .setTitle("Bluetooth ปิดอยู่")
        .setMessage("กรุณาเปิด Bluetooth ใน Settings ก่อนใช้งาน")
        .setPositiveButton(R.string.ok, null)
        .show()
      return
    }
    
    val bonded = adapter.bondedDevices?.toList().orEmpty()

    if (bonded.isEmpty()) {
      MaterialAlertDialogBuilder(ctx)
        .setTitle(R.string.paired_devices)
        .setMessage(R.string.no_paired_devices)
        .setPositiveButton(R.string.ok, null)
        .show()
      return
    }

    val names = bonded.map { "${it.name ?: "Unknown"}\n${it.address}" }.toTypedArray()

    MaterialAlertDialogBuilder(ctx)
      .setTitle(R.string.paired_devices)
      .setItems(names) { _, which ->
        val device = bonded[which]
        MainPairDialog.show(ctx, device.address)
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }
}

object MainPairDialog {
  fun show(ctx: Context, address: String) {
    val layout = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(48, 24, 48, 24)
    }
    
    val input = EditText(ctx).apply { 
      hint = ctx.getString(R.string.enter_code_hint)
      inputType = android.text.InputType.TYPE_CLASS_NUMBER
      maxLines = 1
      setTextColor(ContextCompat.getColor(ctx, R.color.purple_700))
    }
    
    layout.addView(input)

    MaterialAlertDialogBuilder(ctx)
      .setTitle(R.string.enter_code)
      .setView(layout)
      .setPositiveButton(R.string.connect) { _, _ ->
        val code = input.text.toString().trim()
        if (code.length == 6) {
          val i = Intent(ctx, MainClientService::class.java).apply {
            putExtra("address", address)
            putExtra("code", code)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i)
          } else {
            ctx.startService(i)
          }
        }
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }
}
